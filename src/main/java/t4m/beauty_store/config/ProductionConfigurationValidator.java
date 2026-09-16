package t4m.beauty_store.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Stops production startup when security-critical or legal configuration is missing. */
@Component
@Profile("prod")
public class ProductionConfigurationValidator implements InitializingBean {

    private final String jwtSecret;
    private final VNPayConfig vnPayConfig;
    private final StoreProperties storeProperties;

    public ProductionConfigurationValidator(
            @Value("${security.jwt.secret:}") String jwtSecret,
            VNPayConfig vnPayConfig,
            StoreProperties storeProperties) {
        this.jwtSecret = jwtSecret;
        this.vnPayConfig = vnPayConfig;
        this.storeProperties = storeProperties;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> missingOrInvalid = new ArrayList<>();
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            missingOrInvalid.add("security.jwt.secret (minimum 32 bytes)");
        }
        if (!vnPayConfig.isConfigured()) {
            missingOrInvalid.add("vnpay.tmn-code/vnpay.hash-secret");
        }
        if (!isHttps(vnPayConfig.getPayUrl())
                || !isHttps(vnPayConfig.getReturnUrl())
                || !isHttps(vnPayConfig.getIpnUrl())) {
            missingOrInvalid.add("vnpay URLs (HTTPS required)");
        }
        if (isBlank(storeProperties.getLegalName())) {
            missingOrInvalid.add("store.legal-name");
        }
        if (isBlank(storeProperties.getLegalAddress())) {
            missingOrInvalid.add("store.legal-address");
        }
        if (isBlank(storeProperties.getSupportEmail()) || !storeProperties.getSupportEmail().contains("@")) {
            missingOrInvalid.add("store.support-email");
        }
        if (!isHttps(storeProperties.getBaseUrl())) {
            missingOrInvalid.add("store.base-url (HTTPS required)");
        }
        if (!missingOrInvalid.isEmpty()) {
            throw new IllegalStateException(
                    "Production configuration is incomplete: " + String.join(", ", missingOrInvalid));
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isHttps(String value) {
        return value != null && value.strip().toLowerCase(Locale.ROOT).startsWith("https://");
    }
}
