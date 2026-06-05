package com.fertigate.repository;

import com.fertigate.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceRepository extends JpaRepository<Device, UUID> {
    
    Optional<Device> findByDeviceCode(String deviceCode);
    
    List<Device> findByType(String type);
    
    @Query("SELECT d FROM Device d WHERE d.zone.id = :zoneId")
    List<Device> findByZoneId(UUID zoneId);
    
    boolean existsByDeviceCode(String deviceCode);
}
