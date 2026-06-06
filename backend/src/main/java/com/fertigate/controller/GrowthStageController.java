package com.fertigate.controller;

import com.fertigate.dto.GrowthStageRecordDTO;
import com.fertigate.service.GrowthStageService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/growth-stage")
@RequiredArgsConstructor
public class GrowthStageController {

    private final GrowthStageService growthStageService;

    @GetMapping
    public ResponseEntity<List<GrowthStageRecordDTO>> getAllRecords() {
        return ResponseEntity.ok(growthStageService.getAllRecords());
    }

    @GetMapping("/{id}")
    public ResponseEntity<GrowthStageRecordDTO> getRecordById(@PathVariable UUID id) {
        return growthStageService.getRecordById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/crop/{cropId}")
    public ResponseEntity<List<GrowthStageRecordDTO>> getRecordsByCropId(@PathVariable UUID cropId) {
        return ResponseEntity.ok(growthStageService.getRecordsByCropId(cropId));
    }

    @GetMapping("/zone/{zoneId}")
    public ResponseEntity<List<GrowthStageRecordDTO>> getRecordsByZoneId(@PathVariable UUID zoneId) {
        return ResponseEntity.ok(growthStageService.getRecordsByZoneId(zoneId));
    }

    @GetMapping("/crop/{cropId}/current")
    public ResponseEntity<GrowthStageRecordDTO> getCurrentRecordByCropId(@PathVariable UUID cropId) {
        return growthStageService.getCurrentRecordByCropId(cropId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/zone/{zoneId}/current")
    public ResponseEntity<GrowthStageRecordDTO> getCurrentRecordByZoneId(@PathVariable UUID zoneId) {
        return growthStageService.getCurrentRecordByZoneId(zoneId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<GrowthStageRecordDTO> createRecord(@RequestBody GrowthStageRecordDTO dto) {
        GrowthStageRecordDTO created = growthStageService.createRecord(dto);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<GrowthStageRecordDTO> updateRecord(
            @PathVariable UUID id,
            @RequestBody GrowthStageRecordDTO dto) {
        return growthStageService.updateRecord(id, dto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/crop/{cropId}/end")
    public ResponseEntity<GrowthStageRecordDTO> endCurrentRecord(
            @PathVariable UUID cropId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return growthStageService.endCurrentRecord(cropId, endDate)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecord(@PathVariable UUID id) {
        if (growthStageService.deleteRecord(id)) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
}
