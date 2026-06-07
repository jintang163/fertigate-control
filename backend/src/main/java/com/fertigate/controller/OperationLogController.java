package com.fertigate.controller;

import com.fertigate.dto.OperationLogDTO;
import com.fertigate.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/operation-log")
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogService operationLogService;

    @GetMapping
    public ResponseEntity<Page<OperationLogDTO>> getLogs(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(operationLogService.getLogs(
                username, operationType, targetType, success, startTime, endTime, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OperationLogDTO> getLogById(@PathVariable UUID id) {
        return ResponseEntity.ok(operationLogService.getLogById(id));
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return ResponseEntity.ok(operationLogService.getStatistics(startTime, endTime));
    }

    @DeleteMapping("/clean")
    public ResponseEntity<Map<String, String>> cleanOldLogs(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime beforeTime) {
        operationLogService.deleteOldLogs(beforeTime);
        return ResponseEntity.ok(Map.of("message", "清理成功"));
    }
}
