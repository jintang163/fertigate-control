package com.fertigate.controller;

import com.fertigate.entity.Zone;
import com.fertigate.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/zone")
@RequiredArgsConstructor
public class ZoneController {

    private final ZoneRepository zoneRepository;

    @GetMapping
    public ResponseEntity<List<Zone>> getAllZones() {
        return ResponseEntity.ok(zoneRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Zone> getZoneById(@PathVariable UUID id) {
        return zoneRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Zone> createZone(@RequestBody Zone zone) {
        Zone saved = zoneRepository.save(zone);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Zone> updateZone(@PathVariable UUID id, @RequestBody Zone zone) {
        return zoneRepository.findById(id)
                .map(existing -> {
                    zone.setId(id);
                    Zone updated = zoneRepository.save(zone);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteZone(@PathVariable UUID id) {
        if (!zoneRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        zoneRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
