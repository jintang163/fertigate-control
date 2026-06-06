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

/**
 * 设备监控服务类
 * 负责系统中所有设备的状态监控、在线/离线检测、设备心跳维护等功能
 * 每60秒自动执行一次设备在线状态检查
 * 提供设备状态查询、统计分析、心跳更新等API
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceMonitoringService {

    /**
     * 设备数据访问接口，用于设备CRUD操作
     */
    private final DeviceRepository deviceRepository;

    /**
     * 设备离线超时时间，单位：秒
     * 超过此时间未收到心跳则标记设备为离线
     * 默认值：180秒（3分钟），可通过配置文件修改
     */
    @Value("${control.device-offline-timeout-seconds:180}")
    private int deviceOfflineTimeoutSeconds;

    /**
     * 定时任务：检查设备在线状态
     * 每60秒执行一次，遍历所有设备检查心跳超时情况
     * 超过设备离线超时时间未收到心跳的设备将被标记为离线状态
     * 支持事务管理，确保状态更新的原子性
     */
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

    /**
     * 获取所有设备的状态信息
     * @return 所有设备的状态DTO列表
     */
    public List<DeviceStatusDTO> getAllDeviceStatus() {
        return deviceRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 按设备类型获取设备状态信息
     * @param type 设备类型，参考DeviceType枚举
     * @return 指定类型的设备状态DTO列表
     */
    public List<DeviceStatusDTO> getDeviceStatusByType(String type) {
        return deviceRepository.findByType(type).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有在线设备的状态信息
     * @return 在线设备的状态DTO列表
     */
    public List<DeviceStatusDTO> getOnlineDevices() {
        return deviceRepository.findAll().stream()
                .filter(device -> "online".equals(device.getStatus()))
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有离线设备的状态信息
     * @return 离线设备的状态DTO列表
     */
    public List<DeviceStatusDTO> getOfflineDevices() {
        return deviceRepository.findOfflineDevices().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 根据设备ID获取设备状态信息
     * @param deviceId 设备唯一标识符
     * @return 设备状态DTO，设备不存在时返回null
     */
    public DeviceStatusDTO getDeviceStatus(UUID deviceId) {
        return deviceRepository.findById(deviceId)
                .map(this::convertToDTO)
                .orElse(null);
    }

    /**
     * 获取设备统计信息
     * 包括设备总数、在线数、离线数、在线率、按类型统计等
     * @return 统计信息Map，包含total, online, offline, onlineRate, byType等字段
     */
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

    /**
     * 更新设备测量值和心跳
     * 收到传感器数据上报时调用，更新设备当前值和在线状态
     * @param deviceCode 设备编码
     * @param value 测量值
     * @param unit 测量单位
     */
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

    /**
     * 更新设备心跳
     * 收到设备心跳报文时调用，更新最后心跳时间并标记设备为在线
     * @param deviceCode 设备编码
     */
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

    /**
     * 将Device实体转换为DeviceStatusDTO
     * 包含设备基本信息、当前值、在线状态、离线时长等
     * @param device 设备实体
     * @return 设备状态DTO
     */
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
