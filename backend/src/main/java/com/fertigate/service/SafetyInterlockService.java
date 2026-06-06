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

@Slf4j
@Service
@RequiredArgsConstructor
public class SafetyInterlockService {

    private final DeviceRepository deviceRepository;
    private final ValveRepository valveRepository;
    private final FertilizerPumpRepository fertilizerPumpRepository;
    private final ZoneRepository zoneRepository;
    private final AlertRepository alertRepository;
    private final IrrigationControlService irrigationControlService;
    private final MqttConfig mqttConfig;
    private final InfluxDBService influxDBService;

    @Value("${control.safety-interlock.enabled:true}")
    private boolean interlockEnabled;

    @Value("${control.safety-interlock.low-flow-threshold:0.5}")
    private double lowFlowThreshold;

    @Value("${control.safety-interlock.low-pressure-threshold:0.3}")
    private double lowPressureThreshold;

    @Value("${control.safety-interlock.max-overload-threshold:10.0}")
    private double maxOverloadThreshold;

    @Value("${control.safety-interlock.communication-timeout-seconds:60}")
    private int communicationTimeoutSeconds;

    @Value("${mqtt.topics.interlock-alert}")
    private String interlockAlertTopic;

    private final Map<String, LocalDateTime> lastCommunicationTime = new ConcurrentHashMap<>();
    private final Set<String> activeInterlocks = ConcurrentHashMap.newKeySet();

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

    public void clearInterlock(String interlockKey) {
        if (activeInterlocks.remove(interlockKey)) {
            log.info("Safety interlock cleared: {}", interlockKey);
        }
    }

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

    public Set<String> getActiveInterlocks() {
        return new HashSet<>(activeInterlocks);
    }

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

    @Transactional
    public boolean acknowledgeInterlock(String interlockKey) {
        if (activeInterlocks.contains(interlockKey)) {
            clearInterlock(interlockKey);
            log.info("Interlock acknowledged: {}", interlockKey);
            return true;
        }
        return false;
    }

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

    public void setInterlockEnabled(boolean enabled) {
        this.interlockEnabled = enabled;
        log.info("Safety interlock system {}", enabled ? "enabled" : "disabled");
    }

    public void updateCommunicationTime(String deviceCode) {
        lastCommunicationTime.put(deviceCode, LocalDateTime.now());
    }
}
