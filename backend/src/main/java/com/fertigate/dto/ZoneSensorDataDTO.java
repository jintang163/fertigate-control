package com.fertigate.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class ZoneSensorDataDTO {

    private UUID zoneId;

    private String zoneName;

    private BigDecimal humidity;

    private BigDecimal ec;

    private BigDecimal ph;

    private BigDecimal temperature;

    private LocalDateTime timestamp;

    public ZoneSensorDataDTO() {
    }

    public ZoneSensorDataDTO(UUID zoneId, String zoneName) {
        this.zoneId = zoneId;
        this.zoneName = zoneName;
    }
}
