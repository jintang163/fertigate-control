package com.fertigate.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class DeviceStatusDTO {
    private UUID id;
    private String deviceCode;
    private String name;
    private String type;
    private String status;
    private BigDecimal currentValue;
    private String unit;
    private LocalDateTime lastHeartbeat;
    private UUID zoneId;
    private String zoneName;
    private Boolean isOnline;
    private Long offlineDurationSeconds;
}
