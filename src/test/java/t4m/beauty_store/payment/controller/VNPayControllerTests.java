package t4m.beauty_store.payment.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import t4m.beauty_store.order.service.OrderService;
import t4m.beauty_store.payment.dto.VNPayPaymentOutcome;
import t4m.beauty_store.payment.dto.VNPayPaymentResult;
import t4m.beauty_store.payment.service.VNPayService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VNPayControllerTests {
    private VNPayService vnPayService;
    private OrderService orderService;
    private VNPayController controller;

    @BeforeEach
    void setUp() {
        vnPayService = mock(VNPayService.class);
        orderService = mock(OrderService.class);
        controller = new VNPayController(vnPayService, orderService);
        when(vnPayService.verifyPaymentResponse(anyMap())).thenReturn(true);
    }

    @Test
    void lateSignedSuccessIsNotAcknowledgedAsCommitted() {
        MockHttpServletRequest request = successfulIpn();
        when(orderService.processVNPayIpn(
            eq("ORD-1"), eq(200_000L), eq(true), eq("TX-1"), eq("NCB"), eq("00")))
            .thenReturn(new VNPayPaymentResult(
                VNPayPaymentOutcome.RECONCILIATION_REQUIRED,
                "RECONCILIATION_REQUIRED", false));

        Map<String, String> body = controller.paymentIpn(request).getBody();

        assertThat(body).containsEntry("RspCode", "99")
            .containsEntry("Message", "Payment requires reconciliation");
    }

    @Test
    void successfulIpnIsAcknowledgedOnlyAfterInventoryWasCommitted() {
        MockHttpServletRequest request = successfulIpn();
        when(orderService.processVNPayIpn(anyString(), anyLong(), eq(true),
                any(), any(), anyString()))
            .thenReturn(new VNPayPaymentResult(
                VNPayPaymentOutcome.CONFIRMED, "PAID", true));

        assertThat(controller.paymentIpn(request).getBody())
            .containsEntry("RspCode", "00");
    }

    @Test
    void inconsistentConfirmationOutcomeIsRejectedDefensively() {
        MockHttpServletRequest request = successfulIpn();
        when(orderService.processVNPayIpn(anyString(), anyLong(), eq(true),
                any(), any(), anyString()))
            .thenReturn(new VNPayPaymentResult(
                VNPayPaymentOutcome.CONFIRMED, "PAID", false));

        assertThat(controller.paymentIpn(request).getBody())
            .containsEntry("RspCode", "99")
            .containsEntry("Message", "Payment was not committed");
    }

    private static MockHttpServletRequest successfulIpn() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("vnp_TxnRef", "ORD-1");
        request.setParameter("vnp_Amount", "20000000");
        request.setParameter("vnp_ResponseCode", "00");
        request.setParameter("vnp_TransactionStatus", "00");
        request.setParameter("vnp_TransactionNo", "TX-1");
        request.setParameter("vnp_BankCode", "NCB");
        request.setParameter("vnp_SecureHash", "signed");
        return request;
    }
}
