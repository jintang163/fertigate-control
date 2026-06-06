package com.fertigate.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Data
public class RotationScheduleDTO {
    private UUID id;
    private String name;
    private String description;
    private UUID strategyId;
    private List<UUID> zoneIds;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer duration;
    private Integer intervalHours;
    private Integer priority;
    private BigDecimal waterAmount;
    private BigDecimal fertilizerAmount;
    private String irrigationType;
    private Boolean isActive;
    private LocalDateTime lastExecution;
    private LocalDateTime nextExecution;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
