package t4m.beauty_store.admin.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductVariantRequest {
    @NotBlank(message = "Variant SKU is required")
    @Size(max = 100, message = "Variant SKU must not exceed 100 characters")
    private String sku;

    @Size(max = 100, message = "Barcode must not exceed 100 characters")
    private String barcode;
    private String label;
    private String shadeName;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Shade color must use #RRGGBB")
    private String shadeHex;

    @DecimalMin(value = "0", inclusive = false, message = "Size must be greater than 0")
    private BigDecimal sizeValue;
    private String sizeUnit;

    @NotNull(message = "Variant price is required")
    @DecimalMin(value = "0", message = "Variant price must be at least 0")
    private BigDecimal price;

    @DecimalMin(value = "0", message = "Discount price must be at least 0")
    private BigDecimal discountPrice;

    @Min(value = 0, message = "Low-stock threshold must be at least 0")
    private Integer lowStockThreshold;
    private Boolean defaultVariant;
    private Boolean active;
}
