package com.fertigate.service;

import com.fertigate.dto.FertigationRecordDTO;
import com.fertigate.entity.IrrigationRecord;
import com.fertigate.entity.Valve;
import com.fertigate.entity.Zone;
import com.fertigate.repository.IrrigationRecordRepository;
import com.fertigate.repository.ValveRepository;
import com.fertigate.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FertigationRecordService {

    private final IrrigationRecordRepository irrigationRecordRepository;
    private final ZoneRepository zoneRepository;
    private final ValveRepository valveRepository;
    private final InfluxDBService influxDBService;

    public List<FertigationRecordDTO> getAllRecords() {
        return irrigationRecordRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<FertigationRecordDTO> getRecordsByZoneId(UUID zoneId) {
        return irrigationRecordRepository.findByZoneId(zoneId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<FertigationRecordDTO> getRecordsByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        return irrigationRecordRepository.findByTimeRange(startTime, endTime).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<FertigationRecordDTO> getRecordsByZoneAndTimeRange(UUID zoneId, LocalDateTime startTime, LocalDateTime endTime) {
        return irrigationRecordRepository.findByZoneIdAndTimeRange(zoneId, startTime, endTime).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<FertigationRecordDTO> getRecordsByExecutionMode(String executionMode) {
        return irrigationRecordRepository.findByExecutionMode(executionMode).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<FertigationRecordDTO> getRecordsByIrrigationType(String irrigationType) {
        return irrigationRecordRepository.findByIrrigationType(irrigationType).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public Optional<FertigationRecordDTO> getRecordById(UUID id) {
        return irrigationRecordRepository.findById(id)
                .map(this::convertToDTO);
    }

    @Transactional
    public FertigationRecordDTO createRecord(FertigationRecordDTO dto) {
        IrrigationRecord record = new IrrigationRecord();
        
        if (dto.getZoneId() != null) {
            zoneRepository.findById(dto.getZoneId()).ifPresent(record::setZone);
        }
        if (dto.getValveId() != null) {
            valveRepository.findById(dto.getValveId()).ifPresent(record::setValve);
        }
        
        record.setStartTime(dto.getStartTime() != null ? dto.getStartTime() : LocalDateTime.now());
        record.setEndTime(dto.getEndTime());
        record.setWaterAmount(dto.getWaterAmount());
        record.setFertilizerAmount(dto.getFertilizerAmount());
        record.setFertilizerType(dto.getFertilizerType());
        record.setAverageEc(dto.getAverageEc());
        record.setAveragePh(dto.getAveragePh());
        record.setExecutionMode(dto.getExecutionMode() != null ? dto.getExecutionMode() : "manual");
        record.setIrrigationType(dto.getIrrigationType() != null ? dto.getIrrigationType() : "irrigation");
        record.setStatus(dto.getStatus() != null ? dto.getStatus() : "completed");
        record.setReason(dto.getReason());
        
        IrrigationRecord saved = irrigationRecordRepository.save(record);
        log.info("Created fertigation record: {} for zone {}", saved.getId(), saved.getZone() != null ? saved.getZone().getName() : "unknown");
        
        return convertToDTO(saved);
    }

    @Transactional
    public Optional<FertigationRecordDTO> updateRecord(UUID id, FertigationRecordDTO dto) {
        return irrigationRecordRepository.findById(id)
                .map(record -> {
                    if (dto.getZoneId() != null) {
                        zoneRepository.findById(dto.getZoneId()).ifPresent(record::setZone);
                    }
                    if (dto.getValveId() != null) {
                        valveRepository.findById(dto.getValveId()).ifPresent(record::setValve);
                    }
                    if (dto.getStartTime() != null) record.setStartTime(dto.getStartTime());
                    if (dto.getEndTime() != null) record.setEndTime(dto.getEndTime());
                    if (dto.getWaterAmount() != null) record.setWaterAmount(dto.getWaterAmount());
                    if (dto.getFertilizerAmount() != null) record.setFertilizerAmount(dto.getFertilizerAmount());
                    if (dto.getFertilizerType() != null) record.setFertilizerType(dto.getFertilizerType());
                    if (dto.getAverageEc() != null) record.setAverageEc(dto.getAverageEc());
                    if (dto.getAveragePh() != null) record.setAveragePh(dto.getAveragePh());
                    if (dto.getExecutionMode() != null) record.setExecutionMode(dto.getExecutionMode());
                    if (dto.getIrrigationType() != null) record.setIrrigationType(dto.getIrrigationType());
                    if (dto.getStatus() != null) record.setStatus(dto.getStatus());
                    if (dto.getReason() != null) record.setReason(dto.getReason());
                    
                    return convertToDTO(irrigationRecordRepository.save(record));
                });
    }

    @Transactional
    public Optional<FertigationRecordDTO> completeRecord(UUID id) {
        return irrigationRecordRepository.findById(id)
                .map(record -> {
                    if (record.getEndTime() == null) {
                        record.setEndTime(LocalDateTime.now());
                    }
                    record.setStatus("completed");
                    
                    if (record.getValve() != null && record.getValve().getFlowRate() != null) {
                        long durationSeconds = Duration.between(record.getStartTime(), record.getEndTime()).getSeconds();
                        BigDecimal waterAmount = record.getValve().getFlowRate()
                                .multiply(BigDecimal.valueOf(durationSeconds / 3600.0));
                        record.setWaterAmount(waterAmount);
                    }
                    
                    if (record.getZone() != null) {
                        Map<String, Double> avgData = calculateAverageSensorData(record.getZone().getId(), 
                                record.getStartTime(), record.getEndTime());
                        if (avgData.containsKey("ec")) {
                            record.setAverageEc(BigDecimal.valueOf(avgData.get("ec")));
                        }
                        if (avgData.containsKey("ph")) {
                            record.setAveragePh(BigDecimal.valueOf(avgData.get("ph")));
                        }
                    }
                    
                    return convertToDTO(irrigationRecordRepository.save(record));
                });
    }

    @Transactional
    public boolean deleteRecord(UUID id) {
        if (irrigationRecordRepository.existsById(id)) {
            irrigationRecordRepository.deleteById(id);
            log.info("Deleted fertigation record: {}", id);
            return true;
        }
        return false;
    }

    public Map<String, Object> getStatistics(UUID zoneId, LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> stats = new HashMap<>();
        
        List<IrrigationRecord> records;
        if (zoneId != null) {
            records = irrigationRecordRepository.findByZoneIdAndTimeRange(zoneId, startTime, endTime);
        } else {
            records = irrigationRecordRepository.findByTimeRange(startTime, endTime);
        }
        
        stats.put("totalRecords", records.size());
        
        BigDecimal totalWater = records.stream()
                .filter(r -> r.getWaterAmount() != null)
                .map(IrrigationRecord::getWaterAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.put("totalWaterAmount", totalWater);
        
        BigDecimal totalFertilizer = records.stream()
                .filter(r -> r.getFertilizerAmount() != null)
                .map(IrrigationRecord::getFertilizerAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.put("totalFertilizerAmount", totalFertilizer);
        
        long autoCount = records.stream()
                .filter(r -> "auto".equals(r.getExecutionMode()))
                .count();
        long manualCount = records.stream()
                .filter(r -> "manual".equals(r.getExecutionMode()))
                .count();
        stats.put("autoExecutionCount", autoCount);
        stats.put("manualExecutionCount", manualCount);
        
        long irrigationCount = records.stream()
                .filter(r -> "irrigation".equals(r.getIrrigationType()))
                .count();
        long fertigationCount = records.stream()
                .filter(r -> "fertilization".equals(r.getIrrigationType()))
                .count();
        stats.put("irrigationCount", irrigationCount);
        stats.put("fertigationCount", fertigationCount);
        
        long totalDurationSeconds = records.stream()
                .filter(r -> r.getStartTime() != null && r.getEndTime() != null)
                .mapToLong(r -> Duration.between(r.getStartTime(), r.getEndTime()).getSeconds())
                .sum();
        stats.put("totalDurationSeconds", totalDurationSeconds);
        stats.put("averageDurationSeconds", records.size() > 0 ? totalDurationSeconds / records.size() : 0);
        
        if (zoneId != null) {
            stats.put("zoneWaterUsage", irrigationRecordRepository.sumWaterAmountByZoneIdAndTimeRange(zoneId, startTime, endTime));
            stats.put("zoneFertilizerUsage", irrigationRecordRepository.sumFertilizerAmountByZoneIdAndTimeRange(zoneId, startTime, endTime));
        }
        
        Map<String, Object> modeStats = new HashMap<>();
        modeStats.put("autoWaterUsage", irrigationRecordRepository.sumWaterAmountByExecutionModeAndTimeRange("auto", startTime, endTime));
        modeStats.put("manualWaterUsage", irrigationRecordRepository.sumWaterAmountByExecutionModeAndTimeRange("manual", startTime, endTime));
        modeStats.put("autoCount", irrigationRecordRepository.countByExecutionModeAndTimeRange("auto", startTime, endTime));
        modeStats.put("manualCount", irrigationRecordRepository.countByExecutionModeAndTimeRange("manual", startTime, endTime));
        stats.put("byExecutionMode", modeStats);
        
        return stats;
    }

    private Map<String, Double> calculateAverageSensorData(UUID zoneId, LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Double> result = new HashMap<>();
        
        List<Valve> valves = valveRepository.findByZoneId(zoneId);
        if (valves.isEmpty()) {
            return result;
        }
        
        List<Double> ecValues = new ArrayList<>();
        List<Double> phValues = new ArrayList<>();
        
        for (Valve valve : valves) {
            if (valve.getDevice() != null) {
                Map<String, Object> data = influxDBService.getLatestSensorData(valve.getDevice().getDeviceCode());
                if (data.get("ec") instanceof Number ec) {
                    ecValues.add(ec.doubleValue());
                }
                if (data.get("ph") instanceof Number ph) {
                    phValues.add(ph.doubleValue());
                }
            }
        }
        
        if (!ecValues.isEmpty()) {
            double avgEc = ecValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            result.put("ec", avgEc);
        }
        
        if (!phValues.isEmpty()) {
            double avgPh = phValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            result.put("ph", avgPh);
        }
        
        return result;
    }

    private FertigationRecordDTO convertToDTO(IrrigationRecord record) {
        FertigationRecordDTO dto = new FertigationRecordDTO();
        dto.setId(record.getId());
        dto.setStartTime(record.getStartTime());
        dto.setEndTime(record.getEndTime());
        dto.setWaterAmount(record.getWaterAmount());
        dto.setFertilizerAmount(record.getFertilizerAmount());
        dto.setFertilizerType(record.getFertilizerType());
        dto.setAverageEc(record.getAverageEc());
        dto.setAveragePh(record.getAveragePh());
        dto.setExecutionMode(record.getExecutionMode());
        dto.setIrrigationType(record.getIrrigationType());
        dto.setStatus(record.getStatus());
        dto.setReason(record.getReason());
        
        if (record.getStartTime() != null && record.getEndTime() != null) {
            dto.setDurationSeconds(Duration.between(record.getStartTime(), record.getEndTime()).getSeconds());
        }
        
        if (record.getZone() != null) {
            dto.setZoneId(record.getZone().getId());
            dto.setZoneName(record.getZone().getName());
        }
        if (record.getValve() != null) {
            dto.setValveId(record.getValve().getId());
        }
        if (record.getPlan() != null) {
            dto.setPlanId(record.getPlan().getId());
        }
        
        return dto;
    }
}
