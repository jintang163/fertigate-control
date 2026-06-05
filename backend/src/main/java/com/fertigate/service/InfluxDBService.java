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
import java.util.concurrent.atomic.AtomicLong;

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

    @Value("${influxdb.write.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${influxdb.write.retry.delay-ms:1000}")
    private long retryDelayMs;

    private final AtomicLong writeCount = new AtomicLong(0);
    private final AtomicLong writeErrorCount = new AtomicLong(0);
    private final AtomicLong retryCount = new AtomicLong(0);

    public void writeSensorData(SensorDataDTO sensorData) {
        writeSensorDataInternal(sensorData);
    }

    public boolean writeSensorDataWithRetry(SensorDataDTO sensorData, int maxRetries) {
        int attempts = 0;
        int actualMaxRetries = Math.min(maxRetries, maxRetryAttempts);
        
        while (attempts <= actualMaxRetries) {
            try {
                writeSensorDataInternal(sensorData);
                return true;
            } catch (Exception e) {
                attempts++;
                retryCount.incrementAndGet();
                
                if (attempts > actualMaxRetries) {
                    writeErrorCount.incrementAndGet();
                    log.error("Failed to write sensor data after {} attempts: device={}, error={}",
                            attempts, sensorData.getDeviceCode(), e.getMessage());
                    return false;
                }
                
                log.warn("Retry {} writing sensor data: device={}, error={}",
                        attempts, sensorData.getDeviceCode(), e.getMessage());
                
                try {
                    Thread.sleep(retryDelayMs * attempts);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    private void writeSensorDataInternal(SensorDataDTO sensorData) {
        try {
            WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();
            
            Map<String, Double> values = sensorData.getValues();
            if (values == null || values.isEmpty()) {
                log.debug("No values to write for device: {}", sensorData.getDeviceCode());
                return;
            }

            Instant timestamp = determineTimestamp(sensorData);

            List<Point> points = new ArrayList<>();
            
            for (Map.Entry<String, Double> entry : values.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }

                Point point = Point.measurement("sensor_data")
                        .addTag("device_code", nullSafe(sensorData.getDeviceCode()))
                        .addTag("device_name", nullSafe(sensorData.getDeviceName()))
                        .addTag("device_type", nullSafe(sensorData.getDeviceType()))
                        .addTag("zone", nullSafe(sensorData.getZone()))
                        .addTag("gateway_id", nullSafe(sensorData.getGatewayId()))
                        .addField(entry.getKey(), entry.getValue())
                        .time(timestamp, WritePrecision.MS);

                if (sensorData.getIsRetransmission() != null) {
                    point.addTag("is_retransmission", sensorData.getIsRetransmission().toString());
                }

                points.add(point);
            }

            if (!points.isEmpty()) {
                writeApi.writePoints(points);
                writeCount.addAndGet(points.size());
                log.debug("Written {} points to InfluxDB for device: {}", 
                        points.size(), sensorData.getDeviceCode());
            }
            
        } catch (Exception e) {
            log.error("Error writing sensor data to InfluxDB: {}", e.getMessage());
            throw e;
        }
    }

    private Instant determineTimestamp(SensorDataDTO sensorData) {
        if (sensorData.getOriginalTimestamp() != null) {
            return sensorData.getOriginalTimestamp()
                    .atZone(ZoneId.systemDefault())
                    .toInstant();
        }
        
        if (sensorData.getTimestamp() != null) {
            return sensorData.getTimestamp()
                    .atZone(ZoneId.systemDefault())
                    .toInstant();
        }
        
        return Instant.now();
    }

    private String nullSafe(String value) {
        return value != null ? value : "unknown";
    }

    public void writeSensorDataBatch(List<SensorDataDTO> sensorDataList) {
        if (sensorDataList == null || sensorDataList.isEmpty()) {
            return;
        }

        try {
            WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();
            List<Point> allPoints = new ArrayList<>();

            for (SensorDataDTO sensorData : sensorDataList) {
                Map<String, Double> values = sensorData.getValues();
                if (values == null || values.isEmpty()) {
                    continue;
                }

                Instant timestamp = determineTimestamp(sensorData);

                for (Map.Entry<String, Double> entry : values.entrySet()) {
                    if (entry.getValue() == null) {
                        continue;
                    }

                    Point point = Point.measurement("sensor_data")
                            .addTag("device_code", nullSafe(sensorData.getDeviceCode()))
                            .addTag("device_name", nullSafe(sensorData.getDeviceName()))
                            .addTag("device_type", nullSafe(sensorData.getDeviceType()))
                            .addTag("zone", nullSafe(sensorData.getZone()))
                            .addTag("gateway_id", nullSafe(sensorData.getGatewayId()))
                            .addField(entry.getKey(), entry.getValue())
                            .time(timestamp, WritePrecision.MS);

                    if (sensorData.getIsRetransmission() != null) {
                        point.addTag("is_retransmission", sensorData.getIsRetransmission().toString());
                    }

                    allPoints.add(point);
                }
            }

            if (!allPoints.isEmpty()) {
                writeApi.writePoints(allPoints);
                writeCount.addAndGet(allPoints.size());
                log.info("Batch written {} points to InfluxDB for {} devices", 
                        allPoints.size(), sensorDataList.size());
            }
            
        } catch (Exception e) {
            log.error("Error writing batch sensor data to InfluxDB: {}", e.getMessage());
            
            log.info("Falling back to individual writes");
            for (SensorDataDTO data : sensorDataList) {
                try {
                    writeSensorDataWithRetry(data, 2);
                } catch (Exception ex) {
                    log.error("Fallback write also failed: {}", ex.getMessage());
                }
            }
        }
    }

    public boolean writeSensorDataBatchWithRetry(List<SensorDataDTO> sensorDataList, int maxRetries) {
        int attempts = 0;
        int actualMaxRetries = Math.min(maxRetries, maxRetryAttempts);
        
        while (attempts <= actualMaxRetries) {
            try {
                writeSensorDataBatch(sensorDataList);
                return true;
            } catch (Exception e) {
                attempts++;
                retryCount.incrementAndGet();
                
                if (attempts > actualMaxRetries) {
                    writeErrorCount.incrementAndGet();
                    log.error("Failed to write batch after {} attempts: {}", attempts, e.getMessage());
                    return false;
                }
                
                log.warn("Retry {} writing batch: {}", attempts, e.getMessage());
                
                try {
                    Thread.sleep(retryDelayMs * attempts);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
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

    public Map<String, Object> getWriteStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalWrites", writeCount.get());
        stats.put("totalErrors", writeErrorCount.get());
        stats.put("totalRetries", retryCount.get());
        stats.put("errorRate", writeCount.get() > 0 
                ? String.format("%.2f%%", (double) writeErrorCount.get() / writeCount.get() * 100)
                : "0.00%");
        return stats;
    }
}
