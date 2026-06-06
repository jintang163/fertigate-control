package com.fertigate.repository;

import com.fertigate.entity.ThresholdStrategy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ThresholdStrategyRepository extends JpaRepository<ThresholdStrategy, UUID> {

    List<ThresholdStrategy> findByIsActiveTrue();

    @Query("SELECT ts FROM ThresholdStrategy ts WHERE ts.zone.id = :zoneId AND ts.isActive = true ORDER BY ts.priority DESC")
    List<ThresholdStrategy> findActiveByZoneId(UUID zoneId);

    @Query("SELECT ts FROM ThresholdStrategy ts WHERE ts.crop.id = :cropId AND ts.isActive = true ORDER BY ts.priority DESC")
    List<ThresholdStrategy> findActiveByCropId(UUID cropId);

    @Query("SELECT ts FROM ThresholdStrategy ts WHERE ts.zone.id = :zoneId AND ts.crop.id = :cropId AND ts.isActive = true ORDER BY ts.priority DESC")
    Optional<ThresholdStrategy> findActiveByZoneIdAndCropId(UUID zoneId, UUID cropId);
}
