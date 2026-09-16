package t4m.beauty_store.order.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import t4m.beauty_store.cart.service.CartIdentity;
import t4m.beauty_store.order.dto.CheckoutRequest;
import t4m.beauty_store.order.dto.OrderResponse;
import t4m.beauty_store.payment.service.VNPayService;

/**
 * Keeps order creation, stock/voucher reservation, cart clearing and VNPay URL
 * generation in one transaction. A gateway configuration or URL error rolls
 * the entire checkout back so the customer never loses a cart to a hidden
 * pending order.
 */
@Service
@RequiredArgsConstructor
public class CheckoutService {
    private final OrderService orderService;
    private final VNPayService vnPayService;
    private final CheckoutRateLimiter checkoutRateLimiter;
    private final CheckoutGuardService checkoutGuardService;

    @Transactional
    public CheckoutResult checkout(CartIdentity identity, CheckoutRequest request,
            HttpServletRequest httpRequest) {
        checkoutRateLimiter.check(identity, request,
            httpRequest == null ? null : httpRequest.getRemoteAddr());
        checkoutGuardService.verifyOpenOrderQuota(identity, request);
        OrderResponse order = orderService.createOrder(identity, request);
        if (!"VNPAY".equalsIgnoreCase(order.getPaymentMethod())) {
            return new CheckoutResult(order, null);
        }
        String paymentUrl = vnPayService.createPaymentUrl(
            order.getOrderNumber(), order.getTotalAmount(),
            "Thanh toan don hang " + order.getOrderNumber(), httpRequest,
            order.getReservationExpiresAt());
        return new CheckoutResult(order, paymentUrl);
    }
}
