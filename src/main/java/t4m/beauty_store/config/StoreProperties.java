package t4m.beauty_store.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Centralized customer-facing identity, contact, policy, and delivery settings. */
@Component
@ConfigurationProperties(prefix = "store")
@Getter
@Setter
public class StoreProperties {

    private String name = "BeautyStore";
    private String legalName = "";
    private String legalAddress = "";
    private String supportEmail = "support@beautystore.vn";
    private String hotline = "1800-8080";
    private String baseUrl = "http://localhost:8080";
    private BigDecimal shippingFee = BigDecimal.valueOf(30_000);
    private BigDecimal freeShippingThreshold = BigDecimal.valueOf(500_000);

    public String absoluteUrl(String path) {
        String normalizedBase = baseUrl == null ? "" : baseUrl.strip().replaceAll("/+$", "");
        String normalizedPath = path == null || path.isBlank()
                ? ""
                : (path.startsWith("/") ? path : "/" + path);
        return normalizedBase + normalizedPath;
    }
}
