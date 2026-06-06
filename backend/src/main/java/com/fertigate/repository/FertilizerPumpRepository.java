package com.fertigate.repository;

import com.fertigate.entity.FertilizerPump;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FertilizerPumpRepository extends JpaRepository<FertilizerPump, UUID> {

    @Query("SELECT fp FROM FertilizerPump fp WHERE fp.device.deviceCode = :deviceCode")
    Optional<FertilizerPump> findByDeviceCode(String deviceCode);

    @Query("SELECT fp FROM FertilizerPump fp WHERE fp.zone.id = :zoneId")
    List<FertilizerPump> findByZoneId(UUID zoneId);

    @Query("SELECT fp FROM FertilizerPump fp WHERE fp.zone.id = :zoneId AND fp.autoControl = true")
    List<FertilizerPump> findAutoControlledPumpsByZoneId(UUID zoneId);

    List<FertilizerPump> findByIsRunningTrue();
}
