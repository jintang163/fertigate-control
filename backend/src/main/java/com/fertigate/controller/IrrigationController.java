package com.fertigate.controller;

import com.fertigate.dto.IrrigationDecision;
import com.fertigate.entity.IrrigationPlan;
import com.fertigate.entity.IrrigationRecord;
import com.fertigate.entity.Zone;
import com.fertigate.repository.IrrigationPlanRepository;
import com.fertigate.repository.IrrigationRecordRepository;
import com.fertigate.repository.ZoneRepository;
import com.fertigate.service.CropGrowthModelService;
import com.fertigate.service.IrrigationControlService;
import com.fertigate.service.InfluxDBService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/irrigation")
@RequiredArgsConstructor
public class IrrigationController {

    private final IrrigationControlService irrigationControlService;
    private final CropGrowthModelService cropGrowthModelService;
    private final ZoneRepository zoneRepository;
    private final InfluxDBService influxDBService;
    private final IrrigationPlanRepository irrigationPlanRepository;
    private final IrrigationRecordRepository irrigationRecordRepository;

    @GetMapping("/control/status")
    public ResponseEntity<Map<String, Object>> getControlStatus() {
        return ResponseEntity.ok(irrigationControlService.getControlStatus());
    }

    @PutMapping("/control/mode")
    public ResponseEntity<Map<String, String>> setControlMode(@RequestParam String mode) {
        if (!"auto".equals(mode) && !"manual".equals(mode)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid mode. Use 'auto' or 'manual'"));
        }
        irrigationControlService.setControlMode(mode);
        return ResponseEntity.ok(Map.of("mode", mode));
    }

    @PostMapping("/control/emergency-stop")
    public ResponseEntity<Map<String, String>> emergencyStop() {
        irrigationControlService.emergencyStop();
        return ResponseEntity.ok(Map.of("status", "emergency_stop_executed"));
    }

    @PostMapping("/valve/{valveId}/control")
    public ResponseEntity<Map<String, Object>> controlValve(
            @PathVariable UUID valveId,
            @RequestParam boolean open,
            @RequestParam(defaultValue = "手动操作") String reason) {
        
        boolean success = irrigationControlService.manualControlValve(valveId, open, reason);
        if (success) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "valveId", valveId,
                    "open", open,
                    "reason", reason
            ));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/valve/{valveId}/control-with-degree")
    public ResponseEntity<Map<String, Object>> controlValveWithDegree(
            @PathVariable UUID valveId,
            @RequestParam boolean open,
            @RequestParam(defaultValue = "手动操作") String reason,
            @RequestParam(required = false) Integer openingDegree) {
        
        boolean success = irrigationControlService.manualControlValveWithDegree(valveId, open, reason, openingDegree);
        if (success) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "valveId", valveId,
                    "open", open,
                    "reason", reason,
                    "openingDegree", openingDegree != null ? openingDegree : 100
            ));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/decision/{zoneId}")
    public ResponseEntity<IrrigationDecision> getIrrigationDecision(@PathVariable UUID zoneId) {
        return zoneRepository.findById(zoneId)
                .map(zone -> {
                    List<String> deviceCodes = getDeviceCodesForZone(zone);
                    Map<String, Double> sensorData = aggregateSensorData(deviceCodes);
                    IrrigationDecision decision = cropGrowthModelService.analyzeIrrigationNeed(zone, sensorData);
                    return ResponseEntity.ok(decision);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/decision/all")
    public ResponseEntity<List<IrrigationDecision>> getAllIrrigationDecisions() {
        List<Zone> zones = zoneRepository.findAll();
        List<IrrigationDecision> decisions = new ArrayList<>();
        
        for (Zone zone : zones) {
            List<String> deviceCodes = getDeviceCodesForZone(zone);
            Map<String, Double> sensorData = aggregateSensorData(deviceCodes);
            IrrigationDecision decision = cropGrowthModelService.analyzeIrrigationNeed(zone, sensorData);
            decisions.add(decision);
        }
        
        return ResponseEntity.ok(decisions);
    }

    private List<String> getDeviceCodesForZone(Zone zone) {
        return zoneRepository.findById(zone.getId())
                .map(z -> z.getCrop() != null ? List.of() : List.of())
                .orElse(List.of());
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

    @GetMapping("/plan")
    public ResponseEntity<List<IrrigationPlan>> getAllPlans() {
        return ResponseEntity.ok(irrigationPlanRepository.findAll());
    }

    @GetMapping("/plan/active")
    public ResponseEntity<List<IrrigationPlan>> getActivePlans() {
        return ResponseEntity.ok(irrigationPlanRepository.findByIsActiveTrue());
    }

    @PostMapping("/plan")
    public ResponseEntity<IrrigationPlan> createPlan(@RequestBody IrrigationPlan plan) {
        IrrigationPlan saved = irrigationPlanRepository.save(plan);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/plan/{id}/active")
    public ResponseEntity<IrrigationPlan> setPlanActive(
            @PathVariable UUID id,
            @RequestParam boolean active) {
        return irrigationPlanRepository.findById(id)
                .map(plan -> {
                    plan.setIsActive(active);
                    IrrigationPlan updated = irrigationPlanRepository.save(plan);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/record")
    public ResponseEntity<List<IrrigationRecord>> getRecords(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        
        List<IrrigationRecord> records;
        if (startTime != null && endTime != null) {
            records = irrigationRecordRepository.findByTimeRange(startTime, endTime);
        } else {
            records = irrigationRecordRepository.findAll();
        }
        return ResponseEntity.ok(records);
    }

    @GetMapping("/record/zone/{zoneId}")
    public ResponseEntity<List<IrrigationRecord>> getRecordsByZone(@PathVariable UUID zoneId) {
        return ResponseEntity.ok(irrigationRecordRepository.findByZoneId(zoneId));
    }
}
