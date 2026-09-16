package t4m.beauty_store.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import t4m.beauty_store.order.entity.Order;
import t4m.beauty_store.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private Long id;
    private String orderNumber;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private String shippingAddress;
    private String shippingAddressLine;
    private String shippingWard;
    private String shippingDistrict;
    private String shippingProvince;
    private String paymentMethod;
    private BigDecimal subtotal;
    private BigDecimal productDiscount;
    private BigDecimal shippingFee;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private String notes;
    private List<OrderItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime reservationExpiresAt;
    private Long shipperId;
    private String shipperEmail;
    private String voucherCode;
    private BigDecimal voucherDiscount;
    private String voucherType;
    
    // VNPay payment information
    private String paymentStatus;
    private String vnpayTransactionNo;
    private String vnpayBankCode;
    private String vnpayResponseCode;
    private String deliveryFailureReason;
    private Boolean inventoryCommitted;

    public static OrderResponse fromEntity(Order order) {
        List<OrderItemResponse> items = order.getOrderItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProduct().getId())
                        .variantId(item.getVariant() != null ? item.getVariant().getId() : null)
                        .productName(item.getProductName())
                        .productImageUrl(item.getProductImageUrl())
                        .productSku(item.getProductSku())
                        .variantLabel(item.getVariantLabel())
                        .shadeName(item.getShadeName())
                        .netContent(item.getNetContent())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .subtotal(item.getSubtotal())
                        .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .customerName(order.getCustomerName())
                .customerEmail(order.getCustomerEmail())
                .customerPhone(order.getCustomerPhone())
                .shippingAddress(order.getShippingAddress())
                .shippingAddressLine(order.getShippingAddressLine())
                .shippingWard(order.getShippingWard())
                .shippingDistrict(order.getShippingDistrict())
                .shippingProvince(order.getShippingProvince())
                .paymentMethod(order.getPaymentMethod())
                .subtotal(order.getSubtotal())
                .productDiscount(order.getProductDiscount())
                .shippingFee(order.getShippingFee())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .notes(order.getNotes())
                .items(items)
                .createdAt(order.getCreatedAt())
                .reservationExpiresAt(order.getReservationExpiresAt())
                .shipperId(order.getShipper() != null ? order.getShipper().getId() : null)
                .shipperEmail(order.getShipper() != null ? order.getShipper().getEmail() : null)
                .voucherCode(order.getVoucherCode())
                .voucherDiscount(order.getVoucherDiscount())
                .voucherType(order.getVoucherType())
                .paymentStatus(order.getPaymentStatus())
                .vnpayTransactionNo(order.getVnpayTransactionNo())
                .vnpayBankCode(order.getVnpayBankCode())
                .vnpayResponseCode(order.getVnpayResponseCode())
                .deliveryFailureReason(order.getDeliveryFailureReason())
                .inventoryCommitted(order.getInventoryCommitted())
                .build();
    }
}
