package com.fertigate.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @Column(name = "alert_type", nullable = false, length = 50)
    private String alertType;

    @Column(nullable = false, length = 20)
    private String level = "warning";

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "sensor_value", precision = 10, scale = 2)
    private BigDecimal sensorValue;

    @Column(name = "threshold_value", precision = 10, scale = 2)
    private BigDecimal thresholdValue;

    @Column(name = "is_acknowledged")
    private Boolean isAcknowledged = false;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum AlertLevel {
        INFO("info"),
        WARNING("warning"),
        CRITICAL("critical"),
        ERROR("error");

        private final String value;

        AlertLevel(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
