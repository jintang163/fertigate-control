package com.fertigate.controller;

import com.fertigate.dto.DeviceStatusDTO;
import com.fertigate.entity.Device;
import com.fertigate.entity.Valve;
import com.fertigate.repository.DeviceRepository;
import com.fertigate.repository.ValveRepository;
import com.fertigate.service.DeviceMonitoringService;
import com.fertigate.service.SafetyInterlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/device")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceRepository deviceRepository;
    private final ValveRepository valveRepository;
    private final DeviceMonitoringService deviceMonitoringService;
    private final SafetyInterlockService safetyInterlockService;

    @GetMapping
    public ResponseEntity<List<Device>> getAllDevices() {
        return ResponseEntity.ok(deviceRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Device> getDeviceById(@PathVariable UUID id) {
        return deviceRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/code/{deviceCode}")
    public ResponseEntity<Device> getDeviceByCode(@PathVariable String deviceCode) {
        return deviceRepository.findByDeviceCode(deviceCode)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<List<Device>> getDevicesByType(@PathVariable String type) {
        return ResponseEntity.ok(deviceRepository.findByType(type));
    }

    @PostMapping
    public ResponseEntity<Device> createDevice(@RequestBody Device device) {
        if (deviceRepository.existsByDeviceCode(device.getDeviceCode())) {
            return ResponseEntity.badRequest().build();
        }
        Device saved = deviceRepository.save(device);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Device> updateDevice(@PathVariable UUID id, @RequestBody Device device) {
        return deviceRepository.findById(id)
                .map(existing -> {
                    device.setId(id);
                    Device updated = deviceRepository.save(device);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDevice(@PathVariable UUID id) {
        if (!deviceRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        deviceRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/valve")
    public ResponseEntity<List<Valve>> getAllValves() {
        return ResponseEntity.ok(valveRepository.findAll());
    }

    @GetMapping("/valve/{id}")
    public ResponseEntity<Valve> getValveById(@PathVariable UUID id) {
        return valveRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/valve/zone/{zoneId}")
    public ResponseEntity<List<Valve>> getValvesByZone(@PathVariable UUID zoneId) {
        return ResponseEntity.ok(valveRepository.findByZoneId(zoneId));
    }

    @PutMapping("/valve/{id}/auto")
    public ResponseEntity<Valve> setValveAutoControl(
            @PathVariable UUID id,
            @RequestParam boolean autoControl) {
        return valveRepository.findById(id)
                .map(valve -> {
                    valve.setAutoControl(autoControl);
                    Valve updated = valveRepository.save(valve);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/status")
    public ResponseEntity<List<DeviceStatusDTO>> getAllDeviceStatus() {
        return ResponseEntity.ok(deviceMonitoringService.getAllDeviceStatus());
    }

    @GetMapping("/status/online")
    public ResponseEntity<List<DeviceStatusDTO>> getOnlineDevices() {
        return ResponseEntity.ok(deviceMonitoringService.getOnlineDevices());
    }

    @GetMapping("/status/offline")
    public ResponseEntity<List<DeviceStatusDTO>> getOfflineDevices() {
        return ResponseEntity.ok(deviceMonitoringService.getOfflineDevices());
    }

    @GetMapping("/status/type/{type}")
    public ResponseEntity<List<DeviceStatusDTO>> getDeviceStatusByType(@PathVariable String type) {
        return ResponseEntity.ok(deviceMonitoringService.getDeviceStatusByType(type));
    }

    @GetMapping("/status/{id}")
    public ResponseEntity<DeviceStatusDTO> getDeviceStatus(@PathVariable UUID id) {
        DeviceStatusDTO status = deviceMonitoringService.getDeviceStatus(id);
        if (status != null) {
            return ResponseEntity.ok(status);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getDeviceStatistics() {
        return ResponseEntity.ok(deviceMonitoringService.getDeviceStatistics());
    }

    @GetMapping("/interlock/status")
    public ResponseEntity<Map<String, Object>> getInterlockStatus() {
        return ResponseEntity.ok(safetyInterlockService.getInterlockStatus());
    }

    @GetMapping("/interlock/active")
    public ResponseEntity<List<Map<String, Object>>> getActiveInterlocks() {
        return ResponseEntity.ok(safetyInterlockService.getActiveInterlockDetails().stream()
                .map(dto -> Map.of(
                        "interlockType", dto.getInterlockType(),
                        "level", dto.getLevel(),
                        "message", dto.getMessage(),
                        "triggeredAt", dto.getTriggeredAt(),
                        "deviceId", dto.getDeviceId(),
                        "deviceName", dto.getDeviceName(),
                        "autoStopped", dto.getAutoStopped()
                ))
                .toList());
    }

    @PostMapping("/interlock/{key}/acknowledge")
    public ResponseEntity<Map<String, Object>> acknowledgeInterlock(@PathVariable String key) {
        boolean success = safetyInterlockService.acknowledgeInterlock(key);
        return ResponseEntity.ok(Map.of(
                "success", success,
                "key", key
        ));
    }

    @PutMapping("/interlock/enabled")
    public ResponseEntity<Map<String, Object>> setInterlockEnabled(@RequestParam boolean enabled) {
        safetyInterlockService.setInterlockEnabled(enabled);
        return ResponseEntity.ok(Map.of(
                "enabled", enabled
        ));
    }
}
