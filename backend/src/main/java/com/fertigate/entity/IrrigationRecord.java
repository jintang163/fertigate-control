package com.fertigate.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 灌肥台账记录实体类
 * 用于记录每次灌水/施肥的详细信息，包括开始时间、结束时间、用量、执行方式等
 * 支持按灌区、时间范围、执行方式（自动/手动）进行统计分析
 * 是系统用水用肥统计和溯源的核心数据
 */
@Data
@Entity
@Table(name = "irrigation_records")
public class IrrigationRecord {

    /**
     * 记录唯一标识符，UUID自动生成
     */
    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 关联的灌区
     * 记录本次灌溉所属的灌溉区域
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    /**
     * 关联的电磁阀
     * 记录本次灌溉使用的阀门
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "valve_id")
    private Valve valve;

    /**
     * 关联的灌溉计划
     * 可选，记录本次灌溉所属的计划
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private IrrigationPlan plan;

    /**
     * 灌溉开始时间
     * 记录阀门打开或泵启动的时间
     */
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    /**
     * 灌溉结束时间
     * 记录阀门关闭或泵停止的时间
     * null表示灌溉正在进行中
     */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    /**
     * 实际用水量，单位：m³
     * 根据阀门流量和灌溉时长计算，或流量计实际测量值
     */
    @Column(name = "water_amount", precision = 8, scale = 2)
    private BigDecimal waterAmount = new BigDecimal("0.00");

    /**
     * 灌溉原因
     * 记录本次灌溉的触发原因，如：手动操作、轮灌调度、土壤湿度过低等
     */
    @Column(length = 200)
    private String reason;

    /**
     * 执行方式
     * auto：系统自动执行
     * manual：人工手动操作
     * 默认值：auto
     */
    @Column(name = "execution_mode", length = 20)
    private String executionMode = "auto";

    /**
     * 灌溉类型
     * irrigation：单纯灌溉（浇水）
     * fertilization：灌溉加施肥（水肥一体化）
     * 默认值：irrigation
     */
    @Column(name = "irrigation_type", length = 20)
    private String irrigationType = "irrigation";

    /**
     * 实际用肥量，单位：kg
     * 本次施肥的实际用量（仅施肥类型有效）
     */
    @Column(name = "fertilizer_amount", precision = 8, scale = 2)
    private java.math.BigDecimal fertilizerAmount = new java.math.BigDecimal("0.00");

    /**
     * 肥料类型
     * 记录使用的肥料种类，如：尿素、复合肥、水溶肥等
     */
    @Column(name = "fertilizer_type", length = 50)
    private String fertilizerType;

    /**
     * 平均EC值，单位：mS/cm
     * 灌溉期间土壤电导率的平均值
     */
    @Column(name = "average_ec", precision = 5, scale = 2)
    private java.math.BigDecimal averageEc;

    /**
     * 平均pH值
     * 灌溉期间土壤酸碱度的平均值
     */
    @Column(name = "average_ph", precision = 5, scale = 2)
    private java.math.BigDecimal averagePh;

    /**
     * 记录状态
     * running：灌溉进行中
     * completed：已正常完成
     * emergency_stopped：紧急停止
     * interrupted：异常中断
     * 默认值：completed
     */
    @Column(length = 20)
    private String status = "completed";

    /**
     * 记录创建时间，默认当前时间
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
