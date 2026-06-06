package com.fertigate.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class SafetyInterlockDTO {
    private UUID id;
    private String interlockType;
    private String level;
    private String message;
    private UUID deviceId;
    private String deviceCode;
    private String deviceName;
    private UUID zoneId;
    private String zoneName;
    private String sensorValue;
    private String thresholdValue;
    private Boolean autoStopped;
    private LocalDateTime triggeredAt;
    private Boolean acknowledged;
    private LocalDateTime acknowledgedAt;
}
