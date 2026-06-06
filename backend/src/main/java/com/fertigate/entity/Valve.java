package com.fertigate.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 电磁阀实体类
 * 用于管理灌溉系统中的电磁阀设备，支持开关控制、开度调节、流量统计
 * 可关联到具体灌区，支持手动/自动控制模式切换
 */
@Data
@Entity
@Table(name = "valves")
public class Valve {

    /**
     * 电磁阀唯一标识符，UUID自动生成
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
     * 关联的灌区，标识该阀门所属的灌溉区域
     * 用于按灌区进行阀门控制和统计
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    /**
     * 阀门编号，用于现场标识和管理
     * 示例：V-001, V-002
     */
    @Column(name = "valve_number")
    private Integer valveNumber;

    /**
     * 额定流量，单位：m³/h
     * 用于计算灌溉用水量
     */
    @Column(name = "flow_rate", precision = 8, scale = 2)
    private BigDecimal flowRate = new BigDecimal("0.00");

    /**
     * 阀门开关状态
     * true：开启，false：关闭
     * 默认值：false
     */
    @Column(name = "is_open")
    private Boolean isOpen = false;

    /**
     * 是否允许自动控制
     * true：允许系统自动控制，false：仅允许手动操作
     * 默认值：true
     */
    @Column(name = "auto_control")
    private Boolean autoControl = true;

    /**
     * 阀门开度，百分比值
     * 范围：0-100，0表示完全关闭，100表示完全打开
     * 支持流量调节功能
     * 默认值：100
     */
    @Column(name = "opening_degree")
    private Integer openingDegree = 100;

    /**
     * 最后一次操作时间
     * 记录阀门开关或开度调节的时间
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
