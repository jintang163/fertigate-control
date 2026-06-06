package com.fertigate.service;

import com.fertigate.dto.DeviceStatusDTO;
import com.fertigate.entity.Device;
import com.fertigate.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceMonitoringService {

    private final DeviceRepository deviceRepository;

    @Value("${control.device-offline-timeout-seconds:180}")
    private int deviceOfflineTimeoutSeconds;

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void checkDeviceOnlineStatus() {
        log.debug("Checking device online status...");
        
        List<Device> allDevices = deviceRepository.findAll();
        LocalDateTime now = LocalDateTime.now();
        int offlineCount = 0;

        for (Device device : allDevices) {
            if (device.getLastHeartbeat() != null) {
                long secondsSinceLastHeartbeat = Duration.between(device.getLastHeartbeat(), now).getSeconds();
                
                if (secondsSinceLastHeartbeat > deviceOfflineTimeoutSeconds) {
                    if (!"offline".equals(device.getStatus())) {
                        device.setStatus("offline");
                        deviceRepository.save(device);
                        log.warn("Device {} ({}) went offline after {} seconds of inactivity",
                                device.getName(), device.getDeviceCode(), secondsSinceLastHeartbeat);
                        offlineCount++;
                    }
                }
            } else if (!"offline".equals(device.getStatus())) {
                device.setStatus("offline");
                deviceRepository.save(device);
                offlineCount++;
            }
        }

        if (offlineCount > 0) {
            log.info("Device status check completed: {} devices marked as offline", offlineCount);
        }
    }

    public List<DeviceStatusDTO> getAllDeviceStatus() {
        return deviceRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<DeviceStatusDTO> getDeviceStatusByType(String type) {
        return deviceRepository.findByType(type).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<DeviceStatusDTO> getOnlineDevices() {
        return deviceRepository.findAll().stream()
                .filter(device -> "online".equals(device.getStatus()))
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<DeviceStatusDTO> getOfflineDevices() {
        return deviceRepository.findOfflineDevices().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public DeviceStatusDTO getDeviceStatus(UUID deviceId) {
        return deviceRepository.findById(deviceId)
                .map(this::convertToDTO)
                .orElse(null);
    }

    public Map<String, Object> getDeviceStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        long totalDevices = deviceRepository.count();
        long onlineDevices = deviceRepository.countOnlineDevices();
        long offlineDevices = deviceRepository.countOfflineDevices();
        
        stats.put("total", totalDevices);
        stats.put("online", onlineDevices);
        stats.put("offline", offlineDevices);
        stats.put("onlineRate", totalDevices > 0 ? (double) onlineDevices / totalDevices * 100 : 0);
        
        Map<String, Long> typeStats = new HashMap<>();
        typeStats.put("soil_sensor", deviceRepository.countByType("soil"));
        typeStats.put("weather_sensor", deviceRepository.countByType("weather"));
        typeStats.put("valve", deviceRepository.countByType("valve"));
        typeStats.put("fertilizer_pump", deviceRepository.countByType("fertilizer_pump"));
        typeStats.put("flow_sensor", deviceRepository.countByType("flow_sensor"));
        typeStats.put("pressure_sensor", deviceRepository.countByType("pressure_sensor"));
        typeStats.put("gateway", deviceRepository.countByType("gateway"));
        stats.put("byType", typeStats);
        
        return stats;
    }

    @Transactional
    public void updateDeviceValue(String deviceCode, java.math.BigDecimal value, String unit) {
        deviceRepository.findByDeviceCode(deviceCode).ifPresent(device -> {
            device.setCurrentValue(value);
            device.setUnit(unit);
            device.setStatus("online");
            device.setLastHeartbeat(LocalDateTime.now());
            deviceRepository.save(device);
        });
    }

    @Transactional
    public void updateDeviceHeartbeat(String deviceCode) {
        deviceRepository.findByDeviceCode(deviceCode).ifPresent(device -> {
            device.setLastHeartbeat(LocalDateTime.now());
            if (!"online".equals(device.getStatus())) {
                device.setStatus("online");
                log.info("Device {} ({}) came back online", device.getName(), deviceCode);
            }
            deviceRepository.save(device);
        });
    }

    private DeviceStatusDTO convertToDTO(Device device) {
        DeviceStatusDTO dto = new DeviceStatusDTO();
        dto.setId(device.getId());
        dto.setDeviceCode(device.getDeviceCode());
        dto.setName(device.getName());
        dto.setType(device.getType());
        dto.setStatus(device.getStatus());
        dto.setCurrentValue(device.getCurrentValue());
        dto.setUnit(device.getUnit());
        dto.setLastHeartbeat(device.getLastHeartbeat());
        
        if (device.getZone() != null) {
            dto.setZoneId(device.getZone().getId());
            dto.setZoneName(device.getZone().getName());
        }
        
        boolean isOnline = "online".equals(device.getStatus());
        dto.setIsOnline(isOnline);
        
        if (!isOnline && device.getLastHeartbeat() != null) {
            dto.setOfflineDurationSeconds(Duration.between(device.getLastHeartbeat(), LocalDateTime.now()).getSeconds());
        }
        
        return dto;
    }
}
