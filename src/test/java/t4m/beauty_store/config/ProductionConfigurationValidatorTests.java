package t4m.beauty_store.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductionConfigurationValidatorTests {
    private static final String PRIVATE_TEST_FIXTURE = "a-private-fixture-with-at-least-32-bytes";
    private VNPayConfig payment;
    private StoreProperties store;

    @BeforeEach
    void validConfiguration() {
        payment = mock(VNPayConfig.class);
        when(payment.isConfigured()).thenReturn(true);
        when(payment.getPayUrl()).thenReturn("https://payment.example.test/pay");
        when(payment.getReturnUrl()).thenReturn("https://store.example.test/api/payment/vnpay/return");
        when(payment.getIpnUrl()).thenReturn("https://store.example.test/api/payment/vnpay/ipn");
        store = new StoreProperties();
        store.setBaseUrl("https://store.example.test");
        store.setLegalName("Production fixture");
        store.setLegalAddress("Production address fixture");
        store.setSupportEmail("support@example.test");
    }

    @Test
    void permitsCompletePrivateHttpsConfiguration() {
        assertThatCode(() -> validator(PRIVATE_TEST_FIXTURE).afterPropertiesSet()).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "                                ",
        "beautystore-local-demo-only-not-for-production-2026",
        "beautystore-test-secret-key-that-is-never-used-in-production",
        "short-secret"
    })
    void rejectsBlankOrPublicDevelopmentKeys(String secret) {
        assertThatThrownBy(() -> validator(secret).afterPropertiesSet())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("security.jwt.secret")
            .hasMessageNotContaining(secret);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://",
        "http://store.example.test",
        "https://username:password@store.example.test",
        "https://store.example.test:99999",
        "https://store.example.test/callback#fragment",
        "https://store .example.test"
    })
    void rejectsMalformedOrCredentialBearingPublicUrls(String url) {
        store.setBaseUrl(url);

        assertThatThrownBy(() -> validator(PRIVATE_TEST_FIXTURE).afterPropertiesSet())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("store.base-url");
    }

    @Test
    void validatesCallbackUrlSyntaxRatherThanOnlyHttpsPrefix() {
        when(payment.getIpnUrl()).thenReturn("https://");

        assertThatThrownBy(() -> validator(PRIVATE_TEST_FIXTURE).afterPropertiesSet())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("vnpay URLs");
    }

    private ProductionConfigurationValidator validator(String secret) {
        return new ProductionConfigurationValidator(secret, payment, store);
    }
}
