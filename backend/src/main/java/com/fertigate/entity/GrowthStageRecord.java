package com.fertigate.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 生育期记录实体类
 * 用于记录作物的生长发育阶段，包括播种、苗期、开花、结果、收获等阶段
 * 每个生育期可配置对应的环境阈值和水肥需求，为智能灌溉决策提供依据
 * 可关联到具体作物品种和灌区
 */
@Data
@Entity
@Table(name = "growth_stage_records")
public class GrowthStageRecord {

    /**
     * 记录唯一标识符，UUID自动生成
     */
    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 关联的作物品种
     * 用于按作物品种管理生育期配置
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crop_id")
    private Crop crop;

    /**
     * 关联的灌区
     * 用于按灌区管理生育期记录
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    /**
     * 生育期名称
     * 示例：播种期、苗期、分蘖期、拔节期、抽穗期、灌浆期、成熟期、收获期
     */
    @Column(name = "growth_stage", nullable = false, length = 50)
    private String growthStage;

    /**
     * 生育期开始日期
     * 记录该生育阶段的起始时间
     */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /**
     * 生育期结束日期
     * 记录该生育阶段的结束时间，null表示当前处于该生育期
     */
    @Column(name = "end_date")
    private LocalDate endDate;

    /**
     * 备注信息
     * 记录该生育期的特殊情况、管理措施等
     */
    @Column(columnDefinition = "TEXT")
    private String notes;

    /**
     * 最低土壤湿度要求，单位：%
     * 该生育期土壤湿度的下限阈值
     */
    @Column(name = "min_humidity", precision = 5, scale = 2)
    private BigDecimal minHumidity;

    /**
     * 最高土壤湿度要求，单位：%
     * 该生育期土壤湿度的上限阈值
     */
    @Column(name = "max_humidity", precision = 5, scale = 2)
    private BigDecimal maxHumidity;

    /**
     * 最优EC值，单位：mS/cm
     * 该生育期最适宜的土壤电导率
     */
    @Column(name = "optimal_ec", precision = 5, scale = 2)
    private BigDecimal optimalEc;

    /**
     * 最优pH值
     * 该生育期最适宜的土壤酸碱度
     */
    @Column(name = "optimal_ph", precision = 5, scale = 2)
    private BigDecimal optimalPh;

    /**
     * 日需水量，单位：m³/亩
     * 该生育期每亩每日的理论需水量
     */
    @Column(name = "water_requirement", precision = 8, scale = 2)
    private BigDecimal waterRequirement;

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
