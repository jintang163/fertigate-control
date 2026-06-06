package com.fertigate.controller;

import com.fertigate.dto.ThresholdStrategyDTO;
import com.fertigate.service.ThresholdStrategyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/threshold")
@RequiredArgsConstructor
public class ThresholdStrategyController {

    private final ThresholdStrategyService thresholdStrategyService;

    @GetMapping
    public ResponseEntity<List<ThresholdStrategyDTO>> getAllStrategies() {
        return ResponseEntity.ok(thresholdStrategyService.getAllStrategies());
    }

    @GetMapping("/active")
    public ResponseEntity<List<ThresholdStrategyDTO>> getActiveStrategies() {
        return ResponseEntity.ok(thresholdStrategyService.getActiveStrategies());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ThresholdStrategyDTO> getStrategyById(@PathVariable UUID id) {
        return thresholdStrategyService.getStrategyById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/zone/{zoneId}")
    public ResponseEntity<List<ThresholdStrategyDTO>> getStrategiesByZoneId(@PathVariable UUID zoneId) {
        return ResponseEntity.ok(thresholdStrategyService.getStrategiesByZoneId(zoneId));
    }

    @GetMapping("/crop/{cropId}")
    public ResponseEntity<List<ThresholdStrategyDTO>> getStrategiesByCropId(@PathVariable UUID cropId) {
        return ResponseEntity.ok(thresholdStrategyService.getStrategiesByCropId(cropId));
    }

    @GetMapping("/zone/{zoneId}/crop/{cropId}")
    public ResponseEntity<ThresholdStrategyDTO> getStrategyByZoneAndCrop(
            @PathVariable UUID zoneId,
            @PathVariable UUID cropId) {
        return thresholdStrategyService.getStrategyByZoneAndCrop(zoneId, cropId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ThresholdStrategyDTO> createStrategy(@RequestBody ThresholdStrategyDTO dto) {
        ThresholdStrategyDTO created = thresholdStrategyService.createStrategy(dto);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ThresholdStrategyDTO> updateStrategy(
            @PathVariable UUID id,
            @RequestBody ThresholdStrategyDTO dto) {
        return thresholdStrategyService.updateStrategy(id, dto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/active")
    public ResponseEntity<ThresholdStrategyDTO> setStrategyActive(
            @PathVariable UUID id,
            @RequestParam boolean active) {
        return thresholdStrategyService.setStrategyActive(id, active)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStrategy(@PathVariable UUID id) {
        if (thresholdStrategyService.deleteStrategy(id)) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/check/{zoneId}")
    public ResponseEntity<Map<String, Object>> checkThresholds(
            @PathVariable UUID zoneId,
            @RequestBody Map<String, Double> sensorData) {
        boolean withinThresholds = thresholdStrategyService.checkThresholds(zoneId, sensorData);
        boolean shouldIrrigate = thresholdStrategyService.shouldIrrigate(zoneId, sensorData);
        boolean shouldStop = thresholdStrategyService.shouldStopIrrigation(zoneId, sensorData);

        return ResponseEntity.ok(Map.of(
                "withinThresholds", withinThresholds,
                "shouldIrrigate", shouldIrrigate,
                "shouldStopIrrigation", shouldStop,
                "optimalEc", thresholdStrategyService.getOptimalEc(zoneId),
                "optimalPh", thresholdStrategyService.getOptimalPh(zoneId)
        ));
    }
}
