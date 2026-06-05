package com.fertigate.controller;

import com.fertigate.entity.Alert;
import com.fertigate.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/alert")
@RequiredArgsConstructor
public class AlertController {

    private final AlertRepository alertRepository;

    @GetMapping
    public ResponseEntity<List<Alert>> getAllAlerts() {
        return ResponseEntity.ok(alertRepository.findAll());
    }

    @GetMapping("/unacknowledged")
    public ResponseEntity<List<Alert>> getUnacknowledgedAlerts() {
        return ResponseEntity.ok(alertRepository.findByIsAcknowledgedFalseOrderByCreatedAtDesc());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Alert> getAlertById(@PathVariable UUID id) {
        return alertRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/level/{level}")
    public ResponseEntity<List<Alert>> getAlertsByLevel(@PathVariable String level) {
        return ResponseEntity.ok(alertRepository.findByLevel(level));
    }

    @PutMapping("/{id}/acknowledge")
    public ResponseEntity<Alert> acknowledgeAlert(@PathVariable UUID id) {
        return alertRepository.findById(id)
                .map(alert -> {
                    alert.setIsAcknowledged(true);
                    alert.setAcknowledgedAt(LocalDateTime.now());
                    Alert updated = alertRepository.save(alert);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/acknowledge-all")
    public ResponseEntity<String> acknowledgeAllAlerts() {
        List<Alert> alerts = alertRepository.findByIsAcknowledgedFalseOrderByCreatedAtDesc();
        for (Alert alert : alerts) {
            alert.setIsAcknowledged(true);
            alert.setAcknowledgedAt(LocalDateTime.now());
            alertRepository.save(alert);
        }
        return ResponseEntity.ok("Acknowledged " + alerts.size() + " alerts");
    }
}
