package t4m.beauty_store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import t4m.beauty_store.product.repository.ProductVariantRepository;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Render actual Thymeleaf templates, not mocked view names. No external services. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FrontendRenderingTests {
    @Autowired MockMvc mvc;
    @Autowired ProductVariantRepository variants;

    @ParameterizedTest
    @ValueSource(strings = {"/", "/products", "/cart", "/checkout", "/login", "/register",
        "/forgot-password", "/reset-password", "/verify-otp", "/favorites",
        "/order-confirmation/TEST-ORDER", "/payment-pending/TEST-ORDER",
        "/shopping-guide", "/return-policy", "/terms", "/privacy", "/payment-security"})
    void publicPagesRenderSharedAccessibleShell(String path) throws Exception {
        mvc.perform(get(path))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/html"))
            .andExpect(content().string(containsString("id=\"main-content\"")))
            .andExpect(content().string(containsString("name=\"search\"")))
            .andExpect(content().string(containsString("/js/beauty-ui.js")));
    }

    @ParameterizedTest
    @WithMockUser(roles = "USER")
    @ValueSource(strings = {"/profile", "/orders"})
    void memberPagesRender(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk())
            .andExpect(content().string(containsString("/js/auth.js")));
    }

    @ParameterizedTest
    @WithMockUser(roles = "ADMIN")
    @ValueSource(strings = {"/admin", "/admin/products", "/admin/orders", "/admin/accounts",
        "/admin/vouchers", "/admin/vouchers/create", "/admin/vouchers/edit/1",
        "/admin/reviews", "/admin/returns", "/admin/support"})
    void adminPagesRender(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/html"));
    }

    @Test
    @WithMockUser(roles = "SHIPPER")
    void shipperPageLoadsItsHandlers() throws Exception {
        mvc.perform(get("/shipper")).andExpect(status().isOk())
            .andExpect(content().string(containsString("/js/shipper-dashboard.js")))
            .andExpect(content().string(containsString("/images/beauty/favicon.svg")))
            .andExpect(content().string(containsString("id=\"deliveryProofForm\"")))
            .andExpect(content().string(containsString("aria-labelledby=\"deliveryProofTitle\"")))
            .andExpect(content().string(containsString("/js/delivery-proof.js")));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void productCanonicalAndLegacyRedirectPreserveReviewContext() throws Exception {
        var product = variants.findAll().stream().filter(variant -> "BEA-DEMO-SKIN-001".equals(variant.getSku()))
            .findFirst().orElseThrow().getProduct();
        mvc.perform(get("/product/" + product.getId()).param("orderItemId", "123"))
            .andExpect(status().isMovedPermanently())
            .andExpect(redirectedUrl("/products/" + product.getSlug() + "?orderItemId=123"));
        mvc.perform(get("/products/" + product.getSlug()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("rel=\"canonical\"")))
            .andExpect(content().string(containsString("application/ld+json")))
            .andExpect(content().string(containsString("BEA-DEMO-SKIN-001")));
    }
}
