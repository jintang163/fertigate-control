package com.fertigate.controller;

import com.fertigate.dto.FertigationRecordDTO;
import com.fertigate.service.FertigationRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/fertigation")
@RequiredArgsConstructor
public class FertigationRecordController {

    private final FertigationRecordService fertigationRecordService;

    @GetMapping
    public ResponseEntity<List<FertigationRecordDTO>> getAllRecords(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        
        List<FertigationRecordDTO> records;
        if (startTime != null && endTime != null) {
            records = fertigationRecordService.getRecordsByTimeRange(startTime, endTime);
        } else {
            records = fertigationRecordService.getAllRecords();
        }
        return ResponseEntity.ok(records);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FertigationRecordDTO> getRecordById(@PathVariable UUID id) {
        return fertigationRecordService.getRecordById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/zone/{zoneId}")
    public ResponseEntity<List<FertigationRecordDTO>> getRecordsByZoneId(
            @PathVariable UUID zoneId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        
        List<FertigationRecordDTO> records;
        if (startTime != null && endTime != null) {
            records = fertigationRecordService.getRecordsByZoneAndTimeRange(zoneId, startTime, endTime);
        } else {
            records = fertigationRecordService.getRecordsByZoneId(zoneId);
        }
        return ResponseEntity.ok(records);
    }

    @GetMapping("/mode/{executionMode}")
    public ResponseEntity<List<FertigationRecordDTO>> getRecordsByExecutionMode(@PathVariable String executionMode) {
        return ResponseEntity.ok(fertigationRecordService.getRecordsByExecutionMode(executionMode));
    }

    @GetMapping("/type/{irrigationType}")
    public ResponseEntity<List<FertigationRecordDTO>> getRecordsByIrrigationType(@PathVariable String irrigationType) {
        return ResponseEntity.ok(fertigationRecordService.getRecordsByIrrigationType(irrigationType));
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics(
            @RequestParam(required = false) UUID zoneId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        
        return ResponseEntity.ok(fertigationRecordService.getStatistics(zoneId, startTime, endTime));
    }

    @PostMapping
    public ResponseEntity<FertigationRecordDTO> createRecord(@RequestBody FertigationRecordDTO dto) {
        FertigationRecordDTO created = fertigationRecordService.createRecord(dto);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FertigationRecordDTO> updateRecord(
            @PathVariable UUID id,
            @RequestBody FertigationRecordDTO dto) {
        return fertigationRecordService.updateRecord(id, dto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/complete")
    public ResponseEntity<FertigationRecordDTO> completeRecord(@PathVariable UUID id) {
        return fertigationRecordService.completeRecord(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecord(@PathVariable UUID id) {
        if (fertigationRecordService.deleteRecord(id)) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
}
