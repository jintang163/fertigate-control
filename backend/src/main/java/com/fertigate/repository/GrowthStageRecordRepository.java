package com.fertigate.repository;

import com.fertigate.entity.GrowthStageRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GrowthStageRecordRepository extends JpaRepository<GrowthStageRecord, UUID> {

    @Query("SELECT gsr FROM GrowthStageRecord gsr WHERE gsr.crop.id = :cropId ORDER BY gsr.startDate DESC")
    List<GrowthStageRecord> findByCropId(UUID cropId);

    @Query("SELECT gsr FROM GrowthStageRecord gsr WHERE gsr.zone.id = :zoneId ORDER BY gsr.startDate DESC")
    List<GrowthStageRecord> findByZoneId(UUID zoneId);

    @Query("SELECT gsr FROM GrowthStageRecord gsr WHERE gsr.crop.id = :cropId AND gsr.endDate IS NULL ORDER BY gsr.startDate DESC")
    Optional<GrowthStageRecord> findCurrentByCropId(UUID cropId);

    @Query("SELECT gsr FROM GrowthStageRecord gsr WHERE gsr.zone.id = :zoneId AND gsr.endDate IS NULL ORDER BY gsr.startDate DESC")
    Optional<GrowthStageRecord> findCurrentByZoneId(UUID zoneId);
}
