package t4m.beauty_store.product.dto;

import lombok.*;
import t4m.beauty_store.product.entity.InventoryBatch;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryBatchResponse {
    private Long id;
    private Long variantId;
    private String batchCode;
    private LocalDate manufacturedDate;
    private LocalDate expiryDate;
    private Integer quantityOnHand;
    private Integer quantityReserved;
    private Integer availableQuantity;
    private Boolean active;
    private Long version;

    public static InventoryBatchResponse fromEntity(InventoryBatch batch) {
        return InventoryBatchResponse.builder()
            .id(batch.getId())
            .variantId(batch.getVariant().getId())
            .batchCode(batch.getBatchCode())
            .manufacturedDate(batch.getManufacturedDate())
            .expiryDate(batch.getExpiryDate())
            .quantityOnHand(batch.getQuantityOnHand())
            .quantityReserved(batch.getQuantityReserved())
            .availableQuantity(batch.getAvailableQuantity())
            .active(Boolean.TRUE.equals(batch.getActive()))
            .version(batch.getVersion())
            .build();
    }
}
