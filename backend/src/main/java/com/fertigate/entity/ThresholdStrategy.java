package com.fertigate.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "threshold_strategies")
public class ThresholdStrategy {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crop_id")
    private Crop crop;

    @Column(name = "min_humidity", nullable = false, precision = 5, scale = 2)
    private BigDecimal minHumidity = new BigDecimal("50.00");

    @Column(name = "max_humidity", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxHumidity = new BigDecimal("80.00");

    @Column(name = "min_ec", precision = 5, scale = 2)
    private BigDecimal minEc = new BigDecimal("1.00");

    @Column(name = "max_ec", precision = 5, scale = 2)
    private BigDecimal maxEc = new BigDecimal("2.50");

    @Column(name = "min_ph", precision = 5, scale = 2)
    private BigDecimal minPh = new BigDecimal("5.50");

    @Column(name = "max_ph", precision = 5, scale = 2)
    private BigDecimal maxPh = new BigDecimal("7.50");

    @Column(name = "min_temperature", precision = 5, scale = 2)
    private BigDecimal minTemperature = new BigDecimal("10.00");

    @Column(name = "max_temperature", precision = 5, scale = 2)
    private BigDecimal maxTemperature = new BigDecimal("35.00");

    @Column(name = "max_wind_speed", precision = 5, scale = 2)
    private BigDecimal maxWindSpeed = new BigDecimal("15.00");

    @Column(name = "min_rainfall", precision = 5, scale = 2)
    private BigDecimal minRainfall = new BigDecimal("0.00");

    @Column(name = "weather_link_enabled")
    private Boolean weatherLinkEnabled = false;

    @Column(name = "avoid_rain_irrigation")
    private Boolean avoidRainIrrigation = true;

    @Column(name = "high_temp_irrigation")
    private Boolean highTempIrrigation = false;

    @Column(name = "is_active")
    private Boolean isActive = true;

    private Integer priority = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
