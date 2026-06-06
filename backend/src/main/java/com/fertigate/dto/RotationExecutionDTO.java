package com.fertigate.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class RotationExecutionDTO {
    private UUID scheduleId;
    private UUID zoneId;
    private LocalDateTime startTime;
    private Integer duration;
    private BigDecimal waterAmount;
    private BigDecimal fertilizerAmount;
    private String irrigationType;
    private String status;
    private String message;
}
