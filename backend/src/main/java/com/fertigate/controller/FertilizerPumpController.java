package com.fertigate.controller;

import com.fertigate.entity.FertilizerPump;
import com.fertigate.repository.FertilizerPumpRepository;
import com.fertigate.service.IrrigationControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/pump")
@RequiredArgsConstructor
public class FertilizerPumpController {

    private final FertilizerPumpRepository fertilizerPumpRepository;
    private final IrrigationControlService irrigationControlService;

    @GetMapping
    public ResponseEntity<List<FertilizerPump>> getAllPumps() {
        return ResponseEntity.ok(fertilizerPumpRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FertilizerPump> getPumpById(@PathVariable UUID id) {
        return fertilizerPumpRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/code/{deviceCode}")
    public ResponseEntity<FertilizerPump> getPumpByDeviceCode(@PathVariable String deviceCode) {
        return fertilizerPumpRepository.findByDeviceCode(deviceCode)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/zone/{zoneId}")
    public ResponseEntity<List<FertilizerPump>> getPumpsByZone(@PathVariable UUID zoneId) {
        return ResponseEntity.ok(fertilizerPumpRepository.findByZoneId(zoneId));
    }

    @GetMapping("/running")
    public ResponseEntity<List<FertilizerPump>> getRunningPumps() {
        return ResponseEntity.ok(fertilizerPumpRepository.findByIsRunningTrue());
    }

    @PostMapping
    public ResponseEntity<FertilizerPump> createPump(@RequestBody FertilizerPump pump) {
        if (pump.getDevice() != null && pump.getDevice().getDeviceCode() != null) {
            if (fertilizerPumpRepository.findByDeviceCode(pump.getDevice().getDeviceCode()).isPresent()) {
                return ResponseEntity.badRequest().build();
            }
        }
        FertilizerPump saved = fertilizerPumpRepository.save(pump);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FertilizerPump> updatePump(
            @PathVariable UUID id,
            @RequestBody FertilizerPump pump) {
        return fertilizerPumpRepository.findById(id)
                .map(existing -> {
                    pump.setId(id);
                    FertilizerPump updated = fertilizerPumpRepository.save(pump);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePump(@PathVariable UUID id) {
        if (!fertilizerPumpRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        fertilizerPumpRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/auto")
    public ResponseEntity<FertilizerPump> setPumpAutoControl(
            @PathVariable UUID id,
            @RequestParam boolean autoControl) {
        return fertilizerPumpRepository.findById(id)
                .map(pump -> {
                    pump.setAutoControl(autoControl);
                    FertilizerPump updated = fertilizerPumpRepository.save(pump);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/control")
    public ResponseEntity<Map<String, Object>> controlPump(
            @PathVariable UUID id,
            @RequestParam boolean run,
            @RequestParam(defaultValue = "手动操作") String reason,
            @RequestParam(required = false) Integer openingDegree) {
        
        boolean success = irrigationControlService.manualControlFertilizerPump(id, run, reason, openingDegree);
        if (success) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "pumpId", id,
                    "run", run,
                    "reason", reason,
                    "openingDegree", openingDegree != null ? openingDegree : 100
            ));
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
