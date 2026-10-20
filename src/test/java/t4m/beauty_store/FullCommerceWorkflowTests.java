package t4m.beauty_store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import t4m.beauty_store.auth.entity.User;
import t4m.beauty_store.auth.repository.UserRepository;
import t4m.beauty_store.config.StoreTime;
import t4m.beauty_store.order.repository.OrderRepository;
import t4m.beauty_store.order.service.GuestOrderOtpDeliveryService;
import t4m.beauty_store.product.repository.InventoryBatchRepository;
import t4m.beauty_store.product.repository.ProductVariantRepository;
import t4m.beauty_store.voucher.entity.DiscountType;
import t4m.beauty_store.voucher.entity.Voucher;
import t4m.beauty_store.voucher.repository.VoucherRepository;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Real HTTP controllers, security, services and database transactions. In
 * particular, no test transaction or Open Session in View may mask detached
 * response errors after an order transition has already committed.
 */
@SpringBootTest(properties = "orders.reservation-cleanup-ms=86400000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FullCommerceWorkflowTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ProductVariantRepository variants;
    @Autowired InventoryBatchRepository batches;
    @Autowired OrderRepository orders;
    @Autowired VoucherRepository vouchers;
    @MockitoBean GuestOrderOtpDeliveryService otpDelivery;

    private String customer;
    private String shipper;
    private String phone;
    private Long variantId;
    private Long productId;
    private Long batchId;
    private int startingStock;

    @BeforeEach
    void fixture(TestInfo testInfo) {
        customer = createUser("customer");
        shipper = createUser("shipper");
        phone = "09" + String.format("%08d", Math.floorMod(UUID.randomUUID().hashCode(), 100_000_000));
        String sku = switch (testInfo.getTestMethod().orElseThrow().getName()) {
            case "memberCodDeliveryReviewAndReturnCompleteAcrossRoles" -> "BEA-DEMO-SKIN-001";
            case "failedDeliveryIsRestockedOnceAndAdminCanSafelyRetryStatusResponse" -> "BEA-DEMO-SKIN-002";
            case "guestRequiresSingleUseOtpBeforeViewingOrCancellingOrder" -> "BEA-DEMO-SKIN-003";
            case "freeShippingVoucherUsageIsRestoredWhenOrderIsCancelled" -> "BEA-DEMO-SKIN-005";
            case "shippingThresholdUsesServerSubtotal" -> "BEA-DEMO-SUN-001";
            case "loginMergesGuestCartExactlyOnceByVariant" -> "BEA-DEMO-SUN-002";
            default -> "BEA-DEMO-SKIN-004";
        };
        var variant = variants.findAll().stream()
            .filter(candidate -> sku.equals(candidate.getSku()))
            .findFirst().orElseThrow();
        variantId = variant.getId();
        productId = variant.getProduct().getId();
        var batch = batches.findAll().stream()
            .filter(candidate -> candidate.getVariant().getId().equals(variantId))
            .findFirst().orElseThrow();
        batchId = batch.getId();
        startingStock = batch.getQuantityOnHand();
    }

    @Test
    void memberCodDeliveryReviewAndReturnCompleteAcrossRoles() throws Exception {
        JsonNode order = memberCheckout(2);
        long id = order.path("id").asLong();
        long itemId = order.path("items").get(0).path("id").asLong();
        String number = order.path("orderNumber").asText();
        assertStock(startingStock, 2);
        mvc.perform(get("/api/orders/" + number).with(user(createUser("stranger")).roles("USER")))
            .andExpect(status().is4xxClientError());
        mvc.perform(put("/api/admin/orders/" + id + "/status")
            .with(user(customer).roles("USER")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"PROCESSING\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/shipper/orders/available").with(user(customer).roles("USER")))
            .andExpect(status().isForbidden());

        // Retrying the current status must return a usable response, not a 500.
        transition(id, "PENDING");
        transition(id, "PROCESSING");
        assertStock(startingStock - 2, 0);
        mvc.perform(get("/api/shipper/orders/available").with(user(shipper).roles("SHIPPER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == " + id + ")]").isNotEmpty());
        deliver(id);

        JsonNode review = ok(post("/api/reviews").with(user(customer).roles("USER"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("orderItemId", itemId,
                "stars", 5, "title", "Đánh giá demo", "content", "Đã nhận sản phẩm còn nguyên seal."))));
        assertThat(review.path("status").asText()).isEqualTo("PENDING");
        mvc.perform(get("/api/reviews/product/" + productId))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[?(@.id == "
                + review.path("id").asLong() + ")]").isEmpty());
        ok(patch("/api/admin/reviews/" + review.path("id").asLong() + "/status")
            .param("status", "APPROVED").with(user("admin@example.test").roles("ADMIN")));
        mvc.perform(get("/api/reviews/product/" + productId))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[?(@.id == "
                + review.path("id").asLong() + ")]").isNotEmpty());
        mvc.perform(post("/api/reviews").with(user(customer).roles("USER"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"orderItemId\":" + itemId + ",\"stars\":5}"))
            .andExpect(status().isBadRequest());

        JsonNode returned = ok(post("/api/returns").with(user(customer).roles("USER"))
            .contentType(MediaType.APPLICATION_JSON).content(returnRequest(number, itemId)));
        long returnId = returned.path("id").asLong();
        long returnItemId = returned.path("items").get(0).path("id").asLong();
        returnTransition(returnId, "APPROVED");
        mvc.perform(get("/api/admin/returns").with(user("admin@example.test").roles("ADMIN")))
            .andExpect(status().isOk());
        mvc.perform(get("/api/returns").with(user(customer).roles("USER")).param("orderNumber", number))
            .andExpect(status().isOk());
        returnTransition(returnId, "RECEIVED");
        String restock = "{\"returnItemIds\":[" + returnItemId + "]}";
        ok(post("/api/admin/returns/" + returnId + "/restock")
            .with(user("admin@example.test").roles("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).content(restock));
        assertStock(startingStock - 1, 0);
        mvc.perform(post("/api/admin/returns/" + returnId + "/restock")
            .with(user("admin@example.test").roles("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).content(restock)).andExpect(status().isBadRequest());
        // Once inventory is returned, rejecting the claim must not free up its quantity.
        mvc.perform(patch("/api/admin/returns/" + returnId + "/status")
            .with(user("admin@example.test").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"REJECTED\"}")).andExpect(status().isBadRequest());
        JsonNode refunded = ok(patch("/api/admin/returns/" + returnId + "/status")
            .with(user("admin@example.test").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"REFUNDED\",\"refundReference\":\"DEMO-REFUND-001\"}"));
        assertThat(refunded.path("refundReference").asText()).isEqualTo("DEMO-REFUND-001");
        assertStock(startingStock - 1, 0);
    }

    @Test
    void failedDeliveryIsRestockedOnceAndAdminCanSafelyRetryStatusResponse() throws Exception {
        JsonNode order = memberCheckout(1);
        long id = order.path("id").asLong();
        transition(id, "PROCESSING");
        ok(put("/api/shipper/orders/" + id + "/accept").with(user(shipper).roles("SHIPPER")));
        ok(put("/api/shipper/orders/" + id + "/fail").with(user(shipper).roles("SHIPPER"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Khách hẹn lại\"}"));
        assertStock(startingStock - 1, 0);
        JsonNode restocked = ok(post("/api/admin/orders/" + id + "/restock-failed-delivery")
            .with(user("admin@example.test").roles("ADMIN")));
        assertThat(restocked.path("shipperEmail").asText()).isEqualTo(shipper);
        assertStock(startingStock, 0);
        mvc.perform(post("/api/admin/orders/" + id + "/restock-failed-delivery")
            .with(user("admin@example.test").roles("ADMIN"))).andExpect(status().isBadRequest());
        transition(id, "CANCELLED");
        transition(id, "CANCELLED");
        assertStock(startingStock, 0);
    }

    @Test
    void guestRequiresSingleUseOtpBeforeViewingOrCancellingOrder() throws Exception {
        MvcResult added = mvc.perform(post("/api/cart/add").contentType(MediaType.APPLICATION_JSON)
            .content("{\"variantId\":" + variantId + ",\"quantity\":1}"))
            .andExpect(status().isOk()).andReturn();
        Cookie cartCookie = added.getResponse().getCookie("beauty_cart");
        assertThat(cartCookie).isNotNull();
        assertThat(cartCookie.isHttpOnly()).isTrue();
        JsonNode order = ok(post("/api/orders/checkout").cookie(cartCookie)
            .contentType(MediaType.APPLICATION_JSON).content(checkoutBody("COD")));
        String number = order.path("orderNumber").asText();
        assertStock(startingStock, 1);
        mvc.perform(get("/api/orders/" + number)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/guest-orders/" + number)).andExpect(status().isBadRequest());
        String requestBody = json.writeValueAsString(Map.of("orderNumber", number, "email", customer));
        ok(post("/api/guest-orders/access/request").contentType(MediaType.APPLICATION_JSON).content(requestBody));
        ArgumentCaptor<String> otp = ArgumentCaptor.forClass(String.class);
        verify(otpDelivery).schedule(anyLong(), eq(customer), anyString(), eq(number), otp.capture());
        String verifyBody = json.writeValueAsString(Map.of("orderNumber", number, "email", customer,
            "otp", otp.getValue()));
        JsonNode access = ok(post("/api/guest-orders/access/verify")
            .contentType(MediaType.APPLICATION_JSON).content(verifyBody));
        String token = access.path("accessToken").asText();
        mvc.perform(post("/api/guest-orders/access/verify")
            .contentType(MediaType.APPLICATION_JSON).content(verifyBody)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/guest-orders/" + number).header("X-Order-Token", token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.orderNumber").value(number));
        ok(post("/api/guest-orders/" + number + "/cancel").header("X-Order-Token", token));
        assertStock(startingStock, 0);
    }

    @Test
    void unconfiguredVnpayPreservesCartAndDoesNotReserveInventory() throws Exception {
        ok(post("/api/cart/add").with(user(customer).roles("USER"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"variantId\":" + variantId + ",\"quantity\":1}"));
        long before = orders.count();
        mvc.perform(post("/api/orders/checkout").with(user(customer).roles("USER"))
            .contentType(MediaType.APPLICATION_JSON).content(checkoutBody("VNPAY")))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("PAYMENT_UNAVAILABLE"));
        assertThat(orders.count()).isEqualTo(before);
        assertStock(startingStock, 0);
        mvc.perform(get("/api/cart").with(user(customer).roles("USER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void freeShippingVoucherUsageIsRestoredWhenOrderIsCancelled() throws Exception {
        setPrice("250000");
        Voucher voucher = new Voucher();
        voucher.setCode("FLOW-FREESHIP");
        voucher.setDiscountType(DiscountType.FREE_SHIPPING);
        voucher.setDiscountValue(BigDecimal.ZERO);
        voucher.setTotalQuantity(10);
        voucher.setLimitPerUser(1);
        voucher.setStartDate(StoreTime.now().minusDays(1));
        voucher.setEndDate(StoreTime.now().plusDays(1));
        vouchers.saveAndFlush(voucher);
        ok(post("/api/cart/add").with(user(customer).roles("USER")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"variantId\":" + variantId + ",\"quantity\":1}"));
        mvc.perform(post("/api/vouchers/validate").param("code", voucher.getCode())
            .with(user(customer).roles("USER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(true))
            .andExpect(jsonPath("$.shippingDiscount").value(30000));
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(checkoutBody("COD"));
        body.put("voucherCode", voucher.getCode());
        JsonNode first = ok(post("/api/orders/checkout").with(user(customer).roles("USER"))
            .contentType(MediaType.APPLICATION_JSON).content(body.toString()));
        assertThat(first.path("subtotal").decimalValue()).isEqualByComparingTo("250000");
        assertThat(first.path("shippingFee").decimalValue()).isZero();
        assertThat(first.path("totalAmount").decimalValue()).isEqualByComparingTo("250000");
        assertThat(vouchers.findById(voucher.getId()).orElseThrow().getUsedQuantity()).isEqualTo(1);
        ok(post("/api/cart/add").with(user(customer).roles("USER")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"variantId\":" + variantId + ",\"quantity\":1}"));
        mvc.perform(post("/api/orders/checkout").with(user(customer).roles("USER"))
            .contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isBadRequest());
        ok(post("/api/orders/" + first.path("orderNumber").asText() + "/cancel")
            .with(user(customer).roles("USER")));
        assertThat(vouchers.findById(voucher.getId()).orElseThrow().getUsedQuantity()).isZero();
        JsonNode retried = ok(post("/api/orders/checkout").with(user(customer).roles("USER"))
            .contentType(MediaType.APPLICATION_JSON).content(body.toString()));
        assertThat(retried.path("shippingFee").decimalValue()).isZero();
        ok(post("/api/orders/" + retried.path("orderNumber").asText() + "/cancel")
            .with(user(customer).roles("USER")));
        assertThat(vouchers.findById(voucher.getId()).orElseThrow().getUsedQuantity()).isZero();
        assertStock(startingStock, 0);
    }

    @Test
    void shippingThresholdUsesServerSubtotal() throws Exception {
        setPrice("250000");
        JsonNode below = memberCheckout(1);
        assertThat(below.path("shippingFee").decimalValue()).isEqualByComparingTo("30000");
        assertThat(below.path("totalAmount").decimalValue()).isEqualByComparingTo("280000");
        ok(post("/api/orders/" + below.path("orderNumber").asText() + "/cancel")
            .with(user(customer).roles("USER")));
        JsonNode atThreshold = memberCheckout(2);
        assertThat(atThreshold.path("subtotal").decimalValue()).isEqualByComparingTo("500000");
        assertThat(atThreshold.path("shippingFee").decimalValue()).isZero();
        ok(post("/api/orders/" + atThreshold.path("orderNumber").asText() + "/cancel")
            .with(user(customer).roles("USER")));
        assertStock(startingStock, 0);
    }

    @Test
    void loginMergesGuestCartExactlyOnceByVariant() throws Exception {
        ok(post("/api/cart/add").with(user(customer).roles("USER")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"variantId\":" + variantId + ",\"quantity\":1}"));
        Cookie guestCookie = mvc.perform(post("/api/cart/add").contentType(MediaType.APPLICATION_JSON)
            .content("{\"variantId\":" + variantId + ",\"quantity\":2}"))
            .andExpect(status().isOk()).andReturn().getResponse().getCookie("beauty_cart");
        assertThat(guestCookie).isNotNull();
        mvc.perform(get("/api/cart").with(user(customer).roles("USER")).cookie(guestCookie))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(3))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(cookie().maxAge("beauty_cart", 0));
        mvc.perform(get("/api/cart").with(user(customer).roles("USER")).cookie(guestCookie))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(3));
        mvc.perform(get("/api/cart").cookie(guestCookie))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
        assertStock(startingStock, 0);
    }

    private void setPrice(String price) {
        var variant = variants.findById(variantId).orElseThrow();
        variant.setPrice(new BigDecimal(price));
        variant.setDiscountPrice(null);
        variants.saveAndFlush(variant);
    }

    private String createUser(String prefix) {
        User member = new User();
        member.setEmail(prefix + "-" + UUID.randomUUID() + "@example.test");
        member.setName("Demo workflow");
        member.setPasswd("not-a-login-credential");
        member.setActivated(true);
        return users.saveAndFlush(member).getEmail();
    }

    private JsonNode memberCheckout(int quantity) throws Exception {
        ok(post("/api/cart/add").with(user(customer).roles("USER")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"variantId\":" + variantId + ",\"quantity\":" + quantity + "}"));
        return ok(post("/api/orders/checkout").with(user(customer).roles("USER"))
            .contentType(MediaType.APPLICATION_JSON).content(checkoutBody("COD")));
    }

    private String checkoutBody(String payment) throws Exception {
        return json.writeValueAsString(Map.of("customerName", "Demo workflow", "customerEmail", customer,
            "customerPhone", phone, "addressLine", "1 Demo Street", "ward", "Phường 1",
            "district", "Quận 1", "province", "TP Hồ Chí Minh", "paymentMethod", payment));
    }

    private String returnRequest(String number, long itemId) {
        return """
            {"orderNumber":"%s","items":[{"orderItemId":%d,"quantity":1,
             "reason":"UNOPENED_CHANGE_OF_MIND","unopenedAndSealed":true}]}
            """.formatted(number, itemId);
    }

    private void transition(long id, String status) throws Exception {
        ok(put("/api/admin/orders/" + id + "/status").with(user("admin@example.test").roles("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"));
    }

    private void returnTransition(long id, String status) throws Exception {
        ok(patch("/api/admin/returns/" + id + "/status").with(user("admin@example.test").roles("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"));
    }

    private void deliver(long id) throws Exception {
        ok(put("/api/shipper/orders/" + id + "/accept").with(user(shipper).roles("SHIPPER")));
        mvc.perform(put("/api/shipper/orders/" + id + "/complete")
            .with(user(createUser("other-shipper")).roles("SHIPPER"))).andExpect(status().isBadRequest());
        JsonNode delivered = ok(put("/api/shipper/orders/" + id + "/complete")
            .with(user(shipper).roles("SHIPPER")));
        assertThat(delivered.path("status").asText()).isEqualTo("DELIVERED");
        assertThat(delivered.path("paymentStatus").asText()).isEqualTo("COD_PAID");
    }

    private JsonNode ok(MockHttpServletRequestBuilder request) throws Exception {
        return json.readTree(mvc.perform(request).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
    }

    private void assertStock(int onHand, int reserved) {
        var batch = batches.findById(batchId).orElseThrow();
        assertThat(batch.getQuantityOnHand()).isEqualTo(onHand);
        assertThat(batch.getQuantityReserved()).isEqualTo(reserved);
    }
}
