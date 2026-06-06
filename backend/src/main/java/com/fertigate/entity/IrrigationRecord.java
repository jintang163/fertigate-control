package com.fertigate.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "irrigation_records")
public class IrrigationRecord {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "valve_id")
    private Valve valve;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private IrrigationPlan plan;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "water_amount", precision = 8, scale = 2)
    private BigDecimal waterAmount = new BigDecimal("0.00");

    @Column(length = 200)
    private String reason;

    @Column(name = "execution_mode", length = 20)
    private String executionMode = "auto";

    @Column(name = "irrigation_type", length = 20)
    private String irrigationType = "irrigation";

    @Column(name = "fertilizer_amount", precision = 8, scale = 2)
    private java.math.BigDecimal fertilizerAmount = new java.math.BigDecimal("0.00");

    @Column(name = "fertilizer_type", length = 50)
    private String fertilizerType;

    @Column(name = "average_ec", precision = 5, scale = 2)
    private java.math.BigDecimal averageEc;

    @Column(name = "average_ph", precision = 5, scale = 2)
    private java.math.BigDecimal averagePh;

    @Column(length = 20)
    private String status = "completed";

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
