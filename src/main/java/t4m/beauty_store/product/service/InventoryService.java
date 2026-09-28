package t4m.beauty_store.product.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import t4m.beauty_store.admin.dto.InventoryBatchRequest;
import t4m.beauty_store.config.StoreTime;
import t4m.beauty_store.config.ApiException;
import t4m.beauty_store.product.entity.InventoryBatch;
import t4m.beauty_store.product.entity.ProductVariant;
import t4m.beauty_store.product.repository.InventoryBatchRepository;
import t4m.beauty_store.product.repository.ProductVariantRepository;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private final InventoryBatchRepository batchRepository;
    private final ProductVariantRepository variantRepository;

    public record BatchReservation(Long batchId, String batchCode, int quantity) {
    }

    public int getAvailableStock(Long variantId) {
        requireVariant(variantId);
        return batchRepository.getAvailableStock(variantId, StoreTime.today());
    }

    public List<InventoryBatch> getBatches(Long variantId) {
        requireVariant(variantId);
        return batchRepository.findByVariantIdOrderByExpiryDateAscIdAsc(variantId);
    }

    public List<InventoryBatch> getExpiredBatches() {
        return batchRepository.findExpired(StoreTime.today());
    }

    public List<InventoryBatch> getExpiringBatches(int days) {
        if (days < 0 || days > 3650) throw new IllegalArgumentException("Days must be between 0 and 3650");
        LocalDate today = StoreTime.today();
        return batchRepository.findExpiringBetween(today, today.plusDays(days));
    }

    @Transactional
    public InventoryBatch createBatch(Long variantId, InventoryBatchRequest request) {
        ProductVariant variant = requireVariant(variantId);
        validateRequest(request, true);
        String code = normalizeCode(request.getBatchCode());
        if (batchRepository.existsByVariantIdAndBatchCodeIgnoreCase(variantId, code)) {
            throw new IllegalArgumentException("Batch code already exists for this variant");
        }
        InventoryBatch batch = InventoryBatch.builder()
            .variant(variant)
            .batchCode(code)
            .manufacturedDate(request.getManufacturedDate())
            .expiryDate(request.getExpiryDate())
            .quantityOnHand(request.getQuantityOnHand())
            .quantityReserved(0)
            .active(request.getActive() == null || request.getActive())
            .build();
        InventoryBatch saved = batchRepository.save(batch);
        variant.getBatches().add(saved);
        return saved;
    }

    @Transactional
    public InventoryBatch updateBatch(Long id, InventoryBatchRequest request) {
        InventoryBatch batch = batchRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new IllegalArgumentException("Inventory batch not found"));
        validateRequest(request, false);
        String code = normalizeCode(request.getBatchCode());
        if (batchRepository.existsByVariantIdAndBatchCodeIgnoreCaseAndIdNot(
                batch.getVariant().getId(), code, id)) {
            throw new IllegalArgumentException("Batch code already exists for this variant");
        }
        int reserved = batch.getQuantityReserved();
        if (reserved > request.getQuantityOnHand()) {
            throw new IllegalArgumentException("Reserved quantity cannot exceed on-hand quantity");
        }
        boolean requestedActive = request.getActive() == null ? Boolean.TRUE.equals(batch.getActive()) : request.getActive();
        if (reserved > 0 && (!code.equalsIgnoreCase(batch.getBatchCode())
                || !Objects.equals(request.getExpiryDate(), batch.getExpiryDate())
                || requestedActive != Boolean.TRUE.equals(batch.getActive())
                || request.getQuantityOnHand() < batch.getQuantityOnHand())) {
            throw ApiException.conflict("Không thể thay đổi thông tin lô đang giữ tồn cho đơn hàng");
        }
        if (requestedActive && request.getExpiryDate().isBefore(StoreTime.today())) {
            throw new IllegalArgumentException("Không thể kích hoạt lô đã hết hạn");
        }
        batch.setBatchCode(code);
        batch.setManufacturedDate(request.getManufacturedDate());
        batch.setExpiryDate(request.getExpiryDate());
        batch.setQuantityOnHand(request.getQuantityOnHand());
        batch.setQuantityReserved(reserved);
        if (request.getActive() != null) batch.setActive(request.getActive());
        return batchRepository.save(batch);
    }

    @Transactional
    public InventoryBatch setBatchActive(Long id, boolean active) {
        InventoryBatch batch = batchRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new IllegalArgumentException("Inventory batch not found"));
        if (Boolean.TRUE.equals(batch.getActive()) == active) return batch;
        if (batch.getQuantityReserved() > 0) {
            throw ApiException.conflict("Không thể đổi trạng thái lô đang giữ tồn cho đơn hàng");
        }
        if (active && (batch.getExpiryDate() == null || batch.getExpiryDate().isBefore(StoreTime.today()))) {
            throw new IllegalArgumentException("Không thể kích hoạt lô đã hết hạn");
        }
        batch.setActive(active);
        return batchRepository.save(batch);
    }

    @Transactional
    public List<BatchReservation> reserveFefo(Long variantId, int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Reservation quantity must be greater than 0");
        variantRepository.findByIdAndActiveTrueAndProductActiveTrue(variantId)
            .orElseThrow(() -> new IllegalArgumentException("Sellable product variant not found"));
        List<InventoryBatch> batches = batchRepository.findSellableFefoForUpdate(variantId, StoreTime.today());
        int available = batches.stream().mapToInt(InventoryBatch::getAvailableQuantity).sum();
        if (available < quantity) {
            throw new IllegalArgumentException("Insufficient stock for product variant");
        }
        int remaining = quantity;
        List<BatchReservation> reservations = new ArrayList<>();
        for (InventoryBatch batch : batches) {
            if (remaining == 0) break;
            int allocated = Math.min(remaining, batch.getAvailableQuantity());
            batch.setQuantityReserved(batch.getQuantityReserved() + allocated);
            reservations.add(new BatchReservation(batch.getId(), batch.getBatchCode(), allocated));
            remaining -= allocated;
        }
        batchRepository.saveAll(batches);
        return List.copyOf(reservations);
    }

    @Transactional
    public void releaseReservations(Map<Long, Integer> allocations) {
        mutateReservations(allocations, false);
    }

    @Transactional
    public void commitReservations(Map<Long, Integer> allocations) {
        mutateReservations(allocations, true);
    }

    private void mutateReservations(Map<Long, Integer> allocations, boolean commit) {
        if (allocations == null || allocations.isEmpty()) return;
        List<Long> ids = allocations.keySet().stream().sorted().toList();
        List<InventoryBatch> batches = batchRepository.findAllByIdForUpdate(ids);
        if (batches.size() != ids.size()) throw new IllegalArgumentException("Inventory allocation contains an unknown batch");
        for (InventoryBatch batch : batches) {
            int quantity = allocations.getOrDefault(batch.getId(), 0);
            if (quantity <= 0) throw new IllegalArgumentException("Allocation quantity must be greater than 0");
            if (batch.getQuantityReserved() < quantity) {
                throw new IllegalStateException("Allocation has already been released or committed");
            }
            if (commit && (!Boolean.TRUE.equals(batch.getActive())
                    || batch.getExpiryDate() == null
                    || batch.getExpiryDate().isBefore(StoreTime.today()))) {
                throw new IllegalStateException(
                    "Inventory batch is inactive or expired and cannot be committed");
            }
            batch.setQuantityReserved(batch.getQuantityReserved() - quantity);
            if (commit) {
                if (batch.getQuantityOnHand() < quantity) {
                    throw new IllegalStateException("Inventory batch does not have enough on-hand stock");
                }
                batch.setQuantityOnHand(batch.getQuantityOnHand() - quantity);
            }
        }
        batchRepository.saveAll(batches);
    }

    private ProductVariant requireVariant(Long id) {
        return variantRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Product variant not found"));
    }

    private static void validateRequest(InventoryBatchRequest request, boolean creating) {
        String code = normalizeCode(request.getBatchCode());
        if (code == null) throw new IllegalArgumentException("Batch code is required");
        if (request.getExpiryDate() == null) throw new IllegalArgumentException("Expiry date is required");
        if (creating && request.getExpiryDate().isBefore(StoreTime.today())) {
            throw new IllegalArgumentException("Cannot add an already expired inventory batch");
        }
        if (request.getManufacturedDate() != null
                && request.getManufacturedDate().isAfter(request.getExpiryDate())) {
            throw new IllegalArgumentException("Manufactured date must not be after expiry date");
        }
        if (request.getQuantityOnHand() == null || request.getQuantityOnHand() < 0) {
            throw new IllegalArgumentException("On-hand quantity must be at least 0");
        }
    }

    private static String normalizeCode(String value) {
        String clean = BrandService.clean(value);
        return clean == null ? null : clean.toUpperCase(Locale.ROOT);
    }
}
