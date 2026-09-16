package t4m.beauty_store.returns.dto;

import lombok.Builder;
import lombok.Data;
import t4m.beauty_store.returns.entity.ReturnRequest;
import t4m.beauty_store.returns.entity.ReturnStatus;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ReturnResponse {
    private Long id;
    private String orderNumber;
    private ReturnStatus status;
    private String adminNote;
    private String refundReference;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<Item> items;
    private List<History> history;

    @Data
    @Builder
    public static class Item {
        private Long id;
        private Long orderItemId;
        private String productName;
        private String variantLabel;
        private Integer quantity;
        private String reason;
        private Boolean unopenedAndSealed;
        private String details;
        private List<String> imageUrls;
        private Boolean restocked;
        private LocalDateTime restockedAt;
        private List<BatchRestock> batchRestocks;
    }

    @Data
    @Builder
    public static class BatchRestock {
        private Long batchId;
        private String batchCode;
        private Integer quantity;
    }

    @Data
    @Builder
    public static class History {
        private String action;
        private ReturnStatus fromStatus;
        private ReturnStatus toStatus;
        private String note;
        private String changedBy;
        private LocalDateTime createdAt;
    }

    public static ReturnResponse fromEntity(ReturnRequest request) {
        return ReturnResponse.builder()
            .id(request.getId())
            .orderNumber(request.getOrder().getOrderNumber())
            .status(request.getStatus())
            .adminNote(request.getAdminNote())
            .refundReference(request.getRefundReference())
            .createdAt(request.getCreatedAt())
            .updatedAt(request.getUpdatedAt())
            .items(request.getItems().stream().map(item -> Item.builder()
                .id(item.getId())
                .orderItemId(item.getOrderItem().getId())
                .productName(item.getOrderItem().getProductName())
                .variantLabel(item.getOrderItem().getVariantLabel())
                .quantity(item.getQuantity())
                .reason(item.getReason().name())
                .unopenedAndSealed(item.getUnopenedAndSealed())
                .details(item.getDetails())
                .imageUrls(item.getImageUrls())
                .restocked(item.getRestocked())
                .restockedAt(item.getRestockedAt())
                .batchRestocks(item.getBatchRestocks().stream().map(restock -> BatchRestock.builder()
                    .batchId(restock.getInventoryBatch().getId())
                    .batchCode(restock.getInventoryBatch().getBatchCode())
                    .quantity(restock.getQuantity())
                    .build()).toList())
                .build()).toList())
            .history(request.getHistory().stream().map(entry -> History.builder()
                .action(entry.getAction())
                .fromStatus(entry.getFromStatus())
                .toStatus(entry.getToStatus())
                .note(entry.getNote())
                .changedBy(entry.getChangedBy())
                .createdAt(entry.getCreatedAt())
                .build()).toList())
            .build();
    }
}
