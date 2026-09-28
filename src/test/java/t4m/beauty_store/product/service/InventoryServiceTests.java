package t4m.beauty_store.product.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import t4m.beauty_store.admin.dto.InventoryBatchRequest;
import t4m.beauty_store.config.ApiException;
import t4m.beauty_store.config.StoreTime;
import t4m.beauty_store.product.entity.InventoryBatch;
import t4m.beauty_store.product.entity.ProductVariant;
import t4m.beauty_store.product.repository.InventoryBatchRepository;
import t4m.beauty_store.product.repository.ProductVariantRepository;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class InventoryServiceTests {
    private InventoryBatchRepository batches;
    private InventoryService service;
    private InventoryBatch batch;

    @BeforeEach
    void setUp() {
        batches = mock(InventoryBatchRepository.class);
        service = new InventoryService(batches, mock(ProductVariantRepository.class));
        ProductVariant variant = new ProductVariant();
        variant.setId(10L);
        batch = InventoryBatch.builder().id(7L).variant(variant).batchCode("LOT-1")
            .expiryDate(StoreTime.today().plusMonths(6)).quantityOnHand(20)
            .quantityReserved(5).active(true).build();
        when(batches.findByIdForUpdate(7L)).thenReturn(Optional.of(batch));
    }

    @Test
    void blocksStructuralChangesWhileStockIsReserved() {
        InventoryBatchRequest request = request("LOT-2", batch.getExpiryDate(), 20, true);

        assertThatThrownBy(() -> service.updateBatch(7L, request))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("giữ tồn");
        verify(batches, never()).save(any());
    }

    @Test
    void blocksDeactivationWhileStockIsReserved() {
        assertThatThrownBy(() -> service.setBatchActive(7L, false))
            .isInstanceOf(ApiException.class);
        verify(batches, never()).save(any());
    }

    @Test
    void refusesToReactivateExpiredBatch() {
        batch.setQuantityReserved(0);
        batch.setActive(false);
        batch.setExpiryDate(StoreTime.today().minusDays(1));

        assertThatThrownBy(() -> service.setBatchActive(7L, true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("hết hạn");
    }

    private static InventoryBatchRequest request(String code, LocalDate expiry, int onHand, boolean active) {
        InventoryBatchRequest request = new InventoryBatchRequest();
        request.setBatchCode(code);
        request.setExpiryDate(expiry);
        request.setQuantityOnHand(onHand);
        request.setActive(active);
        return request;
    }
}
