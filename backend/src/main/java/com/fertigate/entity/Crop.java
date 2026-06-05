package com.fertigate.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "crops")
public class Crop {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String variety;

    @Column(name = "growth_stage", nullable = false, length = 50)
    private String growthStage = "seedling";

    @Column(name = "min_humidity", nullable = false, precision = 5, scale = 2)
    private BigDecimal minHumidity = new BigDecimal("50.00");

    @Column(name = "max_humidity", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxHumidity = new BigDecimal("80.00");

    @Column(name = "optimal_ec", precision = 5, scale = 2)
    private BigDecimal optimalEc = new BigDecimal("1.80");

    @Column(name = "optimal_ph", precision = 5, scale = 2)
    private BigDecimal optimalPh = new BigDecimal("6.50");

    @Column(name = "water_requirement", precision = 8, scale = 2)
    private BigDecimal waterRequirement = new BigDecimal("0.00");

    @Column(name = "planting_date")
    private LocalDate plantingDate;

    @Column(name = "expected_harvest_date")
    private LocalDate expectedHarvestDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum GrowthStage {
        SEEDLING("seedling"),
        VEGETATIVE("vegetative"),
        FLOWERING("flowering"),
        FRUITING("fruiting"),
        RIPENING("ripening"),
        DORMANT("dormant");

        private final String value;

        GrowthStage(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
