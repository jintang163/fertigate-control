package com.fertigate.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class WeatherDataDTO {

    private BigDecimal temperature;

    private BigDecimal humidity;

    private BigDecimal windSpeed;

    private BigDecimal rainfall;

    private BigDecimal light;

    private LocalDateTime timestamp;

    public WeatherDataDTO() {
        this.timestamp = LocalDateTime.now();
    }
}
