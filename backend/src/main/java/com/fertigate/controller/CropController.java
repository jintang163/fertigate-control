package com.fertigate.controller;

import com.fertigate.entity.Crop;
import com.fertigate.repository.CropRepository;
import com.fertigate.service.CropGrowthModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/crop")
@RequiredArgsConstructor
public class CropController {

    private final CropRepository cropRepository;
    private final CropGrowthModelService cropGrowthModelService;

    @GetMapping
    public ResponseEntity<List<Crop>> getAllCrops() {
        return ResponseEntity.ok(cropRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Crop> getCropById(@PathVariable UUID id) {
        return cropRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/growth-info")
    public ResponseEntity<Map<String, Object>> getCropGrowthInfo(@PathVariable UUID id) {
        return cropRepository.findById(id)
                .map(crop -> ResponseEntity.ok(cropGrowthModelService.getCropGrowthInfo(crop)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Crop> createCrop(@RequestBody Crop crop) {
        Crop saved = cropRepository.save(crop);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Crop> updateCrop(@PathVariable UUID id, @RequestBody Crop crop) {
        return cropRepository.findById(id)
                .map(existing -> {
                    crop.setId(id);
                    Crop updated = cropRepository.save(crop);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCrop(@PathVariable UUID id) {
        if (!cropRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        cropRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
