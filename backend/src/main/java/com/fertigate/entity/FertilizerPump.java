package com.fertigate.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "fertilizer_pumps")
public class FertilizerPump {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    @Column(name = "pump_number")
    private Integer pumpNumber;

    @Column(name = "flow_rate", precision = 8, scale = 2)
    private BigDecimal flowRate = new BigDecimal("0.00");

    @Column(name = "max_pressure", precision = 8, scale = 2)
    private BigDecimal maxPressure = new BigDecimal("10.00");

    @Column(name = "current_pressure", precision = 8, scale = 2)
    private BigDecimal currentPressure = new BigDecimal("0.00");

    @Column(name = "is_running")
    private Boolean isRunning = false;

    @Column(name = "auto_control")
    private Boolean autoControl = true;

    @Column(name = "opening_degree")
    private Integer openingDegree = 0;

    @Column(name = "last_operation")
    private LocalDateTime lastOperation;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
