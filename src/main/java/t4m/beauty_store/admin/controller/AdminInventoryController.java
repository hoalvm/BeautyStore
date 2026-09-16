package t4m.beauty_store.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import t4m.beauty_store.admin.dto.InventoryBatchRequest;
import t4m.beauty_store.product.dto.InventoryBatchResponse;
import t4m.beauty_store.product.service.InventoryService;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminInventoryController {
    private final InventoryService inventoryService;

    @GetMapping("/variants/{variantId}/batches")
    public List<InventoryBatchResponse> list(@PathVariable Long variantId) {
        return inventoryService.getBatches(variantId).stream().map(InventoryBatchResponse::fromEntity).toList();
    }

    @PostMapping("/variants/{variantId}/batches")
    public InventoryBatchResponse create(
            @PathVariable Long variantId,
            @Valid @RequestBody InventoryBatchRequest request) {
        return InventoryBatchResponse.fromEntity(inventoryService.createBatch(variantId, request));
    }

    @PutMapping("/batches/{id}")
    public InventoryBatchResponse update(
            @PathVariable Long id,
            @Valid @RequestBody InventoryBatchRequest request) {
        return InventoryBatchResponse.fromEntity(inventoryService.updateBatch(id, request));
    }

    @DeleteMapping("/batches/{id}")
    public ResponseEntity<Void> archive(@PathVariable Long id) {
        inventoryService.setBatchActive(id, false);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/batches/{id}/visibility")
    public InventoryBatchResponse setVisibility(@PathVariable Long id, @RequestParam boolean active) {
        return InventoryBatchResponse.fromEntity(inventoryService.setBatchActive(id, active));
    }

    @GetMapping("/inventory/expired")
    public List<InventoryBatchResponse> expired() {
        return inventoryService.getExpiredBatches().stream().map(InventoryBatchResponse::fromEntity).toList();
    }

    @GetMapping("/inventory/expiring")
    public List<InventoryBatchResponse> expiring(@RequestParam(defaultValue = "90") int days) {
        return inventoryService.getExpiringBatches(days).stream().map(InventoryBatchResponse::fromEntity).toList();
    }
}
