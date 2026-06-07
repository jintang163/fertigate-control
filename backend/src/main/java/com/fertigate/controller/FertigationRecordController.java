package com.fertigate.controller;

import com.fertigate.annotation.OperationLog;
import com.fertigate.dto.FertigationRecordDTO;
import com.fertigate.entity.OperationLog;
import com.fertigate.service.FertigationRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
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

    @GetMapping("/export")
    @OperationLog(operation = "导出灌肥记录", type = OperationLog.OperationType.EXPORT, targetType = "fertigation_record")
    public ResponseEntity<byte[]> exportToExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) UUID zoneId) {

        log.info("Received Excel export request, startTime: {}, endTime: {}, zoneId: {}", startTime, endTime, zoneId);

        try {
            byte[] excelData = fertigationRecordService.exportToExcel(startTime, endTime, zoneId);

            DateTimeFormatter fileNameFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
            String startDateStr = startTime != null ? startTime.format(fileNameFormatter) : "all";
            String endDateStr = endTime != null ? endTime.format(fileNameFormatter) : "all";
            String fileName = "灌肥台账_" + startDateStr + "_" + endDateStr + ".xlsx";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
            headers.setContentDispositionFormData("attachment", encodedFileName);

            log.info("Excel export successful, fileName: {}, fileSize: {} bytes", fileName, excelData.length);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(excelData);
        } catch (Exception e) {
            log.error("Excel export failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping
    @OperationLog(operation = "创建灌肥记录", type = OperationLog.OperationType.CREATE, targetType = "fertigation_record")
    public ResponseEntity<FertigationRecordDTO> createRecord(@RequestBody FertigationRecordDTO dto) {
        FertigationRecordDTO created = fertigationRecordService.createRecord(dto);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    @OperationLog(operation = "更新灌肥记录", type = OperationLog.OperationType.UPDATE, targetType = "fertigation_record")
    public ResponseEntity<FertigationRecordDTO> updateRecord(
            @PathVariable UUID id,
            @RequestBody FertigationRecordDTO dto) {
        return fertigationRecordService.updateRecord(id, dto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/complete")
    @OperationLog(operation = "完成灌肥记录", type = OperationLog.OperationType.UPDATE, targetType = "fertigation_record")
    public ResponseEntity<FertigationRecordDTO> completeRecord(@PathVariable UUID id) {
        return fertigationRecordService.completeRecord(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @OperationLog(operation = "删除灌肥记录", type = OperationLog.OperationType.DELETE, targetType = "fertigation_record")
    public ResponseEntity<Void> deleteRecord(@PathVariable UUID id) {
        if (fertigationRecordService.deleteRecord(id)) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
}
