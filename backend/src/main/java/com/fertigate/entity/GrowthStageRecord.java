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
@Table(name = "growth_stage_records")
public class GrowthStageRecord {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crop_id")
    private Crop crop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    @Column(name = "growth_stage", nullable = false, length = 50)
    private String growthStage;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "min_humidity", precision = 5, scale = 2)
    private BigDecimal minHumidity;

    @Column(name = "max_humidity", precision = 5, scale = 2)
    private BigDecimal maxHumidity;

    @Column(name = "optimal_ec", precision = 5, scale = 2)
    private BigDecimal optimalEc;

    @Column(name = "optimal_ph", precision = 5, scale = 2)
    private BigDecimal optimalPh;

    @Column(name = "water_requirement", precision = 8, scale = 2)
    private BigDecimal waterRequirement;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
