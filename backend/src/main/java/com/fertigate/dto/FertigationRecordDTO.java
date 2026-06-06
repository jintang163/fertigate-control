package com.fertigate.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class FertigationRecordDTO {
    private UUID id;
    private UUID zoneId;
    private String zoneName;
    private UUID valveId;
    private UUID planId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationSeconds;
    private BigDecimal waterAmount;
    private BigDecimal fertilizerAmount;
    private String fertilizerType;
    private BigDecimal averageEc;
    private BigDecimal averagePh;
    private String executionMode;
    private String irrigationType;
    private String status;
    private String reason;
}
