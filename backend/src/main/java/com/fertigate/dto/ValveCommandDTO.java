package com.fertigate.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ValveCommandDTO {
    private String deviceCode;
    private Boolean open;
    private String reason;
    private String zone;
    private LocalDateTime timestamp;
}
