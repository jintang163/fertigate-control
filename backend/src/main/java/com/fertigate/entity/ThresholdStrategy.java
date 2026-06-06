package com.fertigate.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 阈值策略实体类
 * 用于配置智能灌溉系统的阈值判断规则，包括土壤湿度、EC值、pH值等环境参数范围
 * 支持气象联动条件，如降雨时避免灌溉、高温时禁止灌溉等
 * 可关联到具体灌区和作物品种，实现精准化灌溉决策
 */
@Data
@Entity
@Table(name = "threshold_strategies")
public class ThresholdStrategy {

    /**
     * 策略唯一标识符，UUID自动生成
     */
    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 策略名称，便于识别和管理
     * 示例：小麦苗期灌溉策略、番茄结果期水肥策略
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * 策略描述，详细说明策略的适用场景和配置依据
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * 关联的灌区
     * 用于按灌区配置阈值策略
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    /**
     * 关联的作物品种
     * 用于按作物品种配置阈值策略
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crop_id")
    private Crop crop;

    /**
     * 最低土壤湿度阈值，单位：%
     * 低于此值时触发灌溉
     * 默认值：50%
     */
    @Column(name = "min_humidity", nullable = false, precision = 5, scale = 2)
    private BigDecimal minHumidity = new BigDecimal("50.00");

    /**
     * 最高土壤湿度阈值，单位：%
     * 高于此值时停止灌溉
     * 默认值：80%
     */
    @Column(name = "max_humidity", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxHumidity = new BigDecimal("80.00");

    /**
     * 最低EC值阈值，单位：mS/cm
     * 土壤电导率下限，低于此值可能需要施肥
     * 默认值：1.00
     */
    @Column(name = "min_ec", precision = 5, scale = 2)
    private BigDecimal minEc = new BigDecimal("1.00");

    /**
     * 最高EC值阈值，单位：mS/cm
     * 土壤电导率上限，高于此值停止施肥
     * 默认值：2.50
     */
    @Column(name = "max_ec", precision = 5, scale = 2)
    private BigDecimal maxEc = new BigDecimal("2.50");

    /**
     * 最低pH值阈值
     * 土壤酸碱度下限，低于此值需要调整
     * 默认值：5.50
     */
    @Column(name = "min_ph", precision = 5, scale = 2)
    private BigDecimal minPh = new BigDecimal("5.50");

    /**
     * 最高pH值阈值
     * 土壤酸碱度上限，高于此值需要调整
     * 默认值：7.50
     */
    @Column(name = "max_ph", precision = 5, scale = 2)
    private BigDecimal maxPh = new BigDecimal("7.50");

    /**
     * 最低温度阈值，单位：°C
     * 气象联动参数，低于此温度不建议灌溉
     * 默认值：10°C
     */
    @Column(name = "min_temperature", precision = 5, scale = 2)
    private BigDecimal minTemperature = new BigDecimal("10.00");

    /**
     * 最高温度阈值，单位：°C
     * 气象联动参数，高于此温度不建议灌溉
     * 默认值：35°C
     */
    @Column(name = "max_temperature", precision = 5, scale = 2)
    private BigDecimal maxTemperature = new BigDecimal("35.00");

    /**
     * 最大风速阈值，单位：m/s
     * 气象联动参数，风速过高时不建议灌溉（减少蒸发损失）
     * 默认值：15 m/s
     */
    @Column(name = "max_wind_speed", precision = 5, scale = 2)
    private BigDecimal maxWindSpeed = new BigDecimal("15.00");

    /**
     * 最小降雨量阈值，单位：mm
     * 气象联动参数，降雨量低于此值才考虑灌溉
     * 默认值：0 mm
     */
    @Column(name = "min_rainfall", precision = 5, scale = 2)
    private BigDecimal minRainfall = new BigDecimal("0.00");

    /**
     * 是否启用气象联动
     * true：根据气象数据调整灌溉决策
     * false：仅根据土壤数据决策
     * 默认值：false
     */
    @Column(name = "weather_link_enabled")
    private Boolean weatherLinkEnabled = false;

    /**
     * 是否避免雨天灌溉
     * true：有降雨时不执行灌溉
     * 默认值：true
     */
    @Column(name = "avoid_rain_irrigation")
    private Boolean avoidRainIrrigation = true;

    /**
     * 是否避免高温灌溉
     * true：高温时不执行灌溉
     * 默认值：false
     */
    @Column(name = "high_temp_irrigation")
    private Boolean highTempIrrigation = false;

    /**
     * 策略是否启用
     * true：策略生效中，false：策略已停用
     * 默认值：true
     */
    @Column(name = "is_active")
    private Boolean isActive = true;

    /**
     * 策略优先级，数值越大优先级越高
     * 同一灌区多个策略时，优先级高的优先生效
     * 默认值：0
     */
    private Integer priority = 0;

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
