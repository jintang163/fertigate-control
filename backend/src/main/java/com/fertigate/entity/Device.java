package com.fertigate.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 设备实体类
 * 用于管理系统中所有的硬件设备，包括传感器、电磁阀、施肥泵、网关等
 * 支持设备注册、状态监控、在线/离线检测等功能
 */
@Data
@Entity
@Table(name = "devices")
public class Device {

    /**
     * 设备唯一标识符，UUID自动生成
     */
    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 设备编码，全局唯一，用于MQTT通信和设备识别
     * 格式示例：soil-sensor-001, valve-001, pump-001
     */
    @Column(name = "device_code", unique = true, nullable = false, length = 50)
    private String deviceCode;

    /**
     * 设备名称，用于界面显示，便于用户识别
     * 示例：1号灌区土壤湿度传感器、主管道电磁阀
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * 设备类型，参考DeviceType枚举
     * 可选值：soil(土壤传感器), weather(气象传感器), valve(电磁阀), 
     *        fertilizer_pump(施肥泵), flow_sensor(流量传感器), 
     *        pressure_sensor(压力传感器), gateway(网关)
     */
    @Column(nullable = false, length = 50)
    private String type;

    /**
     * 关联的灌区，设备所属的灌溉区域
     * 用于按灌区进行设备管理和数据统计
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    /**
     * Modbus通信地址，用于RS485总线通信的设备地址
     * 范围：1-247
     */
    @Column(name = "modbus_address")
    private Integer modbusAddress;

    /**
     * Modbus通信端口，标识设备连接的串口或端口号
     * 示例：COM1, /dev/ttyUSB0
     */
    @Column(name = "modbus_port", length = 50)
    private String modbusPort;

    /**
     * 设备状态，参考DeviceStatus枚举
     * 可选值：online(在线), offline(离线), error(故障), maintenance(维护中)
     * 默认值：offline
     */
    @Column(nullable = false, length = 20)
    private String status = "offline";

    /**
     * 最后心跳时间，用于在线/离线检测
     * 超过设定超时时间未更新则标记为离线
     */
    @Column(name = "last_heartbeat")
    private LocalDateTime lastHeartbeat;

    /**
     * 记录创建时间，默认当前时间
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * 记录更新时间，自动更新
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    /**
     * 更新前自动调用，设置更新时间
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 设备当前测量值，用于显示传感器实时数据
     * 如土壤湿度百分比、温度值等
     */
    @Column(name = "current_value", precision = 10, scale = 2)
    private java.math.BigDecimal currentValue;

    /**
     * 测量值单位，如%, °C, m³/h, MPa等
     */
    @Column(length = 20)
    private String unit;

    /**
     * 设备类型枚举
     * 定义系统支持的所有设备类型
     */
    public enum DeviceType {
        /** 土壤传感器：测量土壤湿度、温度、EC、pH等 */
        SOIL_SENSOR("soil"),
        /** 气象传感器：测量空气温度、湿度、风速、降雨量等 */
        WEATHER_SENSOR("weather"),
        /** 电磁阀：控制灌溉水路通断 */
        VALVE("valve"),
        /** 施肥泵：控制肥料注入 */
        FERTILIZER_PUMP("fertilizer_pump"),
        /** 流量传感器：测量管道水流量 */
        FLOW_SENSOR("flow_sensor"),
        /** 压力传感器：测量管道水压 */
        PRESSURE_SENSOR("pressure_sensor"),
        /** 网关：边缘计算网关，负责设备通信和数据采集 */
        GATEWAY("gateway");

        private final String value;

        DeviceType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 设备状态枚举
     * 定义设备的运行状态
     */
    public enum DeviceStatus {
        /** 在线：设备正常通信中 */
        ONLINE("online"),
        /** 离线：超过超时时间未收到心跳 */
        OFFLINE("offline"),
        /** 故障：设备上报异常或诊断发现问题 */
        ERROR("error"),
        /** 维护中：设备正在进行维护保养 */
        MAINTENANCE("maintenance");

        private final String value;

        DeviceStatus(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
