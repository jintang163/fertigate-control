package com.fertigate.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class GanttTaskDTO {

    private UUID id;

    private String name;

    private LocalDateTime start;

    private LocalDateTime end;

    private double progress;

    private UUID zoneId;

    private String zoneName;

    private String status;

    public GanttTaskDTO() {
    }

    public GanttTaskDTO(UUID id, String name, LocalDateTime start, LocalDateTime end) {
        this.id = id;
        this.name = name;
        this.start = start;
        this.end = end;
        this.progress = 0;
        this.status = "pending";
    }
}
