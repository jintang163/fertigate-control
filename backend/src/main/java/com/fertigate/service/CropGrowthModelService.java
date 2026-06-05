package com.fertigate.service;

import com.fertigate.dto.IrrigationDecision;
import com.fertigate.entity.Crop;
import com.fertigate.entity.Zone;
import com.fertigate.repository.CropRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CropGrowthModelService {

    private final CropRepository cropRepository;

    @Value("${control.default-irrigation-duration:1800}")
    private int defaultIrrigationDuration;

    public IrrigationDecision analyzeIrrigationNeed(Zone zone, Map<String, Double> latestSensorData) {
        IrrigationDecision decision = new IrrigationDecision();
        decision.setZoneId(zone.getId());
        decision.setZoneName(zone.getName());
        
        if (zone.getCrop() == null) {
            decision.setNeedIrrigation(false);
            decision.setReason("该区域未关联作物");
            return decision;
        }

        Crop crop = zone.getCrop();
        decision.setCropId(crop.getId());
        decision.setMinHumidity(crop.getMinHumidity().doubleValue());
        decision.setMaxHumidity(crop.getMaxHumidity().doubleValue());

        Double currentHumidity = latestSensorData.get("humidity");
        if (currentHumidity == null) {
            decision.setNeedIrrigation(false);
            decision.setReason("无可用的土壤湿度数据");
            decision.setCurrentHumidity(null);
            return decision;
        }

        decision.setCurrentHumidity(currentHumidity);

        double growthStageFactor = getGrowthStageFactor(crop);
        double weatherFactor = getWeatherFactor(latestSensorData);
        
        double adjustedMinHumidity = crop.getMinHumidity().doubleValue() * (1 + (1 - growthStageFactor) * 0.2);
        double adjustedMaxHumidity = crop.getMaxHumidity().doubleValue() * (0.8 + growthStageFactor * 0.2);

        double waterDeficit = adjustedMinHumidity - currentHumidity;
        
        if (currentHumidity < adjustedMinHumidity) {
            decision.setNeedIrrigation(true);
            
            int duration = calculateIrrigationDuration(
                waterDeficit, 
                crop.getWaterRequirement().doubleValue(),
                zone.getArea() != null ? zone.getArea().doubleValue() : 100,
                weatherFactor
            );
            
            decision.setDurationSeconds(duration);
            decision.setWaterAmount(calculateWaterAmount(duration, zone));
            
            StringBuilder reason = new StringBuilder();
            reason.append(String.format("土壤湿度(%.1f%%)低于阈值(%.1f%%)，缺水率: %.1f%%",
                currentHumidity, adjustedMinHumidity, waterDeficit));
            reason.append(String.format("。生长阶段系数: %.2f，天气系数: %.2f", growthStageFactor, weatherFactor));
            
            decision.setReason(reason.toString());
            
        } else if (currentHumidity > adjustedMaxHumidity) {
            decision.setNeedIrrigation(false);
            decision.setReason(String.format("土壤湿度(%.1f%%)高于上限(%.1f%%)，无需灌溉",
                currentHumidity, adjustedMaxHumidity));
        } else {
            decision.setNeedIrrigation(false);
            decision.setReason(String.format("土壤湿度(%.1f%%)在适宜范围内(%.1f%%-%.1f%%)",
                currentHumidity, adjustedMinHumidity, adjustedMaxHumidity));
        }

        return decision;
    }

    private double getGrowthStageFactor(Crop crop) {
        String growthStage = crop.getGrowthStage();
        
        return switch (growthStage) {
            case "seedling" -> 0.6;
            case "vegetative" -> 0.8;
            case "flowering" -> 1.0;
            case "fruiting" -> 0.9;
            case "ripening" -> 0.5;
            case "dormant" -> 0.3;
            default -> 0.7;
        };
    }

    private double getWeatherFactor(Map<String, Double> sensorData) {
        double factor = 1.0;
        
        Double airTemp = sensorData.get("air_temperature");
        if (airTemp != null) {
            if (airTemp > 30) {
                factor += 0.2;
            } else if (airTemp < 15) {
                factor -= 0.1;
            }
        }
        
        Double rainfall = sensorData.get("rainfall");
        if (rainfall != null && rainfall > 5) {
            factor -= 0.3;
        }
        
        Double light = sensorData.get("light");
        if (light != null && light > 50000) {
            factor += 0.1;
        }
        
        Double windSpeed = sensorData.get("wind_speed");
        if (windSpeed != null && windSpeed > 5) {
            factor += 0.1;
        }
        
        return Math.max(0.3, Math.min(1.5, factor));
    }

    private int calculateIrrigationDuration(double waterDeficit, double waterRequirement, 
                                            double area, double weatherFactor) {
        double baseDuration = waterDeficit * 30;
        double areaFactor = area / 100;
        double duration = baseDuration * areaFactor * weatherFactor + (waterRequirement * 10);
        
        return (int) Math.min(Math.max(duration, 300), defaultIrrigationDuration * 2);
    }

    private double calculateWaterAmount(int durationSeconds, Zone zone) {
        double flowRate = 10;
        double area = zone.getArea() != null ? zone.getArea().doubleValue() : 100;
        
        double waterAmount = (durationSeconds / 3600.0) * flowRate * (area / 100);
        
        return BigDecimal.valueOf(waterAmount)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public int calculateDaysSincePlanting(Crop crop) {
        if (crop.getPlantingDate() == null) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(crop.getPlantingDate(), LocalDate.now());
    }

    public int calculateDaysToHarvest(Crop crop) {
        if (crop.getExpectedHarvestDate() == null) {
            return -1;
        }
        return (int) ChronoUnit.DAYS.between(LocalDate.now(), crop.getExpectedHarvestDate());
    }

    public Map<String, Object> getCropGrowthInfo(Crop crop) {
        return Map.of(
            "cropId", crop.getId(),
            "cropName", crop.getName(),
            "variety", crop.getVariety(),
            "growthStage", crop.getGrowthStage(),
            "daysSincePlanting", calculateDaysSincePlanting(crop),
            "daysToHarvest", calculateDaysToHarvest(crop),
            "minHumidity", crop.getMinHumidity(),
            "maxHumidity", crop.getMaxHumidity(),
            "optimalEc", crop.getOptimalEc(),
            "optimalPh", crop.getOptimalPh(),
            "waterRequirement", crop.getWaterRequirement(),
            "growthStageFactor", getGrowthStageFactor(crop)
        );
    }
}
