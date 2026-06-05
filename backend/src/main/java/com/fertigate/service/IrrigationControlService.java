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

@Slf4j
@Service
@RequiredArgsConstructor
public class IrrigationControlService {

    private final ZoneRepository zoneRepository;
    private final ValveRepository valveRepository;
    private final DeviceRepository deviceRepository;
    private final IrrigationRecordRepository irrigationRecordRepository;
    private final IrrigationPlanRepository irrigationPlanRepository;
    private final CropGrowthModelService cropGrowthModelService;
    private final InfluxDBService influxDBService;
    private final MqttConfig mqttConfig;

    @Value("${control.mode:auto}")
    private String controlMode;

    @Value("${mqtt.topics.valve-command}")
    private String valveCommandTopic;

    private final Map<UUID, LocalDateTime> valveScheduledStop = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Double>> latestSensorData = new ConcurrentHashMap<>();

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
                                    irrigationRecordRepository.save(record);
                                });
                    }
                });
                valveScheduledStop.remove(valveId);
            }
        }
    }

    public void openValve(Valve valve, String reason) {
        Device device = valve.getDevice();
        if (device == null) {
            log.error("Valve {} has no associated device", valve.getId());
            return;
        }

        ValveCommandDTO command = new ValveCommandDTO();
        command.setDeviceCode(device.getDeviceCode());
        command.setOpen(true);
        command.setReason(reason);
        command.setZone(valve.getZone() != null ? valve.getZone().getName() : "");
        command.setTimestamp(LocalDateTime.now());

        mqttConfig.publish(valveCommandTopic, command);
        
        valve.setIsOpen(true);
        valve.setLastOperation(LocalDateTime.now());
        valveRepository.save(valve);
        
        log.info("Sent open command to valve {} ({})", valve.getId(), device.getDeviceCode());
    }

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

    @Transactional
    public boolean manualControlValve(UUID valveId, boolean open, String reason) {
        return valveRepository.findById(valveId)
                .map(valve -> {
                    if (open) {
                        openValve(valve, reason);
                        
                        Zone zone = valve.getZone();
                        if (zone != null) {
                            IrrigationRecord record = new IrrigationRecord();
                            record.setZone(zone);
                            record.setValve(valve);
                            record.setStartTime(LocalDateTime.now());
                            record.setReason("手动控制: " + reason);
                            irrigationRecordRepository.save(record);
                        }
                    } else {
                        closeValve(valve, reason);
                        
                        irrigationRecordRepository.findActiveRecordByValveId(valveId)
                                .ifPresent(record -> {
                                    record.setEndTime(LocalDateTime.now());
                                    irrigationRecordRepository.save(record);
                                });
                    }
                    return true;
                })
                .orElse(false);
    }

    public void setControlMode(String mode) {
        this.controlMode = mode;
        log.info("Control mode changed to: {}", mode);
        
        if ("manual".equals(mode)) {
            valveScheduledStop.clear();
        }
    }

    public String getControlMode() {
        return controlMode;
    }

    public void updateLatestSensorData(String deviceCode, Map<String, Double> values) {
        latestSensorData.put(deviceCode, values);
    }

    public Map<String, Object> getControlStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("controlMode", controlMode);
        status.put("scheduledStops", valveScheduledStop.size());
        status.put("openValves", valveRepository.findByIsOpenTrue().size());
        
        List<Map<String, Object>> scheduledList = new ArrayList<>();
        for (Map.Entry<UUID, LocalDateTime> entry : valveScheduledStop.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("valveId", entry.getKey());
            item.put("scheduledStopTime", entry.getValue());
            scheduledList.add(item);
        }
        status.put("scheduledValveStops", scheduledList);
        
        return status;
    }

    @Transactional
    public void emergencyStop() {
        log.warn("EMERGENCY STOP ACTIVATED - Closing all valves");
        
        List<Valve> allValves = valveRepository.findAll();
        for (Valve valve : allValves) {
            if (Boolean.TRUE.equals(valve.getIsOpen())) {
                closeValve(valve, "紧急停止");
                
                irrigationRecordRepository.findActiveRecordByValveId(valve.getId())
                        .ifPresent(record -> {
                            record.setEndTime(LocalDateTime.now());
                            irrigationRecordRepository.save(record);
                        });
            }
        }
        
        valveScheduledStop.clear();
    }
}
