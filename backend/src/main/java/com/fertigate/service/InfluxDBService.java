package com.fertigate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fertigate.config.InfluxDBConfig;
import com.fertigate.dto.SensorDataDTO;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxTable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class InfluxDBService {

    private final InfluxDBClient influxDBClient;
    private final InfluxDBConfig influxDBConfig;
    private final ObjectMapper objectMapper;

    @Value("${influxdb.bucket}")
    private String bucket;

    @Value("${influxdb.org}")
    private String org;

    public void writeSensorData(SensorDataDTO sensorData) {
        try {
            WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();
            
            Map<String, Double> values = sensorData.getValues();
            if (values == null || values.isEmpty()) {
                return;
            }

            Instant timestamp = sensorData.getTimestamp() != null 
                ? sensorData.getTimestamp().atZone(ZoneId.systemDefault()).toInstant()
                : Instant.now();

            for (Map.Entry<String, Double> entry : values.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }

                Point point = Point.measurement("sensor_data")
                        .addTag("device_code", sensorData.getDeviceCode())
                        .addTag("device_name", sensorData.getDeviceName())
                        .addTag("device_type", sensorData.getDeviceType())
                        .addTag("zone", sensorData.getZone())
                        .addTag("gateway_id", sensorData.getGatewayId())
                        .addField(entry.getKey(), entry.getValue())
                        .time(timestamp, WritePrecision.MS);

                writeApi.writePoint(point);
            }
            
            log.debug("Sensor data written to InfluxDB: {}", sensorData.getDeviceCode());
        } catch (Exception e) {
            log.error("Error writing sensor data to InfluxDB: {}", e.getMessage());
        }
    }

    public void writeSensorDataBatch(List<SensorDataDTO> sensorDataList) {
        for (SensorDataDTO data : sensorDataList) {
            writeSensorData(data);
        }
    }

    public List<Map<String, Object>> querySensorData(String deviceCode, String sensorType, 
                                                     LocalDateTime startTime, LocalDateTime endTime, 
                                                     int limit) {
        try {
            String fluxQuery = String.format(
                "from(bucket: \"%s\") " +
                "|> range(start: %s, stop: %s) " +
                "|> filter(fn: (r) => r[\"_measurement\"] == \"sensor_data\") " +
                "|> filter(fn: (r) => r[\"device_code\"] == \"%s\") " +
                "|> filter(fn: (r) => r[\"_field\"] == \"%s\") " +
                "|> sort(columns: [\"_time\"], desc: true) " +
                "|> limit(n: %d)",
                bucket,
                startTime.atZone(ZoneId.systemDefault()).toInstant().toString(),
                endTime.atZone(ZoneId.systemDefault()).toInstant().toString(),
                deviceCode,
                sensorType,
                limit
            );

            List<FluxTable> tables = influxDBClient.getQueryApi().query(fluxQuery, org);
            List<Map<String, Object>> result = new ArrayList<>();

            for (FluxTable table : tables) {
                table.getRecords().forEach(record -> {
                    Map<String, Object> dataPoint = new HashMap<>();
                    dataPoint.put("time", record.getTime());
                    dataPoint.put("value", record.getValue());
                    dataPoint.put("device_code", record.getValueByKey("device_code"));
                    dataPoint.put("zone", record.getValueByKey("zone"));
                    result.add(dataPoint);
                });
            }

            return result;
        } catch (Exception e) {
            log.error("Error querying sensor data: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public Map<String, Object> getLatestSensorData(String deviceCode) {
        try {
            String fluxQuery = String.format(
                "from(bucket: \"%s\") " +
                "|> range(start: -1h) " +
                "|> filter(fn: (r) => r[\"_measurement\"] == \"sensor_data\") " +
                "|> filter(fn: (r) => r[\"device_code\"] == \"%s\") " +
                "|> sort(columns: [\"_time\"], desc: true) " +
                "|> limit(n: 1)",
                bucket, deviceCode
            );

            List<FluxTable> tables = influxDBClient.getQueryApi().query(fluxQuery, org);
            Map<String, Object> result = new HashMap<>();

            for (FluxTable table : tables) {
                table.getRecords().forEach(record -> {
                    String field = record.getField();
                    Object value = record.getValue();
                    result.put(field, value);
                    result.put("timestamp", record.getTime());
                });
            }

            return result;
        } catch (Exception e) {
            log.error("Error getting latest sensor data: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    public List<Map<String, Object>> getLatestDataForDeviceType(String deviceType, int limit) {
        try {
            String fluxQuery = String.format(
                "from(bucket: \"%s\") " +
                "|> range(start: -24h) " +
                "|> filter(fn: (r) => r[\"_measurement\"] == \"sensor_data\") " +
                "|> filter(fn: (r) => r[\"device_type\"] == \"%s\") " +
                "|> sort(columns: [\"_time\"], desc: true) " +
                "|> limit(n: %d)",
                bucket, deviceType, limit
            );

            List<FluxTable> tables = influxDBClient.getQueryApi().query(fluxQuery, org);
            List<Map<String, Object>> result = new ArrayList<>();

            for (FluxTable table : tables) {
                table.getRecords().forEach(record -> {
                    Map<String, Object> dataPoint = new HashMap<>();
                    dataPoint.put("time", record.getTime());
                    dataPoint.put("field", record.getField());
                    dataPoint.put("value", record.getValue());
                    dataPoint.put("device_code", record.getValueByKey("device_code"));
                    dataPoint.put("zone", record.getValueByKey("zone"));
                    result.add(dataPoint);
                });
            }

            return result;
        } catch (Exception e) {
            log.error("Error getting latest data for device type: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
