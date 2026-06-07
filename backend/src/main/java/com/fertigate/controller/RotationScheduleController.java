package com.fertigate.controller;

import com.fertigate.annotation.OperationLog;
import com.fertigate.dto.GanttTaskDTO;
import com.fertigate.dto.RotationExecutionDTO;
import com.fertigate.dto.RotationScheduleDTO;
import com.fertigate.entity.OperationLog;
import com.fertigate.service.RotationScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/rotation")
@RequiredArgsConstructor
public class RotationScheduleController {

    private final RotationScheduleService rotationScheduleService;

    @GetMapping
    public ResponseEntity<List<RotationScheduleDTO>> getAllSchedules() {
        return ResponseEntity.ok(rotationScheduleService.getAllSchedules());
    }

    @GetMapping("/active")
    public ResponseEntity<List<RotationScheduleDTO>> getActiveSchedules() {
        return ResponseEntity.ok(rotationScheduleService.getActiveSchedules());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RotationScheduleDTO> getScheduleById(@PathVariable UUID id) {
        return rotationScheduleService.getScheduleById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/zone/{zoneId}")
    public ResponseEntity<List<RotationScheduleDTO>> getSchedulesByZoneId(@PathVariable UUID zoneId) {
        return ResponseEntity.ok(rotationScheduleService.getSchedulesByZoneId(zoneId));
    }

    @PostMapping
    @OperationLog(operation = "创建轮灌计划", type = OperationLog.OperationType.CREATE, targetType = "rotation_schedule")
    public ResponseEntity<RotationScheduleDTO> createSchedule(@RequestBody RotationScheduleDTO dto) {
        RotationScheduleDTO created = rotationScheduleService.createSchedule(dto);
        return ResponseEntity.ok(created);
    }

    @PostMapping("/generate")
    public ResponseEntity<List<RotationScheduleDTO>> generateRotationPlan(
            @RequestParam UUID zoneId,
            @RequestParam(defaultValue = "irrigation") String irrigationType,
            @RequestParam(defaultValue = "1") int priority) {
        return ResponseEntity.ok(rotationScheduleService.generateRotationPlan(zoneId, irrigationType, priority));
    }

    @PutMapping("/{id}")
    @OperationLog(operation = "更新轮灌计划", type = OperationLog.OperationType.UPDATE, targetType = "rotation_schedule")
    public ResponseEntity<RotationScheduleDTO> updateSchedule(
            @PathVariable UUID id,
            @RequestBody RotationScheduleDTO dto) {
        return rotationScheduleService.updateSchedule(id, dto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/active")
    public ResponseEntity<RotationScheduleDTO> setScheduleActive(
            @PathVariable UUID id,
            @RequestParam boolean active) {
        return rotationScheduleService.setScheduleActive(id, active)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/execute")
    public ResponseEntity<RotationExecutionDTO> executeScheduleNow(@PathVariable UUID id) {
        RotationExecutionDTO execution = rotationScheduleService.executeRotationNow(id);
        if (execution != null) {
            return ResponseEntity.ok(execution);
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    @OperationLog(operation = "删除轮灌计划", type = OperationLog.OperationType.DELETE, targetType = "rotation_schedule")
    public ResponseEntity<Void> deleteSchedule(@PathVariable UUID id) {
        if (rotationScheduleService.deleteSchedule(id)) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/gantt")
    public ResponseEntity<List<GanttTaskDTO>> getGanttTasks(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        try {
            LocalDate targetDate = date != null ? date : LocalDate.now();
            log.info("Fetching gantt tasks for date: {}", targetDate);
            List<GanttTaskDTO> tasks = rotationScheduleService.getGanttTasks(targetDate);
            return ResponseEntity.ok(tasks);
        } catch (Exception e) {
            log.error("Error fetching gantt tasks: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
