package t4m.beauty_store.order.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.annotation.Transactional;
import t4m.beauty_store.cart.service.CartIdentity;
import t4m.beauty_store.order.dto.CheckoutRequest;
import t4m.beauty_store.order.dto.OrderResponse;
import t4m.beauty_store.payment.service.VNPayService;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CheckoutServiceTests {
    private OrderService orderService;
    private VNPayService vnPayService;
    private CheckoutRateLimiter checkoutRateLimiter;
    private CheckoutGuardService checkoutGuardService;
    private CheckoutService service;
    private CheckoutRequest checkoutRequest;
    private CartIdentity identity;
    private MockHttpServletRequest httpRequest;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        vnPayService = mock(VNPayService.class);
        checkoutRateLimiter = mock(CheckoutRateLimiter.class);
        checkoutGuardService = mock(CheckoutGuardService.class);
        service = new CheckoutService(
            orderService, vnPayService, checkoutRateLimiter, checkoutGuardService);
        checkoutRequest = CheckoutRequest.builder()
            .customerName("Beauty Customer")
            .customerEmail("customer@example.test")
            .customerPhone("0900000000")
            .addressLine("1 Test Street")
            .ward("Ward 1")
            .district("District 1")
            .province("Ho Chi Minh City")
            .paymentMethod("VNPAY")
            .build();
        identity = new CartIdentity("customer@example.test", null);
        httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("203.0.113.10");
    }

    @Test
    void vnpayUrlFailurePropagatesFromTheTransactionalCheckoutBoundary() throws Exception {
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(15);
        OrderResponse order = vnpayOrder(expiresAt);
        IllegalStateException gatewayFailure = new IllegalStateException("gateway unavailable");
        when(orderService.createOrder(identity, checkoutRequest)).thenReturn(order);
        when(vnPayService.createPaymentUrl(
            eq("ORD-ATOMIC"), eq(BigDecimal.valueOf(500_000)), anyString(),
            same(httpRequest), eq(expiresAt)))
            .thenThrow(gatewayFailure);

        assertThatThrownBy(() -> service.checkout(identity, checkoutRequest, httpRequest))
            .isSameAs(gatewayFailure);

        Transactional transaction = CheckoutService.class
            .getDeclaredMethod("checkout", CartIdentity.class, CheckoutRequest.class,
                HttpServletRequest.class)
            .getAnnotation(Transactional.class);
        assertThat(transaction).as("gateway errors must cross a transactional boundary")
            .isNotNull();
        verify(checkoutRateLimiter).check(identity, checkoutRequest, "203.0.113.10");
        verify(checkoutGuardService).verifyOpenOrderQuota(identity, checkoutRequest);
        verify(orderService).createOrder(identity, checkoutRequest);
    }

    @Test
    void vnpayCheckoutUsesOnlyTheServerCreatedOrderForTheRedirect() {
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(12);
        OrderResponse order = vnpayOrder(expiresAt);
        when(orderService.createOrder(identity, checkoutRequest)).thenReturn(order);
        when(vnPayService.createPaymentUrl(
            eq("ORD-ATOMIC"), eq(BigDecimal.valueOf(500_000)),
            eq("Thanh toan don hang ORD-ATOMIC"), same(httpRequest), eq(expiresAt)))
            .thenReturn("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?signed=true");

        CheckoutResult result = service.checkout(identity, checkoutRequest, httpRequest);

        assertThat(result.order()).isSameAs(order);
        assertThat(result.redirectsToPayment()).isTrue();
        assertThat(result.paymentUrl()).startsWith("https://sandbox.vnpayment.vn/");
    }

    @Test
    void codCheckoutReturnsTheOrderWithoutCallingThePaymentGateway() {
        checkoutRequest.setPaymentMethod("COD");
        OrderResponse order = OrderResponse.builder()
            .orderNumber("ORD-COD")
            .paymentMethod("COD")
            .totalAmount(BigDecimal.valueOf(300_000))
            .build();
        when(orderService.createOrder(identity, checkoutRequest)).thenReturn(order);

        CheckoutResult result = service.checkout(identity, checkoutRequest, httpRequest);

        assertThat(result.order()).isSameAs(order);
        assertThat(result.paymentUrl()).isNull();
        assertThat(result.redirectsToPayment()).isFalse();
        verifyNoInteractions(vnPayService);
    }

    private static OrderResponse vnpayOrder(LocalDateTime expiresAt) {
        return OrderResponse.builder()
            .orderNumber("ORD-ATOMIC")
            .paymentMethod("VNPAY")
            .totalAmount(BigDecimal.valueOf(500_000))
            .reservationExpiresAt(expiresAt)
            .build();
    }
}
