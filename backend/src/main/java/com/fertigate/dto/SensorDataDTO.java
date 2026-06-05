package com.fertigate.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class SensorDataDTO {
    private String deviceCode;
    private String deviceName;
    private String deviceType;
    private String zone;
    private String gatewayId;
    private LocalDateTime timestamp;
    private Map<String, Double> values;
    private Boolean interlockSafe;
}
