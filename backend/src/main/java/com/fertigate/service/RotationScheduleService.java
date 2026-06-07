package com.fertigate.service;

import com.fertigate.dto.GanttTaskDTO;
import com.fertigate.dto.RotationExecutionDTO;
import com.fertigate.dto.RotationScheduleDTO;
import com.fertigate.entity.*;
import com.fertigate.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RotationScheduleService {

    private final RotationScheduleRepository rotationScheduleRepository;
    private final ThresholdStrategyRepository thresholdStrategyRepository;
    private final ZoneRepository zoneRepository;
    private final ValveRepository valveRepository;
    private final FertilizerPumpRepository fertilizerPumpRepository;
    private final IrrigationRecordRepository irrigationRecordRepository;
    private final IrrigationControlService irrigationControlService;

    public List<RotationScheduleDTO> getAllSchedules() {
        return rotationScheduleRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<RotationScheduleDTO> getActiveSchedules() {
        return rotationScheduleRepository.findByIsActiveTrue().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<RotationScheduleDTO> getSchedulesByZoneId(UUID zoneId) {
        return rotationScheduleRepository.findActiveByZoneId(zoneId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public Optional<RotationScheduleDTO> getScheduleById(UUID id) {
        return rotationScheduleRepository.findById(id)
                .map(this::convertToDTO);
    }

    @Transactional
    public RotationScheduleDTO createSchedule(RotationScheduleDTO dto) {
        RotationSchedule schedule = new RotationSchedule();
        populateScheduleFromDTO(schedule, dto);
        schedule.setNextExecution(calculateNextExecution(dto.getStartTime(), dto.getIntervalHours()));
        
        RotationSchedule saved = rotationScheduleRepository.save(schedule);
        log.info("Created rotation schedule: {} with id {}", saved.getName(), saved.getId());
        
        return convertToDTO(saved);
    }

    @Transactional
    public Optional<RotationScheduleDTO> updateSchedule(UUID id, RotationScheduleDTO dto) {
        return rotationScheduleRepository.findById(id)
                .map(schedule -> {
                    populateScheduleFromDTO(schedule, dto);
                    if (dto.getStartTime() != null || dto.getIntervalHours() != null) {
                        LocalTime startTime = dto.getStartTime() != null ? dto.getStartTime() : schedule.getStartTime();
                        Integer interval = dto.getIntervalHours() != null ? dto.getIntervalHours() : schedule.getIntervalHours();
                        schedule.setNextExecution(calculateNextExecution(startTime, interval));
                    }
                    return convertToDTO(rotationScheduleRepository.save(schedule));
                });
    }

    @Transactional
    public Optional<RotationScheduleDTO> setScheduleActive(UUID id, boolean active) {
        return rotationScheduleRepository.findById(id)
                .map(schedule -> {
                    schedule.setIsActive(active);
                    if (active && schedule.getNextExecution() == null) {
                        schedule.setNextExecution(calculateNextExecution(schedule.getStartTime(), schedule.getIntervalHours()));
                    }
                    log.info("Schedule {} set to {}", schedule.getName(), active ? "active" : "inactive");
                    return convertToDTO(rotationScheduleRepository.save(schedule));
                });
    }

    @Transactional
    public boolean deleteSchedule(UUID id) {
        if (rotationScheduleRepository.existsById(id)) {
            rotationScheduleRepository.deleteById(id);
            log.info("Deleted rotation schedule: {}", id);
            return true;
        }
        return false;
    }

    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void executeScheduledRotations() {
        LocalDateTime now = LocalDateTime.now();
        List<RotationSchedule> schedulesToExecute = rotationScheduleRepository.findScheduledToExecute(now);
        
        if (schedulesToExecute.isEmpty()) {
            return;
        }
        
        log.info("Found {} rotation schedules to execute", schedulesToExecute.size());
        
        for (RotationSchedule schedule : schedulesToExecute) {
            try {
                executeRotation(schedule);
            } catch (Exception e) {
                log.error("Error executing rotation schedule {}: {}", schedule.getName(), e.getMessage(), e);
            }
        }
    }

    @Transactional
    public RotationExecutionDTO executeRotationNow(UUID scheduleId) {
        return rotationScheduleRepository.findById(scheduleId)
                .map(this::executeRotation)
                .orElse(null);
    }

    public List<RotationScheduleDTO> generateRotationPlan(UUID zoneId, String irrigationType, int priority) {
        List<RotationScheduleDTO> generatedPlans = new ArrayList<>();
        
        Optional<Zone> zoneOpt = zoneRepository.findById(zoneId);
        if (zoneOpt.isEmpty()) {
            return generatedPlans;
        }
        
        Zone zone = zoneOpt.get();
        List<ThresholdStrategy> strategies = thresholdStrategyRepository.findActiveByZoneId(zoneId);
        
        if (strategies.isEmpty()) {
            return generatedPlans;
        }
        
        ThresholdStrategy strategy = strategies.get(0);
        
        RotationScheduleDTO schedule = new RotationScheduleDTO();
        schedule.setName(zone.getName() + " - " + ("fertilization".equals(irrigationType) ? "施肥" : "灌溉") + "计划");
        schedule.setDescription("自动生成的轮灌计划");
        schedule.setZoneIds(Collections.singletonList(zoneId));
        schedule.setStrategyId(strategy.getId());
        schedule.setStartTime(LocalTime.of(6, 0));
        schedule.setDuration(1800);
        schedule.setIntervalHours(24);
        schedule.setPriority(priority);
        schedule.setIrrigationType(irrigationType);
        schedule.setIsActive(true);
        
        if (zone.getArea() != null) {
            BigDecimal waterAmount = zone.getArea().multiply(new BigDecimal("0.01"));
            schedule.setWaterAmount(waterAmount);
            
            if ("fertilization".equals(irrigationType)) {
                schedule.setFertilizerAmount(waterAmount.multiply(new BigDecimal("0.001")));
            }
        }
        
        generatedPlans.add(schedule);
        
        return generatedPlans;
    }

    private RotationExecutionDTO executeRotation(RotationSchedule schedule) {
        RotationExecutionDTO execution = new RotationExecutionDTO();
        execution.setScheduleId(schedule.getId());
        execution.setStartTime(LocalDateTime.now());
        execution.setDuration(schedule.getDuration());
        execution.setWaterAmount(schedule.getWaterAmount());
        execution.setFertilizerAmount(schedule.getFertilizerAmount());
        execution.setIrrigationType(schedule.getIrrigationType());
        
        List<UUID> zoneIds = parseZoneIds(schedule.getZoneIds());
        
        if (zoneIds.isEmpty()) {
            execution.setStatus("failed");
            execution.setMessage("No zones specified for rotation");
            log.warn("Rotation schedule {} has no zones configured", schedule.getName());
            return execution;
        }
        
        boolean allZonesExecuted = true;
        StringBuilder message = new StringBuilder();
        
        for (UUID zoneId : zoneIds) {
            try {
                executeZoneRotation(zoneId, schedule);
                message.append("Zone ").append(zoneId).append(" executed; ");
            } catch (Exception e) {
                allZonesExecuted = false;
                message.append("Zone ").append(zoneId).append(" failed: ").append(e.getMessage()).append("; ");
                log.error("Failed to execute rotation for zone {} in schedule {}: {}", 
                        zoneId, schedule.getName(), e.getMessage());
            }
        }
        
        schedule.setLastExecution(LocalDateTime.now());
        schedule.setNextExecution(calculateNextExecution(schedule.getStartTime(), schedule.getIntervalHours()));
        rotationScheduleRepository.save(schedule);
        
        execution.setStatus(allZonesExecuted ? "success" : "partial");
        execution.setMessage(message.toString());
        
        log.info("Rotation schedule {} executed with status: {}", schedule.getName(), execution.getStatus());
        
        return execution;
    }

    private void executeZoneRotation(UUID zoneId, RotationSchedule schedule) {
        Optional<Zone> zoneOpt = zoneRepository.findById(zoneId);
        if (zoneOpt.isEmpty()) {
            throw new RuntimeException("Zone not found: " + zoneId);
        }
        
        Zone zone = zoneOpt.get();
        String irrigationType = schedule.getIrrigationType() != null ? schedule.getIrrigationType() : "irrigation";
        
        List<Valve> valves = valveRepository.findAutoControlledValvesByZoneId(zoneId);
        if (valves.isEmpty()) {
            throw new RuntimeException("No auto-controlled valves found for zone: " + zoneId);
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        for (Valve valve : valves) {
            if (Boolean.FALSE.equals(valve.getIsOpen())) {
                Integer openingDegree = schedule.getStrategy() != null ? 100 : valve.getOpeningDegree();
                irrigationControlService.openValveWithDegree(valve, "轮灌调度: " + schedule.getName(), openingDegree);
                
                IrrigationRecord record = new IrrigationRecord();
                record.setZone(zone);
                record.setValve(valve);
                record.setPlan(null);
                record.setStartTime(now);
                record.setReason("轮灌调度: " + schedule.getName());
                record.setExecutionMode("auto");
                record.setIrrigationType(irrigationType);
                record.setWaterAmount(schedule.getWaterAmount());
                record.setFertilizerAmount(schedule.getFertilizerAmount());
                record.setStatus("running");
                irrigationRecordRepository.save(record);
                
                if (schedule.getDuration() != null && schedule.getDuration() > 0) {
                    LocalDateTime stopTime = now.plusSeconds(schedule.getDuration());
                    irrigationControlService.scheduleValveStop(valve.getId(), stopTime, "轮灌时长结束");
                }
            }
        }
        
        if ("fertilization".equals(irrigationType) && schedule.getFertilizerAmount() != null && 
            schedule.getFertilizerAmount().compareTo(BigDecimal.ZERO) > 0) {
            List<FertilizerPump> pumps = fertilizerPumpRepository.findAutoControlledPumpsByZoneId(zoneId);
            for (FertilizerPump pump : pumps) {
                if (Boolean.FALSE.equals(pump.getIsRunning())) {
                    irrigationControlService.startFertilizerPump(pump, "轮灌施肥: " + schedule.getName(), 100);
                }
            }
        }
        
        log.info("Rotation executed for zone {}: {} valves, type={}", 
                zone.getName(), valves.size(), irrigationType);
    }

    private LocalDateTime calculateNextExecution(LocalTime startTime, Integer intervalHours) {
        if (startTime == null) {
            return null;
        }
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextExecution = LocalDateTime.of(now.toLocalDate(), startTime);
        
        if (nextExecution.isBefore(now)) {
            nextExecution = nextExecution.plusDays(1);
        }
        
        if (intervalHours != null && intervalHours > 0) {
            while (nextExecution.isBefore(now)) {
                nextExecution = nextExecution.plusHours(intervalHours);
            }
        }
        
        return nextExecution;
    }

    private List<UUID> parseZoneIds(String zoneIdsStr) {
        if (zoneIdsStr == null || zoneIdsStr.isEmpty()) {
            return Collections.emptyList();
        }
        
        try {
            return Arrays.stream(zoneIdsStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(UUID::fromString)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to parse zone IDs: {}", zoneIdsStr, e);
            return Collections.emptyList();
        }
    }

    private String formatZoneIds(List<UUID> zoneIds) {
        if (zoneIds == null || zoneIds.isEmpty()) {
            return "";
        }
        return zoneIds.stream()
                .map(UUID::toString)
                .collect(Collectors.joining(","));
    }

    private void populateScheduleFromDTO(RotationSchedule schedule, RotationScheduleDTO dto) {
        schedule.setName(dto.getName());
        schedule.setDescription(dto.getDescription());
        
        if (dto.getStrategyId() != null) {
            thresholdStrategyRepository.findById(dto.getStrategyId()).ifPresent(schedule::setStrategy);
        }
        
        if (dto.getZoneIds() != null) {
            schedule.setZoneIds(formatZoneIds(dto.getZoneIds()));
        }
        
        if (dto.getStartTime() != null) schedule.setStartTime(dto.getStartTime());
        if (dto.getEndTime() != null) schedule.setEndTime(dto.getEndTime());
        if (dto.getDuration() != null) schedule.setDuration(dto.getDuration());
        if (dto.getIntervalHours() != null) schedule.setIntervalHours(dto.getIntervalHours());
        if (dto.getPriority() != null) schedule.setPriority(dto.getPriority());
        if (dto.getWaterAmount() != null) schedule.setWaterAmount(dto.getWaterAmount());
        if (dto.getFertilizerAmount() != null) schedule.setFertilizerAmount(dto.getFertilizerAmount());
        if (dto.getIrrigationType() != null) schedule.setIrrigationType(dto.getIrrigationType());
        if (dto.getIsActive() != null) schedule.setIsActive(dto.getIsActive());
    }

    private RotationScheduleDTO convertToDTO(RotationSchedule schedule) {
        RotationScheduleDTO dto = new RotationScheduleDTO();
        dto.setId(schedule.getId());
        dto.setName(schedule.getName());
        dto.setDescription(schedule.getDescription());
        dto.setStartTime(schedule.getStartTime());
        dto.setEndTime(schedule.getEndTime());
        dto.setDuration(schedule.getDuration());
        dto.setIntervalHours(schedule.getIntervalHours());
        dto.setPriority(schedule.getPriority());
        dto.setWaterAmount(schedule.getWaterAmount());
        dto.setFertilizerAmount(schedule.getFertilizerAmount());
        dto.setIrrigationType(schedule.getIrrigationType());
        dto.setIsActive(schedule.getIsActive());
        dto.setLastExecution(schedule.getLastExecution());
        dto.setNextExecution(schedule.getNextExecution());
        dto.setCreatedAt(schedule.getCreatedAt());
        dto.setUpdatedAt(schedule.getUpdatedAt());
        
        if (schedule.getStrategy() != null) {
            dto.setStrategyId(schedule.getStrategy().getId());
        }
        
        dto.setZoneIds(parseZoneIds(schedule.getZoneIds()));
        
        return dto;
    }

    @Transactional(readOnly = true)
    public List<GanttTaskDTO> getGanttTasks(LocalDate date) {
        log.info("Generating gantt tasks for date: {}", date);
        
        List<GanttTaskDTO> tasks = new ArrayList<>();
        List<RotationSchedule> activeSchedules = rotationScheduleRepository.findByIsActiveTrue();
        
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.atTime(LocalTime.MAX);
        
        List<IrrigationRecord> dayRecords = irrigationRecordRepository.findByTimeRange(dayStart, dayEnd);
        Map<UUID, List<IrrigationRecord>> zoneRecordsMap = dayRecords.stream()
                .filter(r -> r.getZone() != null)
                .collect(Collectors.groupingBy(r -> r.getZone().getId()));
        
        for (RotationSchedule schedule : activeSchedules) {
            List<UUID> zoneIds = parseZoneIds(schedule.getZoneIds());
            if (zoneIds.isEmpty()) {
                continue;
            }
            
            List<LocalDateTime> executionTimes = generateExecutionTimes(schedule, date);
            
            for (UUID zoneId : zoneIds) {
                Optional<Zone> zoneOpt = zoneRepository.findById(zoneId);
                if (zoneOpt.isEmpty()) {
                    continue;
                }
                Zone zone = zoneOpt.get();
                
                List<IrrigationRecord> zoneRecords = zoneRecordsMap.getOrDefault(zoneId, Collections.emptyList());
                
                for (int i = 0; i < executionTimes.size(); i++) {
                    LocalDateTime taskStart = executionTimes.get(i);
                    int durationSeconds = schedule.getDuration() != null ? schedule.getDuration() : 1800;
                    LocalDateTime taskEnd = taskStart.plusSeconds(durationSeconds);
                    
                    GanttTaskDTO task = new GanttTaskDTO();
                    task.setId(UUID.randomUUID());
                    task.setName(schedule.getName() + " - " + zone.getName());
                    task.setStart(taskStart);
                    task.setEnd(taskEnd);
                    task.setZoneId(zoneId);
                    task.setZoneName(zone.getName());
                    
                    calculateProgressAndStatus(task, zoneRecords, schedule.getName());
                    
                    tasks.add(task);
                }
            }
        }
        
        log.info("Generated {} gantt tasks for date {}", tasks.size(), date);
        return tasks;
    }

    private List<LocalDateTime> generateExecutionTimes(RotationSchedule schedule, LocalDate date) {
        List<LocalDateTime> times = new ArrayList<>();
        
        LocalTime startTime = schedule.getStartTime();
        if (startTime == null) {
            startTime = LocalTime.of(6, 0);
        }
        
        LocalTime endTime = schedule.getEndTime();
        if (endTime == null) {
            endTime = LocalTime.of(22, 0);
        }
        
        int intervalHours = schedule.getIntervalHours() != null && schedule.getIntervalHours() > 0 
                ? schedule.getIntervalHours() : 24;
        
        LocalDateTime current = LocalDateTime.of(date, startTime);
        LocalDateTime dayEnd = LocalDateTime.of(date, endTime);
        
        while (!current.isAfter(dayEnd)) {
            times.add(current);
            current = current.plusHours(intervalHours);
        }
        
        return times;
    }

    private void calculateProgressAndStatus(GanttTaskDTO task, List<IrrigationRecord> zoneRecords, String scheduleName) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime taskStart = task.getStart();
        LocalDateTime taskEnd = task.getEnd();
        
        String searchReason = "轮灌调度: " + scheduleName;
        List<IrrigationRecord> relatedRecords = zoneRecords.stream()
                .filter(r -> r.getReason() != null && r.getReason().contains(searchReason))
                .filter(r -> !r.getStartTime().isBefore(taskStart.minusMinutes(30)) && !r.getStartTime().isAfter(taskEnd.plusMinutes(30)))
                .collect(Collectors.toList());
        
        if (!relatedRecords.isEmpty()) {
            IrrigationRecord latestRecord = relatedRecords.get(0);
            String recordStatus = latestRecord.getStatus();
            
            if ("running".equals(recordStatus)) {
                task.setStatus("running");
                long totalSeconds = Duration.between(taskStart, taskEnd).getSeconds();
                long elapsedSeconds = Duration.between(taskStart, now).getSeconds();
                double progress = Math.min(100.0, (double) elapsedSeconds / totalSeconds * 100);
                task.setProgress(Math.round(progress * 10.0) / 10.0);
            } else if ("completed".equals(recordStatus)) {
                task.setStatus("completed");
                task.setProgress(100.0);
            } else if ("emergency_stopped".equals(recordStatus) || "interrupted".equals(recordStatus)) {
                task.setStatus("cancelled");
                task.setProgress(0.0);
            } else {
                determineDefaultStatus(task, now, taskStart, taskEnd);
            }
        } else {
            determineDefaultStatus(task, now, taskStart, taskEnd);
        }
    }

    private void determineDefaultStatus(GanttTaskDTO task, LocalDateTime now, LocalDateTime taskStart, LocalDateTime taskEnd) {
        if (now.isBefore(taskStart)) {
            task.setStatus("pending");
            task.setProgress(0.0);
        } else if (now.isAfter(taskEnd)) {
            task.setStatus("completed");
            task.setProgress(100.0);
        } else {
            task.setStatus("running");
            long totalSeconds = Duration.between(taskStart, taskEnd).getSeconds();
            long elapsedSeconds = Duration.between(taskStart, now).getSeconds();
            double progress = Math.min(100.0, (double) elapsedSeconds / totalSeconds * 100);
            task.setProgress(Math.round(progress * 10.0) / 10.0);
        }
    }
}
