package com.fertigate.dto;

import lombok.Data;

@Data
public class SensorDataPointDTO {

    private String time;

    private double value;

    private String deviceCode;

    private String zone;

    public SensorDataPointDTO() {
    }

    public SensorDataPointDTO(String time, double value, String deviceCode, String zone) {
        this.time = time;
        this.value = value;
        this.deviceCode = deviceCode;
        this.zone = zone;
    }
}
