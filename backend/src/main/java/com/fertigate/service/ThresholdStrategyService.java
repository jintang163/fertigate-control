package com.fertigate.service;

import com.fertigate.dto.ThresholdStrategyDTO;
import com.fertigate.entity.Crop;
import com.fertigate.entity.ThresholdStrategy;
import com.fertigate.entity.Zone;
import com.fertigate.repository.CropRepository;
import com.fertigate.repository.ThresholdStrategyRepository;
import com.fertigate.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThresholdStrategyService {

    private final ThresholdStrategyRepository thresholdStrategyRepository;
    private final ZoneRepository zoneRepository;
    private final CropRepository cropRepository;

    public List<ThresholdStrategyDTO> getAllStrategies() {
        return thresholdStrategyRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<ThresholdStrategyDTO> getActiveStrategies() {
        return thresholdStrategyRepository.findByIsActiveTrue().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<ThresholdStrategyDTO> getStrategiesByZoneId(UUID zoneId) {
        return thresholdStrategyRepository.findActiveByZoneId(zoneId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<ThresholdStrategyDTO> getStrategiesByCropId(UUID cropId) {
        return thresholdStrategyRepository.findActiveByCropId(cropId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public Optional<ThresholdStrategyDTO> getStrategyByZoneAndCrop(UUID zoneId, UUID cropId) {
        return thresholdStrategyRepository.findActiveByZoneIdAndCropId(zoneId, cropId)
                .map(this::convertToDTO);
    }

    public Optional<ThresholdStrategyDTO> getStrategyById(UUID id) {
        return thresholdStrategyRepository.findById(id)
                .map(this::convertToDTO);
    }

    @Transactional
    public ThresholdStrategyDTO createStrategy(ThresholdStrategyDTO dto) {
        ThresholdStrategy strategy = new ThresholdStrategy();
        populateStrategyFromDTO(strategy, dto);
        
        ThresholdStrategy saved = thresholdStrategyRepository.save(strategy);
        log.info("Created threshold strategy: {} with id {}", saved.getName(), saved.getId());
        
        return convertToDTO(saved);
    }

    @Transactional
    public Optional<ThresholdStrategyDTO> updateStrategy(UUID id, ThresholdStrategyDTO dto) {
        return thresholdStrategyRepository.findById(id)
                .map(strategy -> {
                    populateStrategyFromDTO(strategy, dto);
                    return convertToDTO(thresholdStrategyRepository.save(strategy));
                });
    }

    @Transactional
    public Optional<ThresholdStrategyDTO> setStrategyActive(UUID id, boolean active) {
        return thresholdStrategyRepository.findById(id)
                .map(strategy -> {
                    strategy.setIsActive(active);
                    log.info("Strategy {} set to {}", strategy.getName(), active ? "active" : "inactive");
                    return convertToDTO(thresholdStrategyRepository.save(strategy));
                });
    }

    @Transactional
    public boolean deleteStrategy(UUID id) {
        if (thresholdStrategyRepository.existsById(id)) {
            thresholdStrategyRepository.deleteById(id);
            log.info("Deleted threshold strategy: {}", id);
            return true;
        }
        return false;
    }

    public boolean checkThresholds(UUID zoneId, Map<String, Double> sensorData) {
        List<ThresholdStrategy> strategies = thresholdStrategyRepository.findActiveByZoneId(zoneId);
        if (strategies.isEmpty()) {
            return true;
        }
        
        ThresholdStrategy strategy = strategies.get(0);
        
        Double humidity = sensorData.get("humidity");
        if (humidity != null) {
            if (humidity < strategy.getMinHumidity().doubleValue() || 
                humidity > strategy.getMaxHumidity().doubleValue()) {
                return false;
            }
        }
        
        Double ec = sensorData.get("ec");
        if (ec != null && strategy.getMinEc() != null && strategy.getMaxEc() != null) {
            if (ec < strategy.getMinEc().doubleValue() || ec > strategy.getMaxEc().doubleValue()) {
                return false;
            }
        }
        
        Double ph = sensorData.get("ph");
        if (ph != null && strategy.getMinPh() != null && strategy.getMaxPh() != null) {
            if (ph < strategy.getMinPh().doubleValue() || ph > strategy.getMaxPh().doubleValue()) {
                return false;
            }
        }
        
        if (Boolean.TRUE.equals(strategy.getWeatherLinkEnabled())) {
            Double temperature = sensorData.get("temperature");
            if (temperature != null && strategy.getMinTemperature() != null && strategy.getMaxTemperature() != null) {
                if (temperature < strategy.getMinTemperature().doubleValue() || 
                    temperature > strategy.getMaxTemperature().doubleValue()) {
                    return false;
                }
            }
            
            Double windSpeed = sensorData.get("windSpeed");
            if (windSpeed != null && strategy.getMaxWindSpeed() != null) {
                if (windSpeed > strategy.getMaxWindSpeed().doubleValue()) {
                    return false;
                }
            }
            
            Double rainfall = sensorData.get("rainfall");
            if (rainfall != null && strategy.getMinRainfall() != null) {
                if (rainfall < strategy.getMinRainfall().doubleValue()) {
                    return false;
                }
            }
        }
        
        return true;
    }

    public boolean shouldIrrigate(UUID zoneId, Map<String, Double> sensorData) {
        List<ThresholdStrategy> strategies = thresholdStrategyRepository.findActiveByZoneId(zoneId);
        if (strategies.isEmpty()) {
            return false;
        }
        
        ThresholdStrategy strategy = strategies.get(0);
        
        Double humidity = sensorData.get("humidity");
        if (humidity != null && humidity < strategy.getMinHumidity().doubleValue()) {
            if (Boolean.TRUE.equals(strategy.getAvoidRainIrrigation())) {
                Double rainfall = sensorData.get("rainfall");
                if (rainfall != null && rainfall > 0) {
                    log.debug("Skipping irrigation due to rainfall: {}mm", rainfall);
                    return false;
                }
            }
            
            if (Boolean.TRUE.equals(strategy.getHighTempIrrigation())) {
                Double temperature = sensorData.get("temperature");
                if (temperature != null && strategy.getMaxTemperature() != null && 
                    temperature > strategy.getMaxTemperature().doubleValue()) {
                    log.debug("Skipping irrigation due to high temperature: {}°C", temperature);
                    return false;
                }
            }
            
            return true;
        }
        
        return false;
    }

    public boolean shouldStopIrrigation(UUID zoneId, Map<String, Double> sensorData) {
        List<ThresholdStrategy> strategies = thresholdStrategyRepository.findActiveByZoneId(zoneId);
        if (strategies.isEmpty()) {
            return true;
        }
        
        ThresholdStrategy strategy = strategies.get(0);
        
        Double humidity = sensorData.get("humidity");
        if (humidity != null && humidity >= strategy.getMaxHumidity().doubleValue()) {
            return true;
        }
        
        Double ec = sensorData.get("ec");
        if (ec != null && strategy.getMaxEc() != null && ec > strategy.getMaxEc().doubleValue()) {
            return true;
        }
        
        return false;
    }

    public BigDecimal getOptimalEc(UUID zoneId) {
        List<ThresholdStrategy> strategies = thresholdStrategyRepository.findActiveByZoneId(zoneId);
        if (!strategies.isEmpty() && strategies.get(0).getMinEc() != null && strategies.get(0).getMaxEc() != null) {
            return strategies.get(0).getMinEc().add(strategies.get(0).getMaxEc())
                    .divide(new BigDecimal("2"), 2, BigDecimal.ROUND_HALF_UP);
        }
        return new BigDecimal("1.80");
    }

    public BigDecimal getOptimalPh(UUID zoneId) {
        List<ThresholdStrategy> strategies = thresholdStrategyRepository.findActiveByZoneId(zoneId);
        if (!strategies.isEmpty() && strategies.get(0).getMinPh() != null && strategies.get(0).getMaxPh() != null) {
            return strategies.get(0).getMinPh().add(strategies.get(0).getMaxPh())
                    .divide(new BigDecimal("2"), 2, BigDecimal.ROUND_HALF_UP);
        }
        return new BigDecimal("6.50");
    }

    private void populateStrategyFromDTO(ThresholdStrategy strategy, ThresholdStrategyDTO dto) {
        strategy.setName(dto.getName());
        strategy.setDescription(dto.getDescription());
        
        if (dto.getZoneId() != null) {
            zoneRepository.findById(dto.getZoneId()).ifPresent(strategy::setZone);
        }
        if (dto.getCropId() != null) {
            cropRepository.findById(dto.getCropId()).ifPresent(strategy::setCrop);
        }
        
        if (dto.getMinHumidity() != null) strategy.setMinHumidity(dto.getMinHumidity());
        if (dto.getMaxHumidity() != null) strategy.setMaxHumidity(dto.getMaxHumidity());
        if (dto.getMinEc() != null) strategy.setMinEc(dto.getMinEc());
        if (dto.getMaxEc() != null) strategy.setMaxEc(dto.getMaxEc());
        if (dto.getMinPh() != null) strategy.setMinPh(dto.getMinPh());
        if (dto.getMaxPh() != null) strategy.setMaxPh(dto.getMaxPh());
        if (dto.getMinTemperature() != null) strategy.setMinTemperature(dto.getMinTemperature());
        if (dto.getMaxTemperature() != null) strategy.setMaxTemperature(dto.getMaxTemperature());
        if (dto.getMaxWindSpeed() != null) strategy.setMaxWindSpeed(dto.getMaxWindSpeed());
        if (dto.getMinRainfall() != null) strategy.setMinRainfall(dto.getMinRainfall());
        
        if (dto.getWeatherLinkEnabled() != null) strategy.setWeatherLinkEnabled(dto.getWeatherLinkEnabled());
        if (dto.getAvoidRainIrrigation() != null) strategy.setAvoidRainIrrigation(dto.getAvoidRainIrrigation());
        if (dto.getHighTempIrrigation() != null) strategy.setHighTempIrrigation(dto.getHighTempIrrigation());
        if (dto.getIsActive() != null) strategy.setIsActive(dto.getIsActive());
        if (dto.getPriority() != null) strategy.setPriority(dto.getPriority());
    }

    private ThresholdStrategyDTO convertToDTO(ThresholdStrategy strategy) {
        ThresholdStrategyDTO dto = new ThresholdStrategyDTO();
        dto.setId(strategy.getId());
        dto.setName(strategy.getName());
        dto.setDescription(strategy.getDescription());
        dto.setMinHumidity(strategy.getMinHumidity());
        dto.setMaxHumidity(strategy.getMaxHumidity());
        dto.setMinEc(strategy.getMinEc());
        dto.setMaxEc(strategy.getMaxEc());
        dto.setMinPh(strategy.getMinPh());
        dto.setMaxPh(strategy.getMaxPh());
        dto.setMinTemperature(strategy.getMinTemperature());
        dto.setMaxTemperature(strategy.getMaxTemperature());
        dto.setMaxWindSpeed(strategy.getMaxWindSpeed());
        dto.setMinRainfall(strategy.getMinRainfall());
        dto.setWeatherLinkEnabled(strategy.getWeatherLinkEnabled());
        dto.setAvoidRainIrrigation(strategy.getAvoidRainIrrigation());
        dto.setHighTempIrrigation(strategy.getHighTempIrrigation());
        dto.setIsActive(strategy.getIsActive());
        dto.setPriority(strategy.getPriority());
        dto.setCreatedAt(strategy.getCreatedAt());
        dto.setUpdatedAt(strategy.getUpdatedAt());
        
        if (strategy.getZone() != null) {
            dto.setZoneId(strategy.getZone().getId());
        }
        if (strategy.getCrop() != null) {
            dto.setCropId(strategy.getCrop().getId());
        }
        
        return dto;
    }
}
