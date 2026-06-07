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
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    public byte[] exportToExcel(LocalDateTime startTime, LocalDateTime endTime, UUID zoneId) {
        log.info("Exporting fertigation records to Excel, startTime: {}, endTime: {}, zoneId: {}", startTime, endTime, zoneId);

        List<IrrigationRecord> records;
        if (zoneId != null && startTime != null && endTime != null) {
            records = irrigationRecordRepository.findByZoneIdAndTimeRange(zoneId, startTime, endTime);
        } else if (zoneId != null) {
            records = irrigationRecordRepository.findByZoneId(zoneId);
        } else if (startTime != null && endTime != null) {
            records = irrigationRecordRepository.findByTimeRange(startTime, endTime);
        } else {
            records = irrigationRecordRepository.findAll();
        }

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("灌肥台账");
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle summaryStyle = createSummaryStyle(workbook);

            String[] headers = {"记录ID", "灌区名称", "开始时间", "结束时间", "持续时长(分钟)", 
                              "用水量(m³)", "用肥量(kg)", "肥料类型", "平均EC", "平均pH", 
                              "执行模式", "灌溉类型", "状态", "备注"};

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            BigDecimal totalWater = BigDecimal.ZERO;
            BigDecimal totalFertilizer = BigDecimal.ZERO;

            for (IrrigationRecord record : records) {
                Row row = sheet.createRow(rowNum++);
                int colNum = 0;

                row.createCell(colNum++).setCellValue(record.getId() != null ? record.getId().toString() : "");
                row.createCell(colNum++).setCellValue(record.getZone() != null ? record.getZone().getName() : "");
                row.createCell(colNum++).setCellValue(record.getStartTime() != null ? record.getStartTime().format(dateFormatter) : "");
                row.createCell(colNum++).setCellValue(record.getEndTime() != null ? record.getEndTime().format(dateFormatter) : "");

                long durationMinutes = 0;
                if (record.getStartTime() != null && record.getEndTime() != null) {
                    durationMinutes = Duration.between(record.getStartTime(), record.getEndTime()).toMinutes();
                }
                row.createCell(colNum++).setCellValue(durationMinutes);

                Cell waterCell = row.createCell(colNum++);
                waterCell.setCellValue(record.getWaterAmount() != null ? record.getWaterAmount().doubleValue() : 0);
                waterCell.setCellStyle(dataStyle);
                if (record.getWaterAmount() != null) {
                    totalWater = totalWater.add(record.getWaterAmount());
                }

                Cell fertilizerCell = row.createCell(colNum++);
                fertilizerCell.setCellValue(record.getFertilizerAmount() != null ? record.getFertilizerAmount().doubleValue() : 0);
                fertilizerCell.setCellStyle(dataStyle);
                if (record.getFertilizerAmount() != null) {
                    totalFertilizer = totalFertilizer.add(record.getFertilizerAmount());
                }

                row.createCell(colNum++).setCellValue(record.getFertilizerType() != null ? record.getFertilizerType() : "");
                row.createCell(colNum++).setCellValue(record.getAverageEc() != null ? record.getAverageEc().doubleValue() : 0);
                row.createCell(colNum++).setCellValue(record.getAveragePh() != null ? record.getAveragePh().doubleValue() : 0);
                row.createCell(colNum++).setCellValue(getExecutionModeText(record.getExecutionMode()));
                row.createCell(colNum++).setCellValue(getIrrigationTypeText(record.getIrrigationType()));
                row.createCell(colNum++).setCellValue(getStatusText(record.getStatus()));
                row.createCell(colNum++).setCellValue(record.getReason() != null ? record.getReason() : "");
            }

            Row summaryRow = sheet.createRow(rowNum);
            int summaryCol = 0;
            summaryRow.createCell(summaryCol++).setCellValue("合计");
            Cell totalRecordsCell = summaryRow.createCell(summaryCol++);
            totalRecordsCell.setCellValue("总记录数: " + records.size());
            totalRecordsCell.setCellStyle(summaryStyle);
            summaryCol += 3;

            Cell totalWaterCell = summaryRow.createCell(summaryCol++);
            totalWaterCell.setCellValue(totalWater.setScale(2, RoundingMode.HALF_UP).doubleValue());
            totalWaterCell.setCellStyle(summaryStyle);

            Cell totalFertilizerCell = summaryRow.createCell(summaryCol++);
            totalFertilizerCell.setCellValue(totalFertilizer.setScale(2, RoundingMode.HALF_UP).doubleValue());
            totalFertilizerCell.setCellStyle(summaryStyle);

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            log.info("Excel export completed, total records: {}", records.size());
            return out.toByteArray();

        } catch (IOException e) {
            log.error("Failed to export Excel", e);
            throw new RuntimeException("导出Excel失败", e);
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("0.00"));
        return style;
    }

    private CellStyle createSummaryStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("0.00"));
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private String getExecutionModeText(String mode) {
        if ("auto".equals(mode)) {
            return "自动";
        } else if ("manual".equals(mode)) {
            return "手动";
        }
        return mode != null ? mode : "";
    }

    private String getIrrigationTypeText(String type) {
        if ("irrigation".equals(type)) {
            return "灌溉";
        } else if ("fertilization".equals(type)) {
            return "施肥";
        }
        return type != null ? type : "";
    }

    private String getStatusText(String status) {
        return switch (status) {
            case "running" -> "进行中";
            case "completed" -> "已完成";
            case "emergency_stopped" -> "紧急停止";
            case "interrupted" -> "异常中断";
            default -> status != null ? status : "";
        };
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
