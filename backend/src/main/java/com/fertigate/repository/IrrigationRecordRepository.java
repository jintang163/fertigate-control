package com.fertigate.repository;

import com.fertigate.entity.IrrigationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IrrigationRecordRepository extends JpaRepository<IrrigationRecord, UUID> {
    
    @Query("SELECT r FROM IrrigationRecord r WHERE r.zone.id = :zoneId ORDER BY r.startTime DESC")
    List<IrrigationRecord> findByZoneId(UUID zoneId);
    
    @Query("SELECT r FROM IrrigationRecord r WHERE r.valve.id = :valveId AND r.endTime IS NULL ORDER BY r.startTime DESC")
    Optional<IrrigationRecord> findActiveRecordByValveId(UUID valveId);
    
    @Query("SELECT r FROM IrrigationRecord r WHERE r.startTime BETWEEN :start AND :end ORDER BY r.startTime DESC")
    List<IrrigationRecord> findByTimeRange(LocalDateTime start, LocalDateTime end);
}
