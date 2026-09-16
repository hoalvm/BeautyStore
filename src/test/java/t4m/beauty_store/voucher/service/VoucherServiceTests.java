package t4m.beauty_store.voucher.service;

import org.junit.jupiter.api.Test;
import t4m.beauty_store.auth.entity.User;
import t4m.beauty_store.config.StoreProperties;
import t4m.beauty_store.product.entity.Brand;
import t4m.beauty_store.product.entity.Product;
import t4m.beauty_store.product.entity.ProductVariant;
import t4m.beauty_store.voucher.entity.DiscountType;
import t4m.beauty_store.voucher.entity.Voucher;
import t4m.beauty_store.voucher.repository.VoucherRepository;
import t4m.beauty_store.voucher.repository.VoucherUsageRepository;

import java.math.BigDecimal;
import t4m.beauty_store.config.StoreTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class VoucherServiceTests {

    @Test
    void lockedRedemptionRechecksPerUserLimit() {
        VoucherRepository vouchers = mock(VoucherRepository.class);
        VoucherUsageRepository usages = mock(VoucherUsageRepository.class);
        VoucherService service = new VoucherService(vouchers, usages, new StoreProperties());
        Voucher voucher = activeVoucher();
        User user = new User();
        user.setId(5L);
        user.setEmail("Customer@Example.com");
        String identifierHash = VoucherCustomerIdentity.emailHash(user.getEmail());
        when(vouchers.findByIdForUpdate(1L)).thenReturn(Optional.of(voucher));
        when(usages.countByVoucherAndUserOrIdentifierHash(
            voucher, user, identifierHash)).thenReturn(1L);

        assertThatThrownBy(() -> service.recordVoucherUsage(voucher, user, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("hết lượt");

        assertThat(voucher.getUsedQuantity()).isZero();
        verify(usages, never()).save(any());
    }

    @Test
    void fixedDiscountCannotExceedEligibleMerchandise() {
        VoucherService service = new VoucherService(
            mock(VoucherRepository.class), mock(VoucherUsageRepository.class),
            new StoreProperties());
        Voucher voucher = activeVoucher();
        voucher.setDiscountType(DiscountType.FIXED_AMOUNT);
        voucher.setDiscountValue(BigDecimal.valueOf(500_000));

        assertThat(service.calculateDiscount(voucher, BigDecimal.valueOf(120_000)))
            .isEqualByComparingTo("120000");
    }

    @Test
    void memberLimitCrossCountsUsageCreatedAsGuestWithSameNormalizedEmail() {
        VoucherRepository vouchers = mock(VoucherRepository.class);
        VoucherUsageRepository usages = mock(VoucherUsageRepository.class);
        VoucherService service = new VoucherService(vouchers, usages, new StoreProperties());
        Voucher voucher = activeVoucher();
        User user = new User();
        user.setId(7L);
        user.setEmail("  Buyer@Example.COM ");
        String identifierHash = VoucherCustomerIdentity.emailHash(user.getEmail());
        when(vouchers.findByCodeIgnoreCaseAndActiveTrue("BEAUTY10"))
            .thenReturn(Optional.of(voucher));
        when(usages.countByVoucherAndUserOrIdentifierHash(
            voucher, user, identifierHash)).thenReturn(1L);

        var response = service.validateVoucher(
            "beauty10", BigDecimal.valueOf(100_000), user, null, java.util.List.of());

        assertThat(response.isValid()).isFalse();
        assertThat(response.getMessage()).contains("hết lượt");
        verify(usages).countByVoucherAndUserOrIdentifierHash(
            voucher, user, identifierHash);
    }

    @Test
    void memberUsageStoresSameEmailHashUsedForGuestLimit() {
        VoucherRepository vouchers = mock(VoucherRepository.class);
        VoucherUsageRepository usages = mock(VoucherUsageRepository.class);
        VoucherService service = new VoucherService(vouchers, usages, new StoreProperties());
        Voucher voucher = activeVoucher();
        voucher.setLimitPerUser(null);
        User user = new User();
        user.setId(8L);
        user.setEmail("Buyer@Example.com");
        when(vouchers.findByIdForUpdate(1L)).thenReturn(Optional.of(voucher));

        service.recordVoucherUsage(voucher, user, null, null);

        verify(usages).save(argThat(usage -> user.equals(usage.getUser())
            && VoucherCustomerIdentity.emailHash(user.getEmail())
                .equals(usage.getGuestIdentifierHash())));
    }

    @Test
    void freeShippingQuoteExposesTypeAndCurrentShippingEffect() {
        VoucherRepository vouchers = mock(VoucherRepository.class);
        VoucherService service = new VoucherService(
            vouchers, mock(VoucherUsageRepository.class), new StoreProperties());
        Voucher voucher = activeVoucher();
        voucher.setLimitPerUser(null);
        voucher.setDiscountType(DiscountType.FREE_SHIPPING);
        voucher.setDiscountValue(BigDecimal.ZERO);
        when(vouchers.findByCodeIgnoreCaseAndActiveTrue("FREESHIP"))
            .thenReturn(Optional.of(voucher));

        var response = service.validateVoucher(
            "FREESHIP", BigDecimal.valueOf(200_000), null, null, java.util.List.of());

        assertThat(response.isValid()).isTrue();
        assertThat(response.getDiscountType()).isEqualTo(DiscountType.FREE_SHIPPING);
        assertThat(response.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getShippingDiscount()).isEqualByComparingTo("30000");
    }

    @Test
    void catalogScopeDiscountsOnlyMatchingServerCartLines() {
        VoucherRepository vouchers = mock(VoucherRepository.class);
        VoucherService service = new VoucherService(
            vouchers, mock(VoucherUsageRepository.class), new StoreProperties());
        Voucher voucher = activeVoucher();
        voucher.setLimitPerUser(null);
        Brand eligibleBrand = Brand.builder().id(21L).active(true).build();
        voucher.getBrands().add(eligibleBrand);
        Product eligibleProduct = Product.builder()
            .id(31L).brandEntity(eligibleBrand).active(true).build();
        Product otherProduct = Product.builder().id(32L).active(true).build();
        ProductVariant eligibleVariant = ProductVariant.builder()
            .id(41L).product(eligibleProduct).active(true).build();
        ProductVariant otherVariant = ProductVariant.builder()
            .id(42L).product(otherProduct).active(true).build();
        when(vouchers.findByCodeIgnoreCaseAndActiveTrue("BEAUTY10"))
            .thenReturn(Optional.of(voucher));

        var response = service.validateVoucher(
            "BEAUTY10", new BigDecimal("300000"), null, null, java.util.List.of(
                new VoucherService.VoucherLine(
                    eligibleProduct, eligibleVariant, new BigDecimal("100000")),
                new VoucherService.VoucherLine(
                    otherProduct, otherVariant, new BigDecimal("200000"))));

        assertThat(response.isValid()).isTrue();
        assertThat(response.getDiscountAmount()).isEqualByComparingTo("10000");
    }

    private static Voucher activeVoucher() {
        Voucher voucher = new Voucher();
        voucher.setId(1L);
        voucher.setCode("BEAUTY10");
        voucher.setDiscountType(DiscountType.PERCENTAGE);
        voucher.setDiscountValue(BigDecimal.TEN);
        voucher.setTotalQuantity(10);
        voucher.setUsedQuantity(0);
        voucher.setLimitPerUser(1);
        voucher.setStartDate(StoreTime.now().minusDays(1));
        voucher.setEndDate(StoreTime.now().plusDays(1));
        voucher.setActive(true);
        return voucher;
    }
}
