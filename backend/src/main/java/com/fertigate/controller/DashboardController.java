package com.fertigate.controller;

import com.fertigate.entity.Device;
import com.fertigate.entity.Valve;
import com.fertigate.repository.DeviceRepository;
import com.fertigate.repository.ValveRepository;
import com.fertigate.service.InfluxDBService;
import com.fertigate.service.IrrigationControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DeviceRepository deviceRepository;
    private final ValveRepository valveRepository;
    private final InfluxDBService influxDBService;
    private final IrrigationControlService irrigationControlService;

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getOverview() {
        Map<String, Object> overview = new HashMap<>();

        List<Device> devices = deviceRepository.findAll();
        long onlineDevices = devices.stream()
                .filter(d -> "online".equals(d.getStatus()))
                .count();
        long offlineDevices = devices.size() - onlineDevices;

        List<Valve> valves = valveRepository.findAll();
        long openValves = valves.stream()
                .filter(Valve::getIsOpen)
                .count();
        long closedValves = valves.size() - openValves;

        overview.put("totalDevices", devices.size());
        overview.put("onlineDevices", onlineDevices);
        overview.put("offlineDevices", offlineDevices);
        overview.put("totalValves", valves.size());
        overview.put("openValves", openValves);
        overview.put("closedValves", closedValves);
        overview.put("controlStatus", irrigationControlService.getControlStatus());

        return ResponseEntity.ok(overview);
    }

    @GetMapping("/realtime")
    public ResponseEntity<Map<String, Object>> getRealtimeData() {
        Map<String, Object> realtime = new HashMap<>();

        List<Map<String, Object>> soilData = influxDBService.getLatestDataForDeviceType("soil", 20);
        List<Map<String, Object>> weatherData = influxDBService.getLatestDataForDeviceType("weather", 20);

        realtime.put("soilSensors", soilData);
        realtime.put("weatherSensors", weatherData);

        return ResponseEntity.ok(realtime);
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getDashboardSummary() {
        Map<String, Object> summary = new HashMap<>();

        List<Device> soilSensors = deviceRepository.findByType("soil");
        List<Device> weatherSensors = deviceRepository.findByType("weather");
        List<Device> valves = deviceRepository.findByType("valve");

        Map<String, Object> sensorStatus = new HashMap<>();
        sensorStatus.put("soilSensors", soilSensors.size());
        sensorStatus.put("weatherSensors", weatherSensors.size());
        sensorStatus.put("valves", valves.size());

        long onlineSoil = soilSensors.stream().filter(d -> "online".equals(d.getStatus())).count();
        long onlineWeather = weatherSensors.stream().filter(d -> "online".equals(d.getStatus())).count();
        long onlineValves = valves.stream().filter(d -> "online".equals(d.getStatus())).count();

        Map<String, Object> onlineStatus = new HashMap<>();
        onlineStatus.put("soilSensors", onlineSoil);
        onlineStatus.put("weatherSensors", onlineWeather);
        onlineStatus.put("valves", onlineValves);

        summary.put("totalSensors", sensorStatus);
        summary.put("onlineSensors", onlineStatus);
        summary.put("controlStatus", irrigationControlService.getControlStatus());

        return ResponseEntity.ok(summary);
    }
}
