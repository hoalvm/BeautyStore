package t4m.beauty_store.main.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import t4m.beauty_store.config.StoreProperties;
import t4m.beauty_store.product.dto.ProductResponse;
import t4m.beauty_store.product.entity.Product;
import t4m.beauty_store.product.service.ProductService;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class PageController {

    private final ProductService productService;
    private final StoreProperties storeProperties;
    private final ObjectMapper objectMapper;

    @GetMapping({"/", "/home"})
    public String home() {
        return "index";
    }

    @GetMapping({"/login"})
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @GetMapping("/verify-otp")
    public String verifyOtp() {
        return "verify-otp";
    }

    @GetMapping("/forgot-password")
    public String forgotPassword() {
        return "forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPassword() {
        return "reset-password";
    }

    @GetMapping("/profile")
    public String profile() {
        return "profile";
    }

    @GetMapping("/products")
    public String products() {
        return "products";
    }

    @GetMapping("/products/{slug}")
    public String productDetail(@PathVariable String slug, Model model) {
        Product product = productService.getActiveProductBySlug(slug);
        if (product == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.NOT_FOUND, "Sản phẩm không tồn tại hoặc đã ngừng bán");
        }
        ProductResponse response = ProductResponse.fromEntity(product);
        String canonicalUrl = storeProperties.absoluteUrl("/products/" + response.getSlug());
        model.addAttribute("seoProduct", response);
        model.addAttribute("canonicalUrl", canonicalUrl);
        model.addAttribute("productJsonLd", productJsonLd(response, canonicalUrl));
        return "product-detail";
    }

    @GetMapping("/product/{id}")
    public ResponseEntity<Void> legacyProductDetail(
            @PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long orderItemId) {
        Product product = productService.getActiveProductById(id);
        if (product == null) return ResponseEntity.notFound().build();
        String location = "/products/" + product.getSlug()
            + (orderItemId == null ? "" : "?orderItemId=" + orderItemId);
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
            .location(URI.create(location))
            .build();
    }

    @GetMapping("/cart")
    public String cart() {
        return "cart";
    }

    @GetMapping("/checkout")
    public String checkout() {
        return "checkout";
    }

    @GetMapping("/order-confirmation/{orderNumber}")
    public String orderConfirmation() {
        return "order-confirmation";
    }

    @GetMapping("/payment-pending/{orderNumber}")
    public String paymentPending() {
        return "payment-pending";
    }

    @GetMapping("/orders")
    public String orders() {
        return "orders";
    }
    
    @GetMapping("/favorites")
    public String favorites() {
        return "favorites";
    }
    
    // Policy Pages
    @GetMapping("/terms")
    public String terms() {
        return "policies/terms";
    }
    
    @GetMapping("/privacy")
    public String privacy() {
        return "policies/privacy";
    }
    
    @GetMapping("/return-policy")
    public String returnPolicy() {
        return "policies/return-policy";
    }
    
    @GetMapping("/shopping-guide")
    public String shoppingGuide() {
        return "policies/shopping-guide";
    }
    
    @GetMapping("/payment-security")
    public String paymentSecurity() {
        return "policies/payment-security";
    }

    private String productJsonLd(ProductResponse product, String canonicalUrl) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("@context", "https://schema.org");
        json.put("@type", "Product");
        json.put("name", product.getName());
        json.put("url", canonicalUrl);
        json.put("description", firstNonBlank(product.getBenefits(), product.getDescription()));
        List<String> images = product.getGallery().stream()
            .map(image -> image.getUrl()).filter(value -> value != null && !value.isBlank()).toList();
        json.put("image", images);
        product.getVariants().stream()
            .filter(variant -> Boolean.TRUE.equals(variant.getDefaultVariant()))
            .findFirst()
            .or(() -> product.getVariants().stream().findFirst())
            .map(variant -> variant.getSku())
            .ifPresent(sku -> json.put("sku", sku));
        if (product.getBrandName() != null) {
            json.put("brand", Map.of("@type", "Brand", "name", product.getBrandName()));
        }
        Map<String, Object> offer = new LinkedHashMap<>();
        offer.put("@type", "AggregateOffer");
        offer.put("url", canonicalUrl);
        offer.put("priceCurrency", "VND");
        offer.put("lowPrice", product.getMinPrice());
        offer.put("highPrice", product.getMaxPrice());
        offer.put("offerCount", Math.max(1, product.getVariants().size()));
        offer.put("availability", Boolean.TRUE.equals(product.getInStock())
            ? "https://schema.org/InStock" : "https://schema.org/OutOfStock");
        json.put("offers", offer);
        if (product.getRatingCount() != null && product.getRatingCount() > 0) {
            json.put("aggregateRating", Map.of(
                "@type", "AggregateRating",
                "ratingValue", product.getAverageRating(),
                "reviewCount", product.getRatingCount()));
        }
        try {
            return objectMapper.writeValueAsString(json)
                .replace("<", "\\u003c").replace(">", "\\u003e")
                .replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Không thể tạo dữ liệu SEO cho sản phẩm", exception);
        }
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
