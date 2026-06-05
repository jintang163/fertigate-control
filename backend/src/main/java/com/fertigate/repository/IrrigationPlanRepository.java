package com.fertigate.repository;

import com.fertigate.entity.IrrigationPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IrrigationPlanRepository extends JpaRepository<IrrigationPlan, UUID> {
    
    @Query("SELECT p FROM IrrigationPlan p WHERE p.zone.id = :zoneId AND p.isActive = true")
    List<IrrigationPlan> findActivePlansByZoneId(UUID zoneId);
    
    List<IrrigationPlan> findByIsActiveTrue();
}
