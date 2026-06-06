package com.fertigate.service;

import com.fertigate.config.MqttConfig;
import com.fertigate.dto.IrrigationDecision;
import com.fertigate.dto.ValveCommandDTO;
import com.fertigate.entity.*;
import com.fertigate.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 灌溉控制核心服务类
 * 负责系统的灌溉控制逻辑，包括自动灌溉决策、阀门控制、施肥泵控制、手动/自动模式切换等
 * 每30秒自动执行一次灌溉检查
 * 支持阀门开度调节、施肥泵开度调节、定时停止等高级功能
 * 是整个控制系统的核心调度中心
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IrrigationControlService {

    /**
     * 灌区数据访问接口
     */
    private final ZoneRepository zoneRepository;
    /**
     * 电磁阀数据访问接口
     */
    private final ValveRepository valveRepository;
    /**
     * 设备数据访问接口
     */
    private final DeviceRepository deviceRepository;
    /**
     * 灌溉记录数据访问接口
     */
    private final IrrigationRecordRepository irrigationRecordRepository;
    /**
     * 灌溉计划数据访问接口
     */
    private final IrrigationPlanRepository irrigationPlanRepository;
    /**
     * 作物生长模型服务，用于灌溉需求分析
     */
    private final CropGrowthModelService cropGrowthModelService;
    /**
     * InfluxDB服务，用于读取实时传感器数据
     */
    private final InfluxDBService influxDBService;
    /**
     * MQTT配置，用于下发控制指令
     */
    private final MqttConfig mqttConfig;
    /**
     * 施肥泵数据访问接口
     */
    private final FertilizerPumpRepository fertilizerPumpRepository;
    /**
     * 阈值策略服务，用于灌溉决策辅助
     */
    private final ThresholdStrategyService thresholdStrategyService;

    /**
     * 控制模式：auto（自动）或 manual（手动）
     * 默认值：auto
     */
    @Value("${control.mode:auto}")
    private String controlMode;

    /**
     * 电磁阀控制指令MQTT主题
     */
    @Value("${mqtt.topics.valve-command}")
    private String valveCommandTopic;

    /**
     * 施肥泵控制指令MQTT主题
     */
    @Value("${mqtt.topics.fertilizer-pump-command}")
    private String fertilizerPumpCommandTopic;

    /**
     * 阀门定时停止映射表
     * key: 阀门ID, value: 计划停止时间
     */
    private final Map<UUID, LocalDateTime> valveScheduledStop = new ConcurrentHashMap<>();
    /**
     * 施肥泵定时停止映射表
     * key: 施肥泵ID, value: 计划停止时间
     */
    private final Map<UUID, LocalDateTime> pumpScheduledStop = new ConcurrentHashMap<>();
    /**
     * 最新传感器数据缓存
     * key: 设备编码, value: 测量值Map
     */
    private final Map<String, Map<String, Double>> latestSensorData = new ConcurrentHashMap<>();

    /**
     * 定时任务：自动灌溉检查
     * 每30秒执行一次（可配置），遍历所有灌区进行灌溉决策
     * 仅在auto模式下执行
     */
    @Scheduled(fixedDelayString = "${control.check-interval-seconds:30}000")
    public void checkAndControlIrrigation() {
        if (!"auto".equals(controlMode)) {
            log.debug("Control mode is manual, skipping automatic irrigation check");
            return;
        }

        log.info("Starting automatic irrigation check...");
        
        checkScheduledValveStops();
        
        List<Zone> zones = zoneRepository.findAll();
        for (Zone zone : zones) {
            try {
                processZoneIrrigation(zone);
            } catch (Exception e) {
                log.error("Error processing irrigation for zone {}: {}", zone.getName(), e.getMessage());
            }
        }
        
        log.info("Automatic irrigation check completed");
    }

    private void processZoneIrrigation(Zone zone) {
        List<String> sensorDeviceCodes = getSensorDeviceCodesForZone(zone);
        Map<String, Double> aggregatedSensorData = aggregateSensorData(sensorDeviceCodes);
        
        IrrigationDecision decision = cropGrowthModelService.analyzeIrrigationNeed(zone, aggregatedSensorData);
        log.info("Irrigation decision for zone {}: needIrrigation={}, reason={}",
                zone.getName(), decision.getNeedIrrigation(), decision.getReason());

        List<Valve> valves = valveRepository.findAutoControlledValvesByZoneId(zone.getId());
        
        if (decision.getNeedIrrigation()) {
            startIrrigation(zone, valves, decision);
        } else {
            boolean anyValveOpen = valves.stream().anyMatch(Valve::getIsOpen);
            if (anyValveOpen && shouldStopIrrigation(zone, aggregatedSensorData)) {
                stopIrrigation(zone, valves, decision.getReason());
            }
        }
    }

    private List<String> getSensorDeviceCodesForZone(Zone zone) {
        List<Device> devices = deviceRepository.findByZoneId(zone.getId());
        return devices.stream()
                .filter(d -> "soil".equals(d.getType()) || "weather".equals(d.getType()))
                .map(Device::getDeviceCode)
                .toList();
    }

    private Map<String, Double> aggregateSensorData(List<String> deviceCodes) {
        Map<String, List<Double>> sensorValues = new HashMap<>();
        
        for (String deviceCode : deviceCodes) {
            Map<String, Object> latestData = influxDBService.getLatestSensorData(deviceCode);
            for (Map.Entry<String, Object> entry : latestData.entrySet()) {
                if (entry.getValue() instanceof Number number) {
                    sensorValues.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                               .add(number.doubleValue());
                }
            }
        }
        
        Map<String, Double> result = new HashMap<>();
        for (Map.Entry<String, List<Double>> entry : sensorValues.entrySet()) {
            double avg = entry.getValue().stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);
            result.put(entry.getKey(), avg);
        }
        
        return result;
    }

    private boolean shouldStopIrrigation(Zone zone, Map<String, Double> sensorData) {
        Double humidity = sensorData.get("humidity");
        if (humidity == null) {
            return false;
        }
        
        if (zone.getCrop() != null) {
            return humidity >= zone.getCrop().getMaxHumidity().doubleValue();
        }
        
        return humidity >= 80;
    }

    /**
     * 开始灌溉
     * 打开指定灌区的所有自动控制阀门，创建灌溉记录，支持定时停止
     * @param zone 灌区实体
     * @param valves 要打开的阀门列表
     * @param decision 灌溉决策，包含原因、持续时间等
     */
    @Transactional
    public void startIrrigation(Zone zone, List<Valve> valves, IrrigationDecision decision) {
        LocalDateTime now = LocalDateTime.now();
        
        for (Valve valve : valves) {
            if (Boolean.FALSE.equals(valve.getIsOpen())) {
                openValve(valve, decision.getReason());
                
                IrrigationRecord record = new IrrigationRecord();
                record.setZone(zone);
                record.setValve(valve);
                record.setStartTime(now);
                record.setReason(decision.getReason());
                irrigationRecordRepository.save(record);
                
                if (decision.getDurationSeconds() != null && decision.getDurationSeconds() > 0) {
                    LocalDateTime stopTime = now.plusSeconds(decision.getDurationSeconds());
                    valveScheduledStop.put(valve.getId(), stopTime);
                }
            }
        }
    }

    /**
     * 停止灌溉
     * 关闭指定灌区的所有阀门，更新灌溉记录，计算用水量
     * @param zone 灌区实体
     * @param valves 要关闭的阀门列表
     * @param reason 停止原因
     */
    @Transactional
    public void stopIrrigation(Zone zone, List<Valve> valves, String reason) {
        LocalDateTime now = LocalDateTime.now();
        
        for (Valve valve : valves) {
            if (Boolean.TRUE.equals(valve.getIsOpen())) {
                closeValve(valve, reason);
                
                irrigationRecordRepository.findActiveRecordByValveId(valve.getId())
                        .ifPresent(record -> {
                            record.setEndTime(now);
                            if (valve.getFlowRate() != null) {
                                long durationSeconds = java.time.Duration.between(record.getStartTime(), now).getSeconds();
                                BigDecimal waterAmount = valve.getFlowRate()
                                        .multiply(BigDecimal.valueOf(durationSeconds / 3600.0));
                                record.setWaterAmount(waterAmount);
                            }
                            irrigationRecordRepository.save(record);
                        });
                
                valveScheduledStop.remove(valve.getId());
            }
        }
    }

    private void checkScheduledValveStops() {
        LocalDateTime now = LocalDateTime.now();
        
        for (Map.Entry<UUID, LocalDateTime> entry : valveScheduledStop.entrySet()) {
            if (now.isAfter(entry.getValue())) {
                UUID valveId = entry.getKey();
                valveRepository.findById(valveId).ifPresent(valve -> {
                    if (Boolean.TRUE.equals(valve.getIsOpen())) {
                        closeValve(valve, "自动灌溉时长结束");
                        
                        irrigationRecordRepository.findActiveRecordByValveId(valveId)
                                .ifPresent(record -> {
                                    record.setEndTime(now);
                                    record.setStatus("completed");
                                    irrigationRecordRepository.save(record);
                                });
                    }
                });
                valveScheduledStop.remove(valveId);
            }
        }

        for (Map.Entry<UUID, LocalDateTime> entry : pumpScheduledStop.entrySet()) {
            if (now.isAfter(entry.getValue())) {
                UUID pumpId = entry.getKey();
                fertilizerPumpRepository.findById(pumpId).ifPresent(pump -> {
                    if (Boolean.TRUE.equals(pump.getIsRunning())) {
                        stopFertilizerPump(pump, "自动施肥时长结束");
                    }
                });
                pumpScheduledStop.remove(pumpId);
            }
        }
    }

    /**
     * 打开阀门（使用默认开度）
     * @param valve 阀门实体
     * @param reason 操作原因
     */
    public void openValve(Valve valve, String reason) {
        openValveWithDegree(valve, reason, valve.getOpeningDegree() != null ? valve.getOpeningDegree() : 100);
    }

    /**
     * 打开阀门（指定开度）
     * 通过MQTT下发开阀指令，支持开度调节（0-100%）
     * @param valve 阀门实体
     * @param reason 操作原因
     * @param openingDegree 阀门开度，0-100%
     */
    public void openValveWithDegree(Valve valve, String reason, Integer openingDegree) {
        Device device = valve.getDevice();
        if (device == null) {
            log.error("Valve {} has no associated device", valve.getId());
            return;
        }

        ValveCommandDTO command = new ValveCommandDTO();
        command.setDeviceCode(device.getDeviceCode());
        command.setOpen(true);
        command.setOpeningDegree(openingDegree != null ? openingDegree : 100);
        command.setReason(reason);
        command.setZone(valve.getZone() != null ? valve.getZone().getName() : "");
        command.setTimestamp(LocalDateTime.now());

        mqttConfig.publish(valveCommandTopic, command);
        
        valve.setIsOpen(true);
        valve.setOpeningDegree(openingDegree != null ? openingDegree : 100);
        valve.setLastOperation(LocalDateTime.now());
        valveRepository.save(valve);
        
        log.info("Sent open command to valve {} ({}) with opening degree {}%", 
                valve.getId(), device.getDeviceCode(), openingDegree);
    }

    /**
     * 关闭阀门
     * 通过MQTT下发关阀指令
     * @param valve 阀门实体
     * @param reason 操作原因
     */
    public void closeValve(Valve valve, String reason) {
        Device device = valve.getDevice();
        if (device == null) {
            log.error("Valve {} has no associated device", valve.getId());
            return;
        }

        ValveCommandDTO command = new ValveCommandDTO();
        command.setDeviceCode(device.getDeviceCode());
        command.setOpen(false);
        command.setReason(reason);
        command.setZone(valve.getZone() != null ? valve.getZone().getName() : "");
        command.setTimestamp(LocalDateTime.now());

        mqttConfig.publish(valveCommandTopic, command);
        
        valve.setIsOpen(false);
        valve.setLastOperation(LocalDateTime.now());
        valveRepository.save(valve);
        
        log.info("Sent close command to valve {} ({})", valve.getId(), device.getDeviceCode());
    }

    /**
     * 手动控制阀门（使用默认开度）
     * @param valveId 阀门ID
     * @param open true打开，false关闭
     * @param reason 操作原因
     * @return 是否成功
     */
    @Transactional
    public boolean manualControlValve(UUID valveId, boolean open, String reason) {
        return manualControlValveWithDegree(valveId, open, reason, null);
    }

    /**
     * 手动控制阀门（指定开度）
     * 支持手动操作阀门，记录操作日志和灌溉记录
     * @param valveId 阀门ID
     * @param open true打开，false关闭
     * @param reason 操作原因
     * @param openingDegree 阀门开度，0-100%
     * @return 是否成功
     */
    @Transactional
    public boolean manualControlValveWithDegree(UUID valveId, boolean open, String reason, Integer openingDegree) {
        return valveRepository.findById(valveId)
                .map(valve -> {
                    if (open) {
                        Integer degree = openingDegree != null ? openingDegree : 
                            (valve.getOpeningDegree() != null ? valve.getOpeningDegree() : 100);
                        openValveWithDegree(valve, reason, degree);
                        
                        Zone zone = valve.getZone();
                        if (zone != null) {
                            IrrigationRecord record = new IrrigationRecord();
                            record.setZone(zone);
                            record.setValve(valve);
                            record.setStartTime(LocalDateTime.now());
                            record.setReason("手动控制: " + reason);
                            record.setExecutionMode("manual");
                            record.setIrrigationType("irrigation");
                            record.setStatus("running");
                            irrigationRecordRepository.save(record);
                        }
                    } else {
                        closeValve(valve, reason);
                        
                        irrigationRecordRepository.findActiveRecordByValveId(valveId)
                                .ifPresent(record -> {
                                    record.setEndTime(LocalDateTime.now());
                                    record.setStatus("completed");
                                    if (valve.getFlowRate() != null) {
                                        long durationSeconds = java.time.Duration.between(record.getStartTime(), LocalDateTime.now()).getSeconds();
                                        BigDecimal waterAmount = valve.getFlowRate()
                                                .multiply(BigDecimal.valueOf(durationSeconds / 3600.0));
                                        record.setWaterAmount(waterAmount);
                                    }
                                    irrigationRecordRepository.save(record);
                                });
                    }
                    return true;
                })
                .orElse(false);
    }

    /**
     * 启动施肥泵（指定开度）
     * 通过MQTT下发启动指令，支持开度调节（0-100%）
     * @param pump 施肥泵实体
     * @param reason 操作原因
     * @param openingDegree 泵开度，0-100%
     */
    public void startFertilizerPump(FertilizerPump pump, String reason, Integer openingDegree) {
        Device device = pump.getDevice();
        if (device == null) {
            log.error("Fertilizer pump {} has no associated device", pump.getId());
            return;
        }

        com.fertigate.dto.FertilizerPumpCommandDTO command = new com.fertigate.dto.FertilizerPumpCommandDTO();
        command.setDeviceCode(device.getDeviceCode());
        command.setRun(true);
        command.setOpeningDegree(openingDegree != null ? openingDegree : 100);
        command.setReason(reason);
        command.setZone(pump.getZone() != null ? pump.getZone().getName() : "");
        command.setTimestamp(LocalDateTime.now());

        mqttConfig.publish(fertilizerPumpCommandTopic, command);
        
        pump.setIsRunning(true);
        pump.setOpeningDegree(openingDegree != null ? openingDegree : 100);
        pump.setLastOperation(LocalDateTime.now());
        fertilizerPumpRepository.save(pump);
        
        log.info("Sent start command to fertilizer pump {} ({}) with opening degree {}%", 
                pump.getId(), device.getDeviceCode(), openingDegree);
    }

    /**
     * 停止施肥泵
     * 通过MQTT下发停止指令
     * @param pump 施肥泵实体
     * @param reason 操作原因
     */
    public void stopFertilizerPump(FertilizerPump pump, String reason) {
        Device device = pump.getDevice();
        if (device == null) {
            log.error("Fertilizer pump {} has no associated device", pump.getId());
            return;
        }

        com.fertigate.dto.FertilizerPumpCommandDTO command = new com.fertigate.dto.FertilizerPumpCommandDTO();
        command.setDeviceCode(device.getDeviceCode());
        command.setRun(false);
        command.setReason(reason);
        command.setZone(pump.getZone() != null ? pump.getZone().getName() : "");
        command.setTimestamp(LocalDateTime.now());

        mqttConfig.publish(fertilizerPumpCommandTopic, command);
        
        pump.setIsRunning(false);
        pump.setOpeningDegree(0);
        pump.setLastOperation(LocalDateTime.now());
        fertilizerPumpRepository.save(pump);
        
        log.info("Sent stop command to fertilizer pump {} ({})", pump.getId(), device.getDeviceCode());
    }

    /**
     * 手动控制施肥泵
     * 支持手动启停施肥泵，记录操作日志和施肥记录
     * @param pumpId 施肥泵ID
     * @param run true启动，false停止
     * @param reason 操作原因
     * @param openingDegree 泵开度，0-100%
     * @return 是否成功
     */
    @Transactional
    public boolean manualControlFertilizerPump(UUID pumpId, boolean run, String reason, Integer openingDegree) {
        return fertilizerPumpRepository.findById(pumpId)
                .map(pump -> {
                    if (run) {
                        startFertilizerPump(pump, "手动控制: " + reason, 
                            openingDegree != null ? openingDegree : 100);
                        
                        Zone zone = pump.getZone();
                        if (zone != null) {
                            IrrigationRecord record = new IrrigationRecord();
                            record.setZone(zone);
                            record.setStartTime(LocalDateTime.now());
                            record.setReason("手动施肥: " + reason);
                            record.setExecutionMode("manual");
                            record.setIrrigationType("fertilization");
                            record.setStatus("running");
                            irrigationRecordRepository.save(record);
                        }
                    } else {
                        stopFertilizerPump(pump, reason);
                    }
                    return true;
                })
                .orElse(false);
    }

    /**
     * 定时停止阀门
     * @param valveId 阀门ID
     * @param stopTime 计划停止时间
     * @param reason 停止原因
     */
    public void scheduleValveStop(UUID valveId, LocalDateTime stopTime, String reason) {
        valveScheduledStop.put(valveId, stopTime);
        log.info("Scheduled stop for valve {} at {}", valveId, stopTime);
    }

    /**
     * 定时停止施肥泵
     * @param pumpId 施肥泵ID
     * @param stopTime 计划停止时间
     * @param reason 停止原因
     */
    public void schedulePumpStop(UUID pumpId, LocalDateTime stopTime, String reason) {
        pumpScheduledStop.put(pumpId, stopTime);
        log.info("Scheduled stop for pump {} at {}", pumpId, stopTime);
    }

    /**
     * 设置控制模式
     * 切换手动/自动控制模式，切换到手动模式时会清除所有定时停止
     * @param mode 控制模式：auto 或 manual
     */
    public void setControlMode(String mode) {
        this.controlMode = mode;
        log.info("Control mode changed to: {}", mode);
        
        if ("manual".equals(mode)) {
            valveScheduledStop.clear();
            pumpScheduledStop.clear();
        }
    }

    public String getControlMode() {
        return controlMode;
    }

    public void updateLatestSensorData(String deviceCode, Map<String, Double> values) {
        latestSensorData.put(deviceCode, values);
    }

    /**
     * 获取控制系统状态
     * 包括当前控制模式、定时停止数量、打开阀门数、运行泵数等
     * @return 状态信息Map
     */
    public Map<String, Object> getControlStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("controlMode", controlMode);
        status.put("scheduledValveStopsCount", valveScheduledStop.size());
        status.put("scheduledPumpStopsCount", pumpScheduledStop.size());
        status.put("openValves", valveRepository.findByIsOpenTrue().size());
        status.put("runningPumps", fertilizerPumpRepository.findByIsRunningTrue().size());
        
        List<Map<String, Object>> scheduledValveList = new ArrayList<>();
        for (Map.Entry<UUID, LocalDateTime> entry : valveScheduledStop.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("valveId", entry.getKey());
            item.put("scheduledStopTime", entry.getValue());
            scheduledValveList.add(item);
        }
        status.put("scheduledValveStops", scheduledValveList);
        
        List<Map<String, Object>> scheduledPumpList = new ArrayList<>();
        for (Map.Entry<UUID, LocalDateTime> entry : pumpScheduledStop.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("pumpId", entry.getKey());
            item.put("scheduledStopTime", entry.getValue());
            scheduledPumpList.add(item);
        }
        status.put("scheduledPumpStops", scheduledPumpList);
        
        return status;
    }

    /**
     * 紧急停止
     * 立即关闭所有阀门和施肥泵，更新所有进行中的灌溉记录状态为紧急停止
     * 清除所有定时停止任务
     * 用于安全联锁或人工紧急停止场景
     */
    @Transactional
    public void emergencyStop() {
        log.warn("EMERGENCY STOP ACTIVATED - Closing all valves and pumps");
        
        List<Valve> allValves = valveRepository.findAll();
        for (Valve valve : allValves) {
            if (Boolean.TRUE.equals(valve.getIsOpen())) {
                closeValve(valve, "紧急停止");
                
                irrigationRecordRepository.findActiveRecordByValveId(valve.getId())
                        .ifPresent(record -> {
                            record.setEndTime(LocalDateTime.now());
                            record.setStatus("emergency_stopped");
                            irrigationRecordRepository.save(record);
                        });
            }
        }

        List<FertilizerPump> allPumps = fertilizerPumpRepository.findAll();
        for (FertilizerPump pump : allPumps) {
            if (Boolean.TRUE.equals(pump.getIsRunning())) {
                stopFertilizerPump(pump, "紧急停止");
            }
        }
        
        valveScheduledStop.clear();
        pumpScheduledStop.clear();

        log.warn("EMERGENCY STOP COMPLETED - {} valves closed, {} pumps stopped", 
                allValves.stream().filter(Valve::getIsOpen).count(),
                allPumps.stream().filter(FertilizerPump::getIsRunning).count());
    }
}
