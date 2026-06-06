package com.fertigate.repository;

import com.fertigate.entity.Valve;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ValveRepository extends JpaRepository<Valve, UUID> {
    
    @Query("SELECT v FROM Valve v WHERE v.device.deviceCode = :deviceCode")
    Optional<Valve> findByDeviceCode(String deviceCode);
    
    @Query("SELECT v FROM Valve v WHERE v.zone.id = :zoneId")
    List<Valve> findByZoneId(UUID zoneId);
    
    @Query("SELECT v FROM Valve v WHERE v.zone.id = :zoneId AND v.autoControl = true")
    List<Valve> findAutoControlledValvesByZoneId(UUID zoneId);
    
    List<Valve> findByIsOpenTrue();

    @Query("SELECT v FROM Valve v WHERE v.zone.id = :zoneId AND v.isOpen = true")
    List<Valve> findOpenValvesByZoneId(UUID zoneId);
}
