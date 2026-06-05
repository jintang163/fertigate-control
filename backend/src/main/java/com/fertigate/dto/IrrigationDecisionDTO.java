package com.fertigate.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class IrrigationDecision {
    private UUID zoneId;
    private String zoneName;
    private UUID cropId;
    private Double currentHumidity;
    private Double minHumidity;
    private Double maxHumidity;
    private Boolean needIrrigation;
    private String reason;
    private Integer durationSeconds;
    private Double waterAmount;
}
