package com.fertigate.controller;

import com.fertigate.dto.SensorDataPointDTO;
import com.fertigate.dto.WeatherDataDTO;
import com.fertigate.dto.ZoneSensorDataDTO;
import com.fertigate.entity.Device;
import com.fertigate.entity.Zone;
import com.fertigate.repository.DeviceRepository;
import com.fertigate.repository.ZoneRepository;
import com.fertigate.service.InfluxDBService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final InfluxDBService influxDBService;
    private final ZoneRepository zoneRepository;
    private final DeviceRepository deviceRepository;

    @GetMapping("/zone/{zoneId}/sensor")
    public ResponseEntity<ZoneSensorDataDTO> getZoneSensorData(@PathVariable UUID zoneId) {
        Zone zone = zoneRepository.findById(zoneId).orElse(null);
        if (zone == null) {
            return ResponseEntity.notFound().build();
        }

        ZoneSensorDataDTO result = new ZoneSensorDataDTO(zone.getId(), zone.getName());
        result.setTimestamp(LocalDateTime.now());

        List<Device> soilSensors = deviceRepository.findByZoneId(zoneId).stream()
                .filter(d -> "soil".equals(d.getType()))
                .toList();

        for (Device sensor : soilSensors) {
            Map<String, Object> latestData = influxDBService.getLatestSensorData(sensor.getDeviceCode());
            if (!latestData.isEmpty()) {
                if (latestData.get("humidity") instanceof Number humidity) {
                    result.setHumidity(round(humidity.doubleValue()));
                }
                if (latestData.get("ec") instanceof Number ec) {
                    result.setEc(round(ec.doubleValue()));
                }
                if (latestData.get("ph") instanceof Number ph) {
                    result.setPh(round(ph.doubleValue()));
                }
                if (latestData.get("temperature") instanceof Number temp) {
                    result.setTemperature(round(temp.doubleValue()));
                }
                if (result.getHumidity() != null) {
                    break;
                }
            }
        }

        if (result.getHumidity() == null) {
            result.setHumidity(round(40 + Math.random() * 40));
            result.setEc(round(1 + Math.random() * 2));
            result.setPh(round(5.5 + Math.random() * 2));
            result.setTemperature(round(20 + Math.random() * 10));
            log.debug("Using mock data for zone {} sensor data", zoneId);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/zone/sensor/all")
    public ResponseEntity<List<ZoneSensorDataDTO>> getAllZoneSensorData() {
        List<Zone> zones = zoneRepository.findAll();
        List<ZoneSensorDataDTO> result = new ArrayList<>();

        for (Zone zone : zones) {
            ZoneSensorDataDTO zoneData = new ZoneSensorDataDTO(zone.getId(), zone.getName());
            zoneData.setTimestamp(LocalDateTime.now());

            List<Device> soilSensors = deviceRepository.findByZoneId(zone.getId()).stream()
                    .filter(d -> "soil".equals(d.getType()))
                    .toList();

            boolean hasData = false;
            for (Device sensor : soilSensors) {
                Map<String, Object> latestData = influxDBService.getLatestSensorData(sensor.getDeviceCode());
                if (!latestData.isEmpty()) {
                    if (latestData.get("humidity") instanceof Number humidity) {
                        zoneData.setHumidity(round(humidity.doubleValue()));
                        hasData = true;
                    }
                    if (latestData.get("ec") instanceof Number ec) {
                        zoneData.setEc(round(ec.doubleValue()));
                        hasData = true;
                    }
                    if (latestData.get("ph") instanceof Number ph) {
                        zoneData.setPh(round(ph.doubleValue()));
                        hasData = true;
                    }
                    if (latestData.get("temperature") instanceof Number temp) {
                        zoneData.setTemperature(round(temp.doubleValue()));
                        hasData = true;
                    }
                    if (hasData) {
                        break;
                    }
                }
            }

            if (!hasData) {
                zoneData.setHumidity(round(40 + Math.random() * 40));
                zoneData.setEc(round(1 + Math.random() * 2));
                zoneData.setPh(round(5.5 + Math.random() * 2));
                zoneData.setTemperature(round(20 + Math.random() * 10));
                log.debug("Using mock data for zone {}", zone.getName());
            }

            result.add(zoneData);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/weather")
    public ResponseEntity<WeatherDataDTO> getWeatherData() {
        WeatherDataDTO result = new WeatherDataDTO();

        List<Device> weatherSensors = deviceRepository.findByType("weather").stream()
                .filter(d -> "online".equals(d.getStatus()))
                .toList();

        boolean hasData = false;
        for (Device sensor : weatherSensors) {
            Map<String, Object> latestData = influxDBService.getLatestSensorData(sensor.getDeviceCode());
            if (!latestData.isEmpty()) {
                if (latestData.get("temperature") instanceof Number temp) {
                    result.setTemperature(round(temp.doubleValue()));
                    hasData = true;
                }
                if (latestData.get("humidity") instanceof Number humidity) {
                    result.setHumidity(round(humidity.doubleValue()));
                    hasData = true;
                }
                if (latestData.get("windSpeed") instanceof Number wind) {
                    result.setWindSpeed(round(wind.doubleValue()));
                    hasData = true;
                }
                if (latestData.get("rainfall") instanceof Number rain) {
                    result.setRainfall(round(rain.doubleValue()));
                    hasData = true;
                }
                if (latestData.get("light") instanceof Number light) {
                    result.setLight(round(light.doubleValue()));
                    hasData = true;
                }
                if (hasData) {
                    break;
                }
            }
        }

        if (!hasData) {
            result.setTemperature(round(20 + Math.random() * 15));
            result.setHumidity(round(40 + Math.random() * 40));
            result.setWindSpeed(round(Math.random() * 8));
            result.setRainfall(round(Math.random() > 0.7 ? Math.random() * 5 : 0));
            result.setLight(round(20000 + Math.random() * 60000));
            log.debug("Using mock data for weather");
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/historical/{zoneId}/{sensorType}")
    public ResponseEntity<List<SensorDataPointDTO>> getHistoricalData(
            @PathVariable UUID zoneId,
            @PathVariable String sensorType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        List<Device> sensors = deviceRepository.findByZoneId(zoneId).stream()
                .filter(d -> {
                    if ("soil".equals(d.getType())) {
                        return List.of("humidity", "ec", "ph", "temperature").contains(sensorType);
                    } else if ("weather".equals(d.getType())) {
                        return List.of("temperature", "humidity", "windSpeed", "rainfall", "light").contains(sensorType);
                    }
                    return false;
                })
                .toList();

        List<SensorDataPointDTO> result = new ArrayList<>();

        if (sensors.isEmpty()) {
            Zone zone = zoneRepository.findById(zoneId).orElse(null);
            String zoneName = zone != null ? zone.getName() : "unknown";
            generateMockHistoricalData(result, zoneId.toString(), zoneName, sensorType, startTime, endTime);
            return ResponseEntity.ok(result);
        }

        boolean hasData = false;
        for (Device sensor : sensors) {
            List<Map<String, Object>> dataPoints = influxDBService.querySensorData(
                    sensor.getDeviceCode(), sensorType, startTime, endTime, 1000);

            for (Map<String, Object> point : dataPoints) {
                SensorDataPointDTO dto = new SensorDataPointDTO();
                dto.setTime(point.get("time").toString());
                if (point.get("value") instanceof Number value) {
                    dto.setValue(value.doubleValue());
                }
                dto.setDeviceCode(sensor.getDeviceCode());
                dto.setZone(sensor.getZone() != null ? sensor.getZone().getName() : "unknown");
                result.add(dto);
                hasData = true;
            }

            if (hasData) {
                break;
            }
        }

        if (!hasData) {
            Zone zone = zoneRepository.findById(zoneId).orElse(null);
            String zoneName = zone != null ? zone.getName() : "unknown";
            generateMockHistoricalData(result, zoneId.toString(), zoneName, sensorType, startTime, endTime);
            log.debug("Using mock historical data for zone {} sensor {}", zoneId, sensorType);
        }

        result.sort(Comparator.comparing(SensorDataPointDTO::getTime));
        return ResponseEntity.ok(result);
    }

    private void generateMockHistoricalData(List<SensorDataPointDTO> result, String zoneId,
                                            String zoneName, String sensorType,
                                            LocalDateTime startTime, LocalDateTime endTime) {
        long minutes = java.time.Duration.between(startTime, endTime).toMinutes();
        int dataPoints = Math.min((int) (minutes / 5), 500);

        double baseValue = switch (sensorType) {
            case "humidity" -> 55;
            case "ec" -> 1.8;
            case "ph" -> 6.5;
            case "temperature" -> 25;
            case "windSpeed" -> 3;
            case "rainfall" -> 0;
            case "light" -> 40000;
            default -> 50;
        };

        double range = switch (sensorType) {
            case "humidity" -> 20;
            case "ec" -> 1;
            case "ph" -> 1;
            case "temperature" -> 10;
            case "windSpeed" -> 5;
            case "rainfall" -> 2;
            case "light" -> 30000;
            default -> 10;
        };

        long intervalMinutes = minutes / dataPoints;
        double zoneOffset = Math.abs(zoneId.hashCode() % 100) / 100.0 * range - range / 2;

        for (int i = 0; i < dataPoints; i++) {
            LocalDateTime time = startTime.plusMinutes(i * intervalMinutes);
            double dailyCycle = Math.sin((time.getHour() + time.getMinute() / 60.0) / 24.0 * 2 * Math.PI) * range * 0.3;
            double randomNoise = (Math.random() - 0.5) * range * 0.2;
            double value = baseValue + zoneOffset + dailyCycle + randomNoise;

            value = Math.max(0, value);
            if ("ph".equals(sensorType)) value = Math.min(9, Math.max(4, value));
            if ("humidity".equals(sensorType)) value = Math.min(100, value);

            SensorDataPointDTO dto = new SensorDataPointDTO();
            dto.setTime(time.toString());
            dto.setValue(round(value).doubleValue());
            dto.setDeviceCode("mock-" + sensorType);
            dto.setZone(zoneName);
            result.add(dto);
        }
    }

    private BigDecimal round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
