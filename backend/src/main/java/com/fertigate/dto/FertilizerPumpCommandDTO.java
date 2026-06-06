package com.fertigate.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FertilizerPumpCommandDTO {
    private String deviceCode;
    private Boolean run;
    private Integer openingDegree;
    private String reason;
    private String zone;
    private LocalDateTime timestamp;
}
