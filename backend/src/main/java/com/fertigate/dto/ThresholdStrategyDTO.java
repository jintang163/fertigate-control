package com.fertigate.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class ThresholdStrategyDTO {
    private UUID id;
    private String name;
    private String description;
    private UUID zoneId;
    private UUID cropId;
    private BigDecimal minHumidity;
    private BigDecimal maxHumidity;
    private BigDecimal minEc;
    private BigDecimal maxEc;
    private BigDecimal minPh;
    private BigDecimal maxPh;
    private BigDecimal minTemperature;
    private BigDecimal maxTemperature;
    private BigDecimal maxWindSpeed;
    private BigDecimal minRainfall;
    private Boolean weatherLinkEnabled;
    private Boolean avoidRainIrrigation;
    private Boolean highTempIrrigation;
    private Boolean isActive;
    private Integer priority;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
