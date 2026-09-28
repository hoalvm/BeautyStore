package t4m.beauty_store.voucher.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import t4m.beauty_store.config.StoreTime;
import t4m.beauty_store.voucher.dto.VoucherRequest;
import t4m.beauty_store.voucher.dto.VoucherResponse;
import t4m.beauty_store.voucher.dto.VoucherStatsResponse;
import t4m.beauty_store.voucher.entity.DiscountType;
import t4m.beauty_store.voucher.entity.Voucher;
import t4m.beauty_store.voucher.repository.VoucherRepository;
import t4m.beauty_store.product.repository.BrandRepository;
import t4m.beauty_store.product.repository.CategoryRepository;
import t4m.beauty_store.product.repository.ProductRepository;
import t4m.beauty_store.product.repository.ProductVariantRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.security.SecureRandom;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminVoucherService {
    
    private final VoucherRepository voucherRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public VoucherResponse createVoucher(VoucherRequest request) {
        validateRequest(request, 0);
        // Check if code already exists
        if (voucherRepository.findByCodeIgnoreCase(request.getCode()).isPresent()) {
            throw new IllegalArgumentException("Mã voucher đã tồn tại");
        }

        Voucher voucher = new Voucher();
        voucher.setCode(request.getCode().toUpperCase());
        voucher.setDescription(request.getDescription());
        voucher.setDiscountType(request.getDiscountType());
        voucher.setDiscountValue(request.getDiscountValue());
        voucher.setMaxDiscount(request.getMaxDiscount());
        voucher.setMinOrderValue(request.getMinOrderValue());
        voucher.setTotalQuantity(request.getTotalQuantity());
        voucher.setUsedQuantity(0);
        voucher.setLimitPerUser(request.getLimitPerUser());
        voucher.setStartDate(request.getStartDate());
        voucher.setEndDate(request.getEndDate());
        voucher.setActive(request.getActive() != null ? request.getActive() : true);
        voucher.setApplicableUserGroups(request.getApplicableUserGroups());
        applyScopes(voucher, request);

        Voucher savedVoucher = voucherRepository.save(voucher);
        return VoucherResponse.fromEntity(savedVoucher);
    }

    @Transactional
    public VoucherResponse updateVoucher(Long id, VoucherRequest request) {
        Voucher voucher = voucherRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy voucher"));

        validateRequest(request, voucher.getUsedQuantity());
        // Check if code is being changed and if new code already exists
        if (!voucher.getCode().equalsIgnoreCase(request.getCode())) {
            if (voucherRepository.findByCodeIgnoreCase(request.getCode()).isPresent()) {
                throw new IllegalArgumentException("Mã voucher đã tồn tại");
            }
            voucher.setCode(request.getCode().toUpperCase());
        }

        voucher.setDescription(request.getDescription());
        voucher.setDiscountType(request.getDiscountType());
        voucher.setDiscountValue(request.getDiscountValue());
        voucher.setMaxDiscount(request.getMaxDiscount());
        voucher.setMinOrderValue(request.getMinOrderValue());
        voucher.setTotalQuantity(request.getTotalQuantity());
        voucher.setLimitPerUser(request.getLimitPerUser());
        voucher.setStartDate(request.getStartDate());
        voucher.setEndDate(request.getEndDate());
        voucher.setActive(request.getActive() == null ? voucher.getActive() : request.getActive());
        voucher.setApplicableUserGroups(request.getApplicableUserGroups());
        applyScopes(voucher, request);

        Voucher updatedVoucher = voucherRepository.save(voucher);
        return VoucherResponse.fromEntity(updatedVoucher);
    }

    @Transactional
    public void deleteVoucher(Long id) {
        Voucher voucher = voucherRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy voucher"));
        
        // Check if voucher has been used
        if (voucher.getUsedQuantity() > 0) {
            throw new IllegalArgumentException("Không thể xóa voucher đã được sử dụng");
        }
        
        voucherRepository.delete(voucher);
    }

    @Transactional(readOnly = true)
    public Page<VoucherResponse> getAllVouchers(
            String code,
            String discountType,
            Boolean active,
            String status,
            int page,
            int size,
            String sortBy,
            String sortDir
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Phân trang không hợp lệ");
        }
        String sortProperty = switch (sortBy == null ? "" : sortBy) {
            case "code" -> "code";
            case "discountType" -> "discountType";
            case "startDate" -> "startDate";
            case "endDate" -> "endDate";
            case "totalQuantity" -> "totalQuantity";
            case "usedQuantity" -> "usedQuantity";
            case "active" -> "active";
            default -> "createdAt";
        };
        Sort sort = "asc".equalsIgnoreCase(sortDir)
            ? Sort.by(sortProperty).ascending()
            : Sort.by(sortProperty).descending();
        
        Pageable pageable = PageRequest.of(page, size, sort);
        
        DiscountType type = null;
        if (discountType != null && !discountType.isEmpty()) {
            try {
                type = DiscountType.valueOf(discountType);
            } catch (IllegalArgumentException e) {
                // Ignore invalid discount type
            }
        }
        
        Page<Voucher> voucherPage = voucherRepository.findAllWithFilters(
            code,
            type,
            active,
            status,
            StoreTime.now(),
            pageable
        );
        
        return voucherPage.map(VoucherResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public VoucherResponse getVoucherById(Long id) {
        Voucher voucher = voucherRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy voucher"));
        return VoucherResponse.fromEntity(voucher);
    }

    @Transactional
    public VoucherResponse toggleVoucherStatus(Long id) {
        Voucher voucher = voucherRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy voucher"));
        
        voucher.setActive(!voucher.getActive());
        Voucher updatedVoucher = voucherRepository.save(voucher);
        return VoucherResponse.fromEntity(updatedVoucher);
    }

    @Transactional(readOnly = true)
    public VoucherStatsResponse getStatistics() {
        LocalDateTime now = StoreTime.now();
        
        long total = voucherRepository.count();
        long active = voucherRepository.countActiveVouchers(now);
        long upcoming = voucherRepository.countUpcomingVouchers(now);
        long expired = voucherRepository.countExpiredVouchers(now);
        long totalUsage = voucherRepository.sumTotalUsage();
        
        return new VoucherStatsResponse(total, active, expired, upcoming, totalUsage);
    }

    public String generateRandomCode(int length) {
        if (length < 6 || length > 32) {
            throw new IllegalArgumentException("Độ dài mã voucher phải từ 6 đến 32 ký tự");
        }
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        return code.toString();
    }

    private static void validateRequest(VoucherRequest request, int usedQuantity) {
        if (request.getEndDate() == null || request.getStartDate() == null
                || !request.getEndDate().isAfter(request.getStartDate())) {
            throw new IllegalArgumentException("Ngày kết thúc phải sau ngày bắt đầu");
        }
        if (request.getTotalQuantity() == null || request.getTotalQuantity() < usedQuantity) {
            throw new IllegalArgumentException(
                "Tổng số lượng voucher không được nhỏ hơn số lượt đã sử dụng");
        }
        if (request.getDiscountType() == DiscountType.PERCENTAGE
                && request.getDiscountValue() != null
                && request.getDiscountValue().compareTo(java.math.BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Mức giảm phần trăm không được vượt quá 100%");
        }
        if (request.getDiscountType() != DiscountType.FREE_SHIPPING
                && (request.getDiscountValue() == null
                    || request.getDiscountValue().signum() <= 0)) {
            throw new IllegalArgumentException("Giá trị giảm giá phải lớn hơn 0");
        }
    }

    private void applyScopes(Voucher voucher, VoucherRequest request) {
        Set<Long> brandIds = ids(request.getBrandIds());
        Set<Long> categoryIds = ids(request.getCategoryIds());
        Set<Long> productIds = ids(request.getProductIds());
        Set<Long> variantIds = ids(request.getVariantIds());
        voucher.setBrands(new LinkedHashSet<>(loadAll(
            brandRepository.findAllById(brandIds), brandIds, "thương hiệu")));
        voucher.setCategories(new LinkedHashSet<>(loadAll(
            categoryRepository.findAllById(categoryIds), categoryIds, "danh mục")));
        voucher.setProducts(new LinkedHashSet<>(loadAll(
            productRepository.findAllById(productIds), productIds, "sản phẩm")));
        voucher.setVariants(new LinkedHashSet<>(loadAll(
            productVariantRepository.findAllById(variantIds), variantIds, "biến thể")));
    }

    private static Set<Long> ids(Set<Long> values) {
        return values == null ? Set.of() : new LinkedHashSet<>(values);
    }

    private static <T> List<T> loadAll(Iterable<T> values, Set<Long> requestedIds, String label) {
        List<T> result = new ArrayList<>();
        values.forEach(result::add);
        if (result.size() != new HashSet<>(requestedIds).size()) {
            throw new IllegalArgumentException("Có " + label + " không tồn tại");
        }
        return result;
    }
}
