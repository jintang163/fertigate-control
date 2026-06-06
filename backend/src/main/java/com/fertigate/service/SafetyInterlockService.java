package com.fertigate.service;

import com.fertigate.config.MqttConfig;
import com.fertigate.dto.SafetyInterlockDTO;
import com.fertigate.entity.*;
import com.fertigate.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 安全联锁服务类
 * 负责系统安全保护功能，包括通信中断检测、低流量检测、低水压检测、过载检测等
 * 每10秒自动执行一次安全检查
 * 检测到异常时自动停止相关设备并发送告警
 * 是系统安全运行的核心保障服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SafetyInterlockService {

    /**
     * 设备数据访问接口
     */
    private final DeviceRepository deviceRepository;
    /**
     * 电磁阀数据访问接口
     */
    private final ValveRepository valveRepository;
    /**
     * 施肥泵数据访问接口
     */
    private final FertilizerPumpRepository fertilizerPumpRepository;
    /**
     * 灌区数据访问接口
     */
    private final ZoneRepository zoneRepository;
    /**
     * 告警数据访问接口
     */
    private final AlertRepository alertRepository;
    /**
     * 灌溉控制服务，用于紧急停止设备
     */
    private final IrrigationControlService irrigationControlService;
    /**
     * MQTT配置，用于发送告警消息
     */
    private final MqttConfig mqttConfig;
    /**
     * InfluxDB服务，用于读取实时传感器数据
     */
    private final InfluxDBService influxDBService;

    /**
     * 安全联锁总开关，可通过配置文件开启/关闭
     * 默认值：true（启用）
     */
    @Value("${control.safety-interlock.enabled:true}")
    private boolean interlockEnabled;

    /**
     * 低流量阈值，单位：m³/h
     * 低于此值时触发低流量联锁
     * 默认值：0.5 m³/h
     */
    @Value("${control.safety-interlock.low-flow-threshold:0.5}")
    private double lowFlowThreshold;

    /**
     * 低水压阈值，单位：MPa
     * 低于此值时触发低水压联锁
     * 默认值：0.3 MPa
     */
    @Value("${control.safety-interlock.low-pressure-threshold:0.3}")
    private double lowPressureThreshold;

    /**
     * 最大过载压力阈值，单位：MPa
     * 超过此值时触发过载联锁
     * 默认值：10.0 MPa
     */
    @Value("${control.safety-interlock.max-overload-threshold:10.0}")
    private double maxOverloadThreshold;

    /**
     * 通信超时时间，单位：秒
     * 超过此时间未收到设备通信时触发通信中断联锁
     * 默认值：60秒
     */
    @Value("${control.safety-interlock.communication-timeout-seconds:60}")
    private int communicationTimeoutSeconds;

    /**
     * 安全联锁告警MQTT主题
     * 用于向边缘端推送联锁告警信息
     */
    @Value("${mqtt.topics.interlock-alert}")
    private String interlockAlertTopic;

    /**
     * 记录设备最后通信时间，用于通信超时检测
     * key: deviceCode, value: 最后通信时间
     */
    private final Map<String, LocalDateTime> lastCommunicationTime = new ConcurrentHashMap<>();
    /**
     * 存储当前激活的联锁集合
     * 防止同一联锁重复触发
     */
    private final Set<String> activeInterlocks = ConcurrentHashMap.newKeySet();

    /**
     * 定时任务：安全联锁检查
     * 每10秒执行一次（可配置），检查所有安全联锁条件
     * 包括通信中断、低流量、低水压、过载检测
     * 支持事务管理
     */
    @Scheduled(fixedDelayString = "${control.safety-interlock.check-interval-seconds:10}000")
    @Transactional
    public void checkSafetyInterlocks() {
        if (!interlockEnabled) {
            return;
        }

        log.debug("Checking safety interlocks...");

        checkCommunicationInterlock();
        checkLowFlowInterlock();
        checkLowPressureInterlock();
        checkOverloadInterlock();
    }

    /**
     * 检查通信中断联锁
     * 遍历所有设备，检查最后心跳时间是否超过通信超时阈值
     * 超时则触发通信中断联锁并停止该设备所在灌区的所有设备
     */
    private void checkCommunicationInterlock() {
        LocalDateTime now = LocalDateTime.now();
        
        List<Device> devices = deviceRepository.findAll();
        for (Device device : devices) {
            String deviceCode = device.getDeviceCode();
            LocalDateTime lastHeartbeat = device.getLastHeartbeat();
            
            if (lastHeartbeat != null) {
                long secondsSinceLastCommunication = Duration.between(lastHeartbeat, now).getSeconds();
                
                if (secondsSinceLastCommunication > communicationTimeoutSeconds) {
                    String interlockKey = "comm_" + deviceCode;
                    if (!activeInterlocks.contains(interlockKey)) {
                        triggerInterlock(
                            interlockKey,
                            "communication_timeout",
                            "critical",
                            "设备通信超时: " + device.getName() + " (" + deviceCode + "), 已离线 " + secondsSinceLastCommunication + " 秒",
                            device,
                            String.valueOf(secondsSinceLastCommunication),
                            String.valueOf(communicationTimeoutSeconds),
                            true
                        );
                        
                        stopZoneDevicesForDevice(device, "通信中断安全联锁");
                    }
                } else {
                    clearInterlock("comm_" + deviceCode);
                }
            }
        }
    }

    /**
     * 检查低流量联锁
     * 仅检查有阀门打开的灌区，读取流量传感器数据
     * 流量低于阈值时触发低流量联锁并停止该灌区所有设备
     * 用于检测管道漏水或水源不足等异常
     */
    private void checkLowFlowInterlock() {
        List<Valve> openValves = valveRepository.findByIsOpenTrue();
        
        for (Valve valve : openValves) {
            Zone zone = valve.getZone();
            if (zone == null) continue;
            
            List<Device> flowSensors = deviceRepository.findByZoneId(zone.getId()).stream()
                    .filter(d -> "flow_sensor".equals(d.getType()))
                    .toList();
            
            for (Device sensor : flowSensors) {
                Map<String, Object> sensorData = influxDBService.getLatestSensorData(sensor.getDeviceCode());
                Object flowObj = sensorData.get("flow");
                
                if (flowObj instanceof Number flow) {
                    if (flow.doubleValue() < lowFlowThreshold) {
                        String interlockKey = "low_flow_" + zone.getId();
                        if (!activeInterlocks.contains(interlockKey)) {
                            triggerInterlock(
                                interlockKey,
                                "low_flow",
                                "critical",
                                "灌区 " + zone.getName() + " 流量过低: " + flow.doubleValue() + " m³/h",
                                sensor,
                                String.valueOf(flow.doubleValue()),
                                String.valueOf(lowFlowThreshold),
                                true
                            );
                            
                            stopZoneDevices(zone.getId(), "流量过低安全联锁");
                        }
                    } else {
                        clearInterlock("low_flow_" + zone.getId());
                    }
                }
            }
        }
    }

    private void checkLowPressureInterlock() {
        List<Valve> openValves = valveRepository.findByIsOpenTrue();
        
        for (Valve valve : openValves) {
            Zone zone = valve.getZone();
            if (zone == null) continue;
            
            List<Device> pressureSensors = deviceRepository.findByZoneId(zone.getId()).stream()
                    .filter(d -> "pressure_sensor".equals(d.getType()))
                    .toList();
            
            for (Device sensor : pressureSensors) {
                Map<String, Object> sensorData = influxDBService.getLatestSensorData(sensor.getDeviceCode());
                Object pressureObj = sensorData.get("pressure");
                
                if (pressureObj instanceof Number pressure) {
                    if (pressure.doubleValue() < lowPressureThreshold) {
                        String interlockKey = "low_pressure_" + zone.getId();
                        if (!activeInterlocks.contains(interlockKey)) {
                            triggerInterlock(
                                interlockKey,
                                "low_pressure",
                                "critical",
                                "灌区 " + zone.getName() + " 水压过低: " + pressure.doubleValue() + " MPa",
                                sensor,
                                String.valueOf(pressure.doubleValue()),
                                String.valueOf(lowPressureThreshold),
                                true
                            );
                            
                            stopZoneDevices(zone.getId(), "水压过低安全联锁");
                        }
                    } else {
                        clearInterlock("low_pressure_" + zone.getId());
                    }
                }
            }
        }
    }

    private void checkOverloadInterlock() {
        List<FertilizerPump> runningPumps = fertilizerPumpRepository.findByIsRunningTrue();
        
        for (FertilizerPump pump : runningPumps) {
            if (pump.getCurrentPressure() != null && 
                pump.getMaxPressure() != null) {
                
                double pressureRatio = pump.getCurrentPressure().doubleValue() / pump.getMaxPressure().doubleValue();
                
                if (pressureRatio > 0.9) {
                    String interlockKey = "overload_pump_" + pump.getId();
                    if (!activeInterlocks.contains(interlockKey)) {
                        triggerInterlock(
                            interlockKey,
                            "overload",
                            "critical",
                            "施肥泵过载: " + (pump.getDevice() != null ? pump.getDevice().getName() : pump.getId()) + 
                            ", 压力比: " + String.format("%.2f", pressureRatio * 100) + "%",
                            pump.getDevice(),
                            String.valueOf(pump.getCurrentPressure()),
                            String.valueOf(pump.getMaxPressure()),
                            true
                        );
                        
                        irrigationControlService.stopFertilizerPump(pump, "过载安全联锁");
                    }
                } else {
                    clearInterlock("overload_pump_" + pump.getId());
                }
            }
        }
    }

    /**
     * 触发安全联锁
     * 记录联锁事件，保存告警记录，并通过MQTT发送告警消息
     * @param interlockKey 联锁唯一键，用于去重
     * @param type 联锁类型：communication_timeout, low_flow, low_pressure, overload
     * @param level 告警级别：info, warning, critical
     * @param message 告警详细信息
     * @param device 关联的设备实体
     * @param sensorValue 触发联锁的传感器当前值
     * @param thresholdValue 触发联锁的阈值
     * @param autoStop 是否自动停止设备
     */
    @Transactional
    public void triggerInterlock(String interlockKey, String type, String level, String message,
                                  Device device, String sensorValue, String thresholdValue, boolean autoStop) {
        log.warn("SAFETY INTERLOCK TRIGGERED: [{}] {} - {}", type, level, message);
        
        activeInterlocks.add(interlockKey);
        
        Alert alert = new Alert();
        alert.setDevice(device);
        alert.setAlertType(type);
        alert.setLevel(level);
        alert.setMessage(message);
        if (sensorValue != null) {
            alert.setSensorValue(new BigDecimal(sensorValue));
        }
        if (thresholdValue != null) {
            alert.setThresholdValue(new BigDecimal(thresholdValue));
        }
        alert.setCreatedAt(LocalDateTime.now());
        alertRepository.save(alert);
        
        SafetyInterlockDTO dto = new SafetyInterlockDTO();
        dto.setInterlockType(type);
        dto.setLevel(level);
        dto.setMessage(message);
        dto.setSensorValue(sensorValue);
        dto.setThresholdValue(thresholdValue);
        dto.setAutoStopped(autoStop);
        dto.setTriggeredAt(LocalDateTime.now());
        dto.setAcknowledged(false);
        
        if (device != null) {
            dto.setDeviceId(device.getId());
            dto.setDeviceCode(device.getDeviceCode());
            dto.setDeviceName(device.getName());
        }
        
        mqttConfig.publish(interlockAlertTopic, dto);
    }

    /**
     * 清除安全联锁
     * 当异常条件解除后调用，从激活联锁集合中移除
     * @param interlockKey 联锁唯一键
     */
    public void clearInterlock(String interlockKey) {
        if (activeInterlocks.remove(interlockKey)) {
            log.info("Safety interlock cleared: {}", interlockKey);
        }
    }

    /**
     * 停止指定灌区的所有设备
     * 安全联锁触发时调用，紧急停止灌区所有阀门和施肥泵
     * @param zoneId 灌区ID
     * @param reason 停止原因
     */
    @Transactional
    public void stopZoneDevices(UUID zoneId, String reason) {
        log.warn("Stopping all devices in zone {} due to: {}", zoneId, reason);
        
        List<Valve> valves = valveRepository.findOpenValvesByZoneId(zoneId);
        for (Valve valve : valves) {
            irrigationControlService.closeValve(valve, reason);
        }
        
        List<FertilizerPump> pumps = fertilizerPumpRepository.findByZoneId(zoneId);
        for (FertilizerPump pump : pumps) {
            if (Boolean.TRUE.equals(pump.getIsRunning())) {
                irrigationControlService.stopFertilizerPump(pump, reason);
            }
        }
    }

    private void stopZoneDevicesForDevice(Device device, String reason) {
        if (device.getZone() != null) {
            stopZoneDevices(device.getZone().getId(), reason);
        }
    }

    /**
     * 获取当前激活的联锁集合
     * @return 联锁键集合的副本
     */
    public Set<String> getActiveInterlocks() {
        return new HashSet<>(activeInterlocks);
    }

    /**
     * 获取当前激活的联锁详细信息列表
     * @return 联锁详情DTO列表
     */
    public List<SafetyInterlockDTO> getActiveInterlockDetails() {
        List<SafetyInterlockDTO> details = new ArrayList<>();
        
        for (String key : activeInterlocks) {
            SafetyInterlockDTO dto = new SafetyInterlockDTO();
            dto.setInterlockType(key.split("_")[0]);
            dto.setMessage(key);
            dto.setTriggeredAt(lastCommunicationTime.getOrDefault(key, LocalDateTime.now()));
            dto.setAcknowledged(false);
            details.add(dto);
        }
        
        return details;
    }

    /**
     * 确认并清除安全联锁
     * 人工确认告警后调用，清除激活的联锁状态
     * @param interlockKey 联锁唯一键
     * @return 是否成功确认
     */
    @Transactional
    public boolean acknowledgeInterlock(String interlockKey) {
        if (activeInterlocks.contains(interlockKey)) {
            clearInterlock(interlockKey);
            log.info("Interlock acknowledged: {}", interlockKey);
            return true;
        }
        return false;
    }

    /**
     * 获取安全联锁系统状态
     * 包括是否启用、当前激活联锁数、各阈值配置等
     * @return 状态信息Map
     */
    public Map<String, Object> getInterlockStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", interlockEnabled);
        status.put("activeInterlocks", activeInterlocks.size());
        status.put("activeInterlockKeys", activeInterlocks);
        status.put("lowFlowThreshold", lowFlowThreshold);
        status.put("lowPressureThreshold", lowPressureThreshold);
        status.put("maxOverloadThreshold", maxOverloadThreshold);
        status.put("communicationTimeoutSeconds", communicationTimeoutSeconds);
        return status;
    }

    /**
     * 设置安全联锁系统启用状态
     * 可通过API临时开启或关闭安全联锁功能
     * @param enabled true启用，false禁用
     */
    public void setInterlockEnabled(boolean enabled) {
        this.interlockEnabled = enabled;
        log.info("Safety interlock system {}", enabled ? "enabled" : "disabled");
    }

    /**
     * 更新设备最后通信时间
     * 收到设备通信时调用，用于通信超时检测
     * @param deviceCode 设备编码
     */
    public void updateCommunicationTime(String deviceCode) {
        lastCommunicationTime.put(deviceCode, LocalDateTime.now());
    }
}
