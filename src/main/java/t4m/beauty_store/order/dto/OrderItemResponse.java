package t4m.beauty_store.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {
    private Long id;
    private Long productId;
    private Long variantId;
    private String productName;
    private String productImageUrl;
    private String productSku;
    private String variantLabel;
    private String shadeName;
    private String netContent;
    private Integer quantity;
    private BigDecimal price;
    private BigDecimal subtotal;
}
