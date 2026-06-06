package com.fertigate.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "rotation_schedules")
public class RotationSchedule {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "strategy_id")
    private ThresholdStrategy strategy;

    @Column(name = "zone_ids", columnDefinition = "TEXT")
    private String zoneIds;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    private Integer duration;

    @Column(name = "interval_hours")
    private Integer intervalHours = 24;

    private Integer priority = 0;

    @Column(name = "water_amount", precision = 8, scale = 2)
    private BigDecimal waterAmount;

    @Column(name = "fertilizer_amount", precision = 8, scale = 2)
    private BigDecimal fertilizerAmount;

    @Column(name = "irrigation_type", length = 20)
    private String irrigationType = "irrigation";

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "last_execution")
    private LocalDateTime lastExecution;

    @Column(name = "next_execution")
    private LocalDateTime nextExecution;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
