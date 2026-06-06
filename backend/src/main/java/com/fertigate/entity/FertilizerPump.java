package com.fertigate.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 施肥泵实体类
 * 用于管理灌溉系统中的施肥泵设备，支持启停控制、开度调节、压力监控
 * 可关联到具体灌区，支持手动/自动控制模式切换
 * 支持安全联锁保护，过载时自动停止
 */
@Data
@Entity
@Table(name = "fertilizer_pumps")
public class FertilizerPump {

    /**
     * 施肥泵唯一标识符，UUID自动生成
     */
    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 关联的设备实体，存储设备通信信息
     * 通过device_code进行MQTT控制指令下发
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    /**
     * 关联的灌区，标识该施肥泵所属的灌溉区域
     * 用于按灌区进行施肥泵控制和统计
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    /**
     * 泵编号，用于现场标识和管理
     * 示例：P-001, P-002
     */
    @Column(name = "pump_number")
    private Integer pumpNumber;

    /**
     * 额定流量，单位：m³/h
     * 用于计算施肥用量
     */
    @Column(name = "flow_rate", precision = 8, scale = 2)
    private BigDecimal flowRate = new BigDecimal("0.00");

    /**
     * 最大工作压力，单位：MPa
     * 用于过载检测和安全保护
     * 默认值：10.00 MPa
     */
    @Column(name = "max_pressure", precision = 8, scale = 2)
    private BigDecimal maxPressure = new BigDecimal("10.00");

    /**
     * 当前工作压力，单位：MPa
     * 实时监控施肥泵运行状态，过载时触发安全联锁
     */
    @Column(name = "current_pressure", precision = 8, scale = 2)
    private BigDecimal currentPressure = new BigDecimal("0.00");

    /**
     * 泵运行状态
     * true：运行中，false：已停止
     * 默认值：false
     */
    @Column(name = "is_running")
    private Boolean isRunning = false;

    /**
     * 是否允许自动控制
     * true：允许系统自动控制，false：仅允许手动操作
     * 默认值：true
     */
    @Column(name = "auto_control")
    private Boolean autoControl = true;

    /**
     * 泵开度，百分比值
     * 范围：0-100，0表示完全关闭，100表示完全打开
     * 支持施肥流量调节功能
     * 默认值：0
     */
    @Column(name = "opening_degree")
    private Integer openingDegree = 0;

    /**
     * 最后一次操作时间
     * 记录泵启停或开度调节的时间
     */
    @Column(name = "last_operation")
    private LocalDateTime lastOperation;

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
}
