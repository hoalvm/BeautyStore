package t4m.beauty_store.voucher.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import t4m.beauty_store.product.repository.BrandRepository;
import t4m.beauty_store.product.repository.CategoryRepository;
import t4m.beauty_store.product.repository.ProductRepository;
import t4m.beauty_store.product.repository.ProductVariantRepository;
import t4m.beauty_store.voucher.dto.VoucherRequest;
import t4m.beauty_store.voucher.dto.VoucherResponse;
import t4m.beauty_store.voucher.entity.DiscountType;
import t4m.beauty_store.voucher.entity.Voucher;
import t4m.beauty_store.voucher.repository.VoucherRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminVoucherServiceTests {

    private VoucherRepository voucherRepository;
    private AdminVoucherService service;

    @BeforeEach
    void setUp() {
        voucherRepository = mock(VoucherRepository.class);
        BrandRepository brandRepository = mock(BrandRepository.class);
        CategoryRepository categoryRepository = mock(CategoryRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        ProductVariantRepository productVariantRepository = mock(ProductVariantRepository.class);
        service = new AdminVoucherService(
            voucherRepository,
            brandRepository,
            categoryRepository,
            productRepository,
            productVariantRepository);

        when(brandRepository.findAllById(any())).thenReturn(List.of());
        when(categoryRepository.findAllById(any())).thenReturn(List.of());
        when(productRepository.findAllById(any())).thenReturn(List.of());
        when(productVariantRepository.findAllById(any())).thenReturn(List.of());
    }

    @Test
    void updateAcquiresCheckoutLockAndPreservesLatestUsedQuantity() {
        Voucher voucher = voucher(4);
        VoucherRequest request = request(12);
        when(voucherRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(voucher));
        when(voucherRepository.save(voucher)).thenReturn(voucher);

        VoucherResponse response = service.updateVoucher(1L, request);

        assertThat(voucher.getUsedQuantity()).isEqualTo(4);
        assertThat(voucher.getTotalQuantity()).isEqualTo(12);
        assertThat(response.getUsedQuantity()).isEqualTo(4);
        verify(voucherRepository).findByIdForUpdate(1L);
        verify(voucherRepository, never()).findById(1L);
    }

    @Test
    void updateValidatesTotalAgainstLatestLockedUsage() {
        Voucher voucher = voucher(6);
        when(voucherRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(voucher));

        assertThatThrownBy(() -> service.updateVoucher(1L, request(5)))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(voucher.getUsedQuantity()).isEqualTo(6);
        verify(voucherRepository).findByIdForUpdate(1L);
        verify(voucherRepository, never()).save(any());
    }

    @Test
    void toggleAcquiresCheckoutLock() {
        Voucher voucher = voucher(2);
        when(voucherRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(voucher));
        when(voucherRepository.save(voucher)).thenReturn(voucher);

        VoucherResponse response = service.toggleVoucherStatus(1L);

        assertThat(response.getActive()).isFalse();
        assertThat(voucher.getUsedQuantity()).isEqualTo(2);
        verify(voucherRepository).findByIdForUpdate(1L);
        verify(voucherRepository, never()).findById(1L);
    }

    @Test
    void deleteAcquiresCheckoutLockBeforeCheckingUsage() {
        Voucher voucher = voucher(0);
        when(voucherRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(voucher));

        service.deleteVoucher(1L);

        verify(voucherRepository).findByIdForUpdate(1L);
        verify(voucherRepository).delete(voucher);
        verify(voucherRepository, never()).findById(1L);
    }

    private static Voucher voucher(int usedQuantity) {
        Voucher voucher = new Voucher();
        voucher.setId(1L);
        voucher.setCode("BEAUTY10");
        voucher.setDescription("Voucher test");
        voucher.setDiscountType(DiscountType.FIXED_AMOUNT);
        voucher.setDiscountValue(BigDecimal.TEN);
        voucher.setTotalQuantity(10);
        voucher.setUsedQuantity(usedQuantity);
        voucher.setStartDate(LocalDateTime.now().minusDays(1));
        voucher.setEndDate(LocalDateTime.now().plusDays(1));
        voucher.setActive(true);
        return voucher;
    }

    private static VoucherRequest request(int totalQuantity) {
        VoucherRequest request = new VoucherRequest();
        request.setCode("BEAUTY10");
        request.setDescription("Voucher updated");
        request.setDiscountType(DiscountType.FIXED_AMOUNT);
        request.setDiscountValue(BigDecimal.valueOf(20));
        request.setTotalQuantity(totalQuantity);
        request.setStartDate(LocalDateTime.now().minusHours(1));
        request.setEndDate(LocalDateTime.now().plusDays(2));
        request.setActive(true);
        return request;
    }
}
