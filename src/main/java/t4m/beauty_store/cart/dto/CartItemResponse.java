package t4m.beauty_store.cart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItemResponse {
    private Long id;
    private Long productId;
    private Long variantId;
    private String productName;
    private String productImageUrl;
    private String productSku;
    private String variantLabel;
    private String shadeName;
    private String netContent;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal subtotal;
    private Integer availableStock;
    private Boolean available;
}
