package t4m.beauty_store.order.service;

import t4m.beauty_store.order.dto.OrderResponse;

/** Result of the atomic order-and-payment-link checkout operation. */
public record CheckoutResult(OrderResponse order, String paymentUrl) {
    public boolean redirectsToPayment() {
        return paymentUrl != null && !paymentUrl.isBlank();
    }
}
