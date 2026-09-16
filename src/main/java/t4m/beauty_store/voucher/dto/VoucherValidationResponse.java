package t4m.beauty_store.voucher.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import t4m.beauty_store.voucher.entity.DiscountType;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoucherValidationResponse {
    private boolean valid;
    private String message;
    private BigDecimal discountAmount;
    private String voucherCode;
    private DiscountType discountType;
    /** Shipping fee removed by this voucher for the current server-side cart. */
    private BigDecimal shippingDiscount;
}
