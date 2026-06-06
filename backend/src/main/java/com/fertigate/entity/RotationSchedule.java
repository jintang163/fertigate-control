package com.fertigate.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * 轮灌调度实体类
 * 用于配置分区轮灌计划，支持按灌区、时间、优先级生成自动执行的灌溉调度
 * 可设置灌溉类型（灌溉/施肥）、用水量、用肥量、执行周期等参数
 * 系统每30秒自动检查并执行到期的轮灌计划
 */
@Data
@Entity
@Table(name = "rotation_schedules")
public class RotationSchedule {

    /**
     * 调度唯一标识符，UUID自动生成
     */
    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 调度名称，便于识别和管理
     * 示例：1号灌区每日早灌、小麦追肥计划
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * 调度描述，详细说明调度的目的和执行规则
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * 关联的阈值策略
     * 执行轮灌时参考该策略的阈值配置
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "strategy_id")
    private ThresholdStrategy strategy;

    /**
     * 关联的灌区ID列表，逗号分隔的UUID字符串
     * 支持多个灌区按顺序执行轮灌
     * 格式：uuid1,uuid2,uuid3
     */
    @Column(name = "zone_ids", columnDefinition = "TEXT")
    private String zoneIds;

    /**
     * 每日开始执行时间
     * 示例：06:00表示每天早上6点开始执行
     */
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    /**
     * 每日结束执行时间
     * 可选，用于限制调度的执行时间段
     */
    @Column(name = "end_time")
    private LocalTime endTime;

    /**
     * 单次灌溉持续时间，单位：秒
     * 打开阀门后持续的时间，到时自动关闭
     */
    private Integer duration;

    /**
     * 执行间隔，单位：小时
     * 两次执行之间的间隔时间
     * 默认值：24小时（每日一次）
     */
    @Column(name = "interval_hours")
    private Integer intervalHours = 24;

    /**
     * 调度优先级，数值越大优先级越高
     * 多个调度同时到期时，优先级高的先执行
     * 默认值：0
     */
    private Integer priority = 0;

    /**
     * 计划用水量，单位：m³
     * 本次灌溉的预计用水量
     */
    @Column(name = "water_amount", precision = 8, scale = 2)
    private BigDecimal waterAmount;

    /**
     * 计划用肥量，单位：kg
     * 本次施肥的预计用肥量（仅施肥类型有效）
     */
    @Column(name = "fertilizer_amount", precision = 8, scale = 2)
    private BigDecimal fertilizerAmount;

    /**
     * 灌溉类型
     * irrigation：单纯灌溉
     * fertilization：灌溉加施肥
     * 默认值：irrigation
     */
    @Column(name = "irrigation_type", length = 20)
    private String irrigationType = "irrigation";

    /**
     * 调度是否启用
     * true：生效中，false：已停用
     * 默认值：true
     */
    @Column(name = "is_active")
    private Boolean isActive = true;

    /**
     * 最后一次执行时间
     * 记录上次成功执行的时间
     */
    @Column(name = "last_execution")
    private LocalDateTime lastExecution;

    /**
     * 下一次执行时间
     * 系统自动计算的下次执行时间
     */
    @Column(name = "next_execution")
    private LocalDateTime nextExecution;

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
