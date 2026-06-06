package com.fertigate.repository;

import com.fertigate.entity.RotationSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface RotationScheduleRepository extends JpaRepository<RotationSchedule, UUID> {

    List<RotationSchedule> findByIsActiveTrue();

    @Query("SELECT rs FROM RotationSchedule rs WHERE rs.isActive = true AND rs.nextExecution <= :now ORDER BY rs.priority DESC, rs.nextExecution ASC")
    List<RotationSchedule> findScheduledToExecute(LocalDateTime now);

    @Query("SELECT rs FROM RotationSchedule rs WHERE rs.strategy.id = :strategyId")
    List<RotationSchedule> findByStrategyId(UUID strategyId);

    @Query("SELECT rs FROM RotationSchedule rs WHERE rs.zoneIds LIKE CONCAT('%', :zoneId, '%') AND rs.isActive = true")
    List<RotationSchedule> findActiveByZoneId(UUID zoneId);
}
