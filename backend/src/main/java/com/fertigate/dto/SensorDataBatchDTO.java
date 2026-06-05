package com.fertigate.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SensorDataBatchDTO {
    private String gatewayId;
    private LocalDateTime batchTimestamp;
    private Integer batchSize;
    private Boolean isBackfill;
    private List<SensorDataDTO> data;
}
