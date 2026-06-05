package com.fertigate.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "devices")
public class Device {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "device_code", unique = true, nullable = false, length = 50)
    private String deviceCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    @Column(name = "modbus_address")
    private Integer modbusAddress;

    @Column(name = "modbus_port", length = 50)
    private String modbusPort;

    @Column(nullable = false, length = 20)
    private String status = "offline";

    @Column(name = "last_heartbeat")
    private LocalDateTime lastHeartbeat;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum DeviceType {
        SOIL_SENSOR("soil"),
        WEATHER_SENSOR("weather"),
        VALVE("valve"),
        GATEWAY("gateway");

        private final String value;

        DeviceType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public enum DeviceStatus {
        ONLINE("online"),
        OFFLINE("offline"),
        ERROR("error"),
        MAINTENANCE("maintenance");

        private final String value;

        DeviceStatus(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
