package com.fertigate.controller;

import com.fertigate.dto.SensorDataDTO;
import com.fertigate.service.InfluxDBService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sensor")
@RequiredArgsConstructor
public class SensorDataController {

    private final InfluxDBService influxDBService;

    @GetMapping("/data/{deviceCode}")
    public ResponseEntity<List<Map<String, Object>>> getSensorData(
            @PathVariable String deviceCode,
            @RequestParam String sensorType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "100") int limit) {
        
        List<Map<String, Object>> data = influxDBService.querySensorData(
                deviceCode, sensorType, startTime, endTime, limit);
        return ResponseEntity.ok(data);
    }

    @GetMapping("/latest/{deviceCode}")
    public ResponseEntity<Map<String, Object>> getLatestData(@PathVariable String deviceCode) {
        Map<String, Object> data = influxDBService.getLatestSensorData(deviceCode);
        return ResponseEntity.ok(data);
    }

    @GetMapping("/type/{deviceType}")
    public ResponseEntity<List<Map<String, Object>>> getLatestDataByType(
            @PathVariable String deviceType,
            @RequestParam(defaultValue = "50") int limit) {
        List<Map<String, Object>> data = influxDBService.getLatestDataForDeviceType(deviceType, limit);
        return ResponseEntity.ok(data);
    }
}
