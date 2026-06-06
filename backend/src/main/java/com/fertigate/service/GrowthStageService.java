package com.fertigate.service;

import com.fertigate.dto.GrowthStageRecordDTO;
import com.fertigate.entity.Crop;
import com.fertigate.entity.GrowthStageRecord;
import com.fertigate.entity.Zone;
import com.fertigate.repository.CropRepository;
import com.fertigate.repository.GrowthStageRecordRepository;
import com.fertigate.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrowthStageService {

    private final GrowthStageRecordRepository growthStageRecordRepository;
    private final CropRepository cropRepository;
    private final ZoneRepository zoneRepository;

    public List<GrowthStageRecordDTO> getAllRecords() {
        return growthStageRecordRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<GrowthStageRecordDTO> getRecordsByCropId(UUID cropId) {
        return growthStageRecordRepository.findByCropId(cropId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<GrowthStageRecordDTO> getRecordsByZoneId(UUID zoneId) {
        return growthStageRecordRepository.findByZoneId(zoneId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public Optional<GrowthStageRecordDTO> getCurrentRecordByCropId(UUID cropId) {
        return growthStageRecordRepository.findCurrentByCropId(cropId)
                .map(this::convertToDTO);
    }

    public Optional<GrowthStageRecordDTO> getCurrentRecordByZoneId(UUID zoneId) {
        return growthStageRecordRepository.findCurrentByZoneId(zoneId)
                .map(this::convertToDTO);
    }

    public Optional<GrowthStageRecordDTO> getRecordById(UUID id) {
        return growthStageRecordRepository.findById(id)
                .map(this::convertToDTO);
    }

    @Transactional
    public GrowthStageRecordDTO createRecord(GrowthStageRecordDTO dto) {
        GrowthStageRecord record = new GrowthStageRecord();
        
        if (dto.getCropId() != null) {
            cropRepository.findById(dto.getCropId()).ifPresent(record::setCrop);
        }
        if (dto.getZoneId() != null) {
            zoneRepository.findById(dto.getZoneId()).ifPresent(record::setZone);
        }
        
        record.setGrowthStage(dto.getGrowthStage());
        record.setStartDate(dto.getStartDate() != null ? dto.getStartDate() : LocalDate.now());
        record.setEndDate(dto.getEndDate());
        record.setNotes(dto.getNotes());
        record.setMinHumidity(dto.getMinHumidity());
        record.setMaxHumidity(dto.getMaxHumidity());
        record.setOptimalEc(dto.getOptimalEc());
        record.setOptimalPh(dto.getOptimalPh());
        record.setWaterRequirement(dto.getWaterRequirement());
        
        GrowthStageRecord saved = growthStageRecordRepository.save(record);
        log.info("Created growth stage record: {} for crop/zone {}", 
                saved.getGrowthStage(), saved.getId());
        
        if (dto.getCropId() != null && record.getCrop() != null) {
            endPreviousCropRecords(dto.getCropId(), saved.getId());
            updateCropGrowthStage(record.getCrop(), saved.getGrowthStage());
        }
        
        return convertToDTO(saved);
    }

    @Transactional
    public Optional<GrowthStageRecordDTO> updateRecord(UUID id, GrowthStageRecordDTO dto) {
        return growthStageRecordRepository.findById(id)
                .map(record -> {
                    if (dto.getCropId() != null) {
                        cropRepository.findById(dto.getCropId()).ifPresent(record::setCrop);
                    }
                    if (dto.getZoneId() != null) {
                        zoneRepository.findById(dto.getZoneId()).ifPresent(record::setZone);
                    }
                    
                    if (dto.getGrowthStage() != null) {
                        record.setGrowthStage(dto.getGrowthStage());
                        if (record.getCrop() != null) {
                            updateCropGrowthStage(record.getCrop(), dto.getGrowthStage());
                        }
                    }
                    if (dto.getStartDate() != null) {
                        record.setStartDate(dto.getStartDate());
                    }
                    record.setEndDate(dto.getEndDate());
                    record.setNotes(dto.getNotes());
                    
                    if (dto.getMinHumidity() != null) record.setMinHumidity(dto.getMinHumidity());
                    if (dto.getMaxHumidity() != null) record.setMaxHumidity(dto.getMaxHumidity());
                    if (dto.getOptimalEc() != null) record.setOptimalEc(dto.getOptimalEc());
                    if (dto.getOptimalPh() != null) record.setOptimalPh(dto.getOptimalPh());
                    if (dto.getWaterRequirement() != null) record.setWaterRequirement(dto.getWaterRequirement());
                    
                    return convertToDTO(growthStageRecordRepository.save(record));
                });
    }

    @Transactional
    public boolean deleteRecord(UUID id) {
        if (growthStageRecordRepository.existsById(id)) {
            growthStageRecordRepository.deleteById(id);
            log.info("Deleted growth stage record: {}", id);
            return true;
        }
        return false;
    }

    @Transactional
    public Optional<GrowthStageRecordDTO> endCurrentRecord(UUID cropId, LocalDate endDate) {
        return growthStageRecordRepository.findCurrentByCropId(cropId)
                .map(record -> {
                    record.setEndDate(endDate != null ? endDate : LocalDate.now());
                    return convertToDTO(growthStageRecordRepository.save(record));
                });
    }

    private void endPreviousCropRecords(UUID cropId, UUID excludeId) {
        growthStageRecordRepository.findByCropId(cropId).stream()
                .filter(r -> !r.getId().equals(excludeId) && r.getEndDate() == null)
                .forEach(r -> {
                    r.setEndDate(LocalDate.now().minusDays(1));
                    growthStageRecordRepository.save(r);
                });
    }

    private void updateCropGrowthStage(Crop crop, String growthStage) {
        if (crop != null) {
            crop.setGrowthStage(growthStage);
            cropRepository.save(crop);
        }
    }

    private GrowthStageRecordDTO convertToDTO(GrowthStageRecord record) {
        GrowthStageRecordDTO dto = new GrowthStageRecordDTO();
        dto.setId(record.getId());
        dto.setGrowthStage(record.getGrowthStage());
        dto.setStartDate(record.getStartDate());
        dto.setEndDate(record.getEndDate());
        dto.setNotes(record.getNotes());
        dto.setMinHumidity(record.getMinHumidity());
        dto.setMaxHumidity(record.getMaxHumidity());
        dto.setOptimalEc(record.getOptimalEc());
        dto.setOptimalPh(record.getOptimalPh());
        dto.setWaterRequirement(record.getWaterRequirement());
        dto.setCreatedAt(record.getCreatedAt());
        dto.setUpdatedAt(record.getUpdatedAt());
        
        if (record.getCrop() != null) {
            dto.setCropId(record.getCrop().getId());
        }
        if (record.getZone() != null) {
            dto.setZoneId(record.getZone().getId());
        }
        
        return dto;
    }
}
