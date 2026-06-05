package com.fertigate.repository;

import com.fertigate.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AlertRepository extends JpaRepository<Alert, UUID> {
    
    List<Alert> findByIsAcknowledgedFalseOrderByCreatedAtDesc();
    
    @Query("SELECT a FROM Alert a WHERE a.device.id = :deviceId ORDER BY a.createdAt DESC")
    List<Alert> findByDeviceId(UUID deviceId);
    
    List<Alert> findByLevel(String level);
}
