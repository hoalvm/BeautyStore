package t4m.beauty_store.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class InventoryBatchRequest {
    @NotBlank(message = "Batch code is required")
    private String batchCode;
    private LocalDate manufacturedDate;

    @NotNull(message = "Expiry date is required")
    private LocalDate expiryDate;

    @NotNull(message = "On-hand quantity is required")
    @Min(value = 0, message = "On-hand quantity must be at least 0")
    private Integer quantityOnHand;

    private Boolean active;
}
