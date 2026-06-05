package com.fertigate.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "valves")
public class Valve {

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

    @Column(name = "valve_number")
    private Integer valveNumber;

    @Column(name = "flow_rate", precision = 8, scale = 2)
    private BigDecimal flowRate = new BigDecimal("0.00");

    @Column(name = "is_open")
    private Boolean isOpen = false;

    @Column(name = "auto_control")
    private Boolean autoControl = true;

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
