package com.fertigate.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class GrowthStageRecordDTO {
    private UUID id;
    private UUID cropId;
    private UUID zoneId;
    private String growthStage;
    private LocalDate startDate;
    private LocalDate endDate;
    private String notes;
    private BigDecimal minHumidity;
    private BigDecimal maxHumidity;
    private BigDecimal optimalEc;
    private BigDecimal optimalPh;
    private BigDecimal waterRequirement;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
