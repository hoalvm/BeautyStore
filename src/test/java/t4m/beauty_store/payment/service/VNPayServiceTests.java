package t4m.beauty_store.payment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import t4m.beauty_store.config.VNPayConfig;
import t4m.beauty_store.payment.util.VNPayUtil;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VNPayServiceTests {

    private static final String HASH_SECRET =
            "test-vnpay-hash-secret-with-sufficient-entropy-123456";

    private VNPayService service;

    @BeforeEach
    void setUp() {
        VNPayConfig config = new VNPayConfig();
        ReflectionTestUtils.setField(config, "tmnCode", "TESTSHOP");
        ReflectionTestUtils.setField(config, "hashSecret", HASH_SECRET);
        ReflectionTestUtils.setField(config, "payUrl", "https://sandbox.example/pay");
        ReflectionTestUtils.setField(config, "returnUrl", "http://localhost:8080/api/payment/vnpay/return");
        ReflectionTestUtils.setField(config, "ipnUrl", "http://localhost:8080/api/payment/vnpay/ipn");
        service = new VNPayService(config);
    }

    @Test
    void createsUrlUsingExactServerAmount() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        String url = service.createPaymentUrl(
                "BEA-ORDER-1", BigDecimal.valueOf(123_456), null, request);

        assertThat(url).contains("vnp_Amount=12345600");
        assertThat(url).contains("vnp_TxnRef=BEA-ORDER-1");
        assertThat(url).doesNotContain(HASH_SECRET);
    }

    @Test
    void rejectsFractionalVndAmount() {
        assertThatThrownBy(() -> service.createPaymentUrl(
                "BEA-ORDER-1", new BigDecimal("1000.50"), null, new MockHttpServletRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(ArithmeticException.class);
    }

    @Test
    void verifiesOnlyUntamperedSignedFields() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "BEA-ORDER-1");
        params.put("vnp_Amount", "12345600");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_SecureHash", VNPayUtil.hmacSHA512(
                HASH_SECRET, VNPayUtil.hashAllFields(params)));

        assertThat(service.verifyPaymentResponse(params)).isTrue();

        params.put("vnp_Amount", "100");
        assertThat(service.verifyPaymentResponse(params)).isFalse();
    }
}
