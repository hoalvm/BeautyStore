package t4m.beauty_store.voucher.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import t4m.beauty_store.auth.entity.User;
import t4m.beauty_store.auth.repository.UserRepository;
import t4m.beauty_store.cart.service.CartIdentity;
import t4m.beauty_store.cart.service.CartService;
import t4m.beauty_store.cart.service.GuestCartCookieService;
import t4m.beauty_store.product.entity.Product;
import t4m.beauty_store.product.entity.ProductVariant;
import t4m.beauty_store.voucher.dto.VoucherValidationResponse;
import t4m.beauty_store.voucher.entity.DiscountType;
import t4m.beauty_store.voucher.service.VoucherService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VoucherControllerTests {
    private VoucherService voucherService;
    private CartService cartService;
    private GuestCartCookieService guestCookieService;
    private UserRepository userRepository;
    private VoucherController controller;

    @BeforeEach
    void setUp() {
        voucherService = mock(VoucherService.class);
        cartService = mock(CartService.class);
        guestCookieService = mock(GuestCartCookieService.class);
        userRepository = mock(UserRepository.class);
        controller = new VoucherController(
            voucherService, cartService, guestCookieService, userRepository);
    }

    @Test
    void ignoresUntrustedOrderTotalAndUsesGuestServerCartSnapshot() throws Exception {
        String guestHash = "a".repeat(64);
        var snapshot = snapshot(new BigDecimal("185000"));
        when(guestCookieService.resolveExistingTokenHash(any())).thenReturn(guestHash);
        when(cartService.getVoucherCartSnapshot(new CartIdentity(null, guestHash)))
            .thenReturn(snapshot);
        when(voucherService.validateVoucher(
                eq("BEAUTY10"), eq(new BigDecimal("185000")), isNull(), isNull(), any()))
            .thenReturn(new VoucherValidationResponse(
                true, "OK", new BigDecimal("18500"), "BEAUTY10",
                DiscountType.PERCENTAGE, BigDecimal.ZERO));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();

        mockMvc.perform(post("/api/vouchers/validate")
                .param("code", "BEAUTY10")
                .param("orderTotal", "1e2147483647"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.discountAmount").value(18500));

        verify(cartService).getVoucherCartSnapshot(new CartIdentity(null, guestHash));
        verify(voucherService).validateVoucher(
            eq("BEAUTY10"), eq(new BigDecimal("185000")), isNull(), isNull(),
            argThat(lines -> lines.size() == 1
                && lines.getFirst().lineTotal().compareTo(new BigDecimal("185000")) == 0
                && lines.getFirst().product().getId().equals(11L)
                && lines.getFirst().variant().getId().equals(12L)));
    }

    @Test
    void authenticatedPreviewUsesAccountCartAndServerPricedLines() {
        User user = new User();
        user.setId(9L);
        user.setEmail("buyer@example.com");
        var snapshot = snapshot(new BigDecimal("240000"));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(cartService.getVoucherCartSnapshot(new CartIdentity(user.getEmail(), null)))
            .thenReturn(snapshot);
        when(voucherService.validateVoucher(
                eq("BRAND20"), eq(new BigDecimal("240000")), eq(user), isNull(), any()))
            .thenReturn(new VoucherValidationResponse(
                true, "OK", new BigDecimal("48000"), "BRAND20",
                DiscountType.PERCENTAGE, BigDecimal.ZERO));

        controller.validateVoucher("BRAND20", user, new MockHttpServletRequest());

        verify(cartService).getVoucherCartSnapshot(new CartIdentity(user.getEmail(), null));
        verify(voucherService).validateVoucher(
            eq("BRAND20"), eq(new BigDecimal("240000")), eq(user), isNull(), any());
    }

    private static CartService.VoucherCartSnapshot snapshot(BigDecimal subtotal) {
        Product product = new Product();
        product.setId(11L);
        ProductVariant variant = new ProductVariant();
        variant.setId(12L);
        variant.setProduct(product);
        return new CartService.VoucherCartSnapshot(subtotal, List.of(
            new CartService.VoucherCartLine(product, variant, subtotal)));
    }
}
