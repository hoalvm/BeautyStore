package t4m.beauty_store.voucher.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import t4m.beauty_store.auth.entity.User;
import t4m.beauty_store.config.StoreProperties;
import t4m.beauty_store.config.StoreTime;
import t4m.beauty_store.order.entity.Order;
import t4m.beauty_store.product.entity.Category;
import t4m.beauty_store.product.entity.Product;
import t4m.beauty_store.product.entity.ProductVariant;
import t4m.beauty_store.voucher.dto.VoucherValidationResponse;
import t4m.beauty_store.voucher.entity.DiscountType;
import t4m.beauty_store.voucher.entity.Voucher;
import t4m.beauty_store.voucher.entity.VoucherUsage;
import t4m.beauty_store.voucher.repository.VoucherRepository;
import t4m.beauty_store.voucher.repository.VoucherUsageRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class VoucherService {
    private final VoucherRepository voucherRepository;
    private final VoucherUsageRepository voucherUsageRepository;
    private final StoreProperties storeProperties;

    public record VoucherLine(Product product, ProductVariant variant, BigDecimal lineTotal) {}

    public record CheckoutVoucher(Voucher voucher, VoucherValidationResponse validation) {}

    @Transactional(readOnly = true)
    public VoucherValidationResponse validateVoucher(String code, BigDecimal orderTotal, User user) {
        return validateVoucher(code, orderTotal, user, null);
    }

    @Transactional(readOnly = true)
    public VoucherValidationResponse validateVoucher(
            String code, BigDecimal orderTotal, User user, String guestIdentifierHash) {
        return validateVoucher(code, orderTotal, user, guestIdentifierHash, List.of());
    }

    @Transactional(readOnly = true)
    public VoucherValidationResponse validateVoucher(
            String code, BigDecimal orderTotal, User user, String guestIdentifierHash,
            List<VoucherLine> lines) {
        String normalizedCode = normalizeCode(code);
        if (normalizedCode == null || orderTotal == null || orderTotal.signum() <= 0) {
            return invalid("Mã giảm giá hoặc giá trị đơn hàng không hợp lệ");
        }
        Voucher voucher = voucherRepository.findByCodeIgnoreCaseAndActiveTrue(normalizedCode)
            .orElse(null);
        return validateLoaded(voucher, orderTotal, user, guestIdentifierHash,
            safeLines(lines), false);
    }

    /**
     * Checkout validation uses a row lock. The lock remains held by the outer
     * order transaction, preventing quantity and per-customer limit races.
     */
    @Transactional
    public CheckoutVoucher validateVoucherForCheckout(
            String code, BigDecimal orderTotal, User user, String guestIdentifierHash,
            List<VoucherLine> lines) {
        String normalizedCode = normalizeCode(code);
        if (normalizedCode == null || orderTotal == null || orderTotal.signum() <= 0) {
            return new CheckoutVoucher(null,
                invalid("Mã giảm giá hoặc giá trị đơn hàng không hợp lệ"));
        }
        Voucher voucher = voucherRepository.findByCodeIgnoreCaseForUpdate(normalizedCode)
            .orElse(null);
        VoucherValidationResponse validation = validateLoaded(
            voucher, orderTotal, user, guestIdentifierHash, safeLines(lines), true);
        return new CheckoutVoucher(voucher, validation);
    }

    private VoucherValidationResponse validateLoaded(
            Voucher voucher, BigDecimal orderTotal, User user, String guestIdentifierHash,
            List<VoucherLine> lines, boolean requireGuestIdentity) {
        if (voucher == null || !Boolean.TRUE.equals(voucher.getActive())) {
            return invalid("Mã giảm giá không tồn tại hoặc đã bị vô hiệu hóa");
        }

        var now = StoreTime.now();
        if (voucher.getStartDate() == null || now.isBefore(voucher.getStartDate())) {
            return invalid("Mã giảm giá chưa đến thời gian áp dụng");
        }
        if (voucher.getEndDate() == null || now.isAfter(voucher.getEndDate())) {
            return invalid("Mã giảm giá đã hết hạn");
        }
        if (!voucher.hasAvailableQuantity()) {
            return invalid("Mã giảm giá đã hết lượt sử dụng");
        }
        if (voucher.getMinOrderValue() != null
                && orderTotal.compareTo(voucher.getMinOrderValue()) < 0) {
            return invalid(String.format(
                "Đơn hàng tối thiểu %,.0fđ để áp dụng mã này", voucher.getMinOrderValue()));
        }

        VoucherValidationResponse identityLimit = validateIdentityLimit(
            voucher, user, guestIdentifierHash, requireGuestIdentity);
        if (identityLimit != null) return identityLimit;
        if (!isAllowedUserGroup(voucher, user)) {
            return invalid("Mã giảm giá không áp dụng cho nhóm khách hàng này");
        }

        BigDecimal eligibleTotal = eligibleTotal(voucher, orderTotal, lines);
        if (hasCatalogScope(voucher) && eligibleTotal.signum() == 0) {
            return invalid("Giỏ hàng không có sản phẩm phù hợp với mã giảm giá");
        }

        return new VoucherValidationResponse(true,
            "Áp dụng mã giảm giá thành công!",
            calculateDiscount(voucher, eligibleTotal), voucher.getCode(),
            voucher.getDiscountType(), quotedShippingDiscount(voucher, orderTotal));
    }

    private VoucherValidationResponse validateIdentityLimit(
            Voucher voucher, User user, String guestIdentifierHash,
            boolean requireGuestIdentity) {
        Integer limit = voucher.getLimitPerUser();
        if (limit == null) return null;
        String identifierHash = user == null
            ? VoucherCustomerIdentity.normalizeHash(guestIdentifierHash)
            : VoucherCustomerIdentity.emailHash(user.getEmail());
        if (user != null) {
            if (identifierHash == null) {
                return invalid("Không xác định được khách hàng để áp dụng mã giảm giá");
            }
            if (voucherUsageRepository.countByVoucherAndUserOrIdentifierHash(
                    voucher, user, identifierHash) >= limit) {
                return invalid("Bạn đã sử dụng hết lượt áp dụng mã giảm giá này");
            }
            return null;
        }
        if (identifierHash == null) {
            return requireGuestIdentity
                ? invalid("Không xác định được khách hàng để áp dụng mã giảm giá")
                : null;
        }
        if (voucherUsageRepository.countByVoucherAndGuestIdentifierHash(
                voucher, identifierHash) >= limit) {
            return invalid("Bạn đã sử dụng hết lượt áp dụng mã giảm giá này");
        }
        return null;
    }

    public BigDecimal calculateDiscount(Voucher voucher, BigDecimal eligibleTotal) {
        if (voucher == null || eligibleTotal == null || eligibleTotal.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal value = voucher.getDiscountValue() == null
            ? BigDecimal.ZERO : voucher.getDiscountValue();
        BigDecimal discount = switch (voucher.getDiscountType()) {
            case PERCENTAGE -> eligibleTotal.multiply(value)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            case FIXED_AMOUNT -> value.min(eligibleTotal);
            case FREE_SHIPPING -> BigDecimal.ZERO;
        };
        if (voucher.getDiscountType() == DiscountType.PERCENTAGE
                && voucher.getMaxDiscount() != null) {
            discount = discount.min(voucher.getMaxDiscount());
        }
        return discount.max(BigDecimal.ZERO).setScale(0, RoundingMode.HALF_UP);
    }

    @Transactional
    public void recordVoucherUsage(Voucher voucher, User user) {
        recordVoucherUsage(voucher, user, null, null);
    }

    @Transactional
    public void recordVoucherUsage(
            Voucher voucher, User user, String guestIdentifierHash, Order order) {
        if (voucher == null) return;
        if (order != null && !voucherUsageRepository.findByOrderId(order.getId()).isEmpty()) {
            return;
        }
        Voucher locked = voucherRepository.findByIdForUpdate(voucher.getId())
            .orElseThrow(() -> new IllegalArgumentException("Mã giảm giá không tồn tại"));
        if (!locked.isCurrentlyValid()) {
            throw new IllegalArgumentException("Mã giảm giá đã hết lượt hoặc hết hạn");
        }
        VoucherValidationResponse identityLimit = validateIdentityLimit(
            locked, user, guestIdentifierHash, true);
        if (identityLimit != null) {
            throw new IllegalArgumentException(identityLimit.getMessage());
        }

        locked.setUsedQuantity(locked.getUsedQuantity() + 1);
        voucherRepository.save(locked);

        VoucherUsage usage = new VoucherUsage();
        usage.setVoucher(locked);
        usage.setUser(user);
        usage.setGuestIdentifierHash(user == null
            ? VoucherCustomerIdentity.normalizeHash(guestIdentifierHash)
            : VoucherCustomerIdentity.emailHash(user.getEmail()));
        usage.setOrder(order);
        usage.setUsedAt(StoreTime.now());
        voucherUsageRepository.save(usage);
    }

    @Transactional
    public void restoreVoucherUsage(Voucher voucher, User user) {
        if (voucher == null) return;
        Voucher locked = voucherRepository.findByIdForUpdate(voucher.getId()).orElse(null);
        if (locked == null) return;
        List<VoucherUsage> usages =
            voucherUsageRepository.findTopByVoucherAndUserOrderByUsedAtDesc(locked, user);
        if (usages.isEmpty()) return;
        voucherUsageRepository.delete(usages.getFirst());
        locked.setUsedQuantity(Math.max(0, locked.getUsedQuantity() - 1));
        voucherRepository.save(locked);
    }

    @Transactional
    public void restoreVoucherUsageForOrder(Voucher voucher, Order order) {
        restoreVoucherUsageForOrder(order);
    }

    /** Idempotently restores usage even when the voucher was disabled later. */
    @Transactional
    public void restoreVoucherUsageForOrder(Order order) {
        if (order == null || order.getId() == null) return;
        List<VoucherUsage> usages = voucherUsageRepository.findByOrderId(order.getId());
        if (usages.isEmpty()) return;

        Map<Long, List<VoucherUsage>> byVoucher = new LinkedHashMap<>();
        usages.stream()
            .sorted(Comparator.comparing(usage -> usage.getVoucher().getId()))
            .forEach(usage -> byVoucher
                .computeIfAbsent(usage.getVoucher().getId(), ignored -> new ArrayList<>())
                .add(usage));
        for (Map.Entry<Long, List<VoucherUsage>> entry : byVoucher.entrySet()) {
            Voucher locked = voucherRepository.findByIdForUpdate(entry.getKey()).orElse(null);
            voucherUsageRepository.deleteAll(entry.getValue());
            if (locked != null) {
                locked.setUsedQuantity(Math.max(0,
                    locked.getUsedQuantity() - entry.getValue().size()));
                voucherRepository.save(locked);
            }
        }
    }

    @Transactional(readOnly = true)
    public Voucher getVoucherByCode(String code) {
        String normalized = normalizeCode(code);
        return normalized == null ? null
            : voucherRepository.findByCodeIgnoreCaseAndActiveTrue(normalized).orElse(null);
    }

    private static VoucherValidationResponse invalid(String message) {
        return new VoucherValidationResponse(false, message, null, null, null, null);
    }

    private BigDecimal quotedShippingDiscount(Voucher voucher, BigDecimal subtotal) {
        if (voucher.getDiscountType() != DiscountType.FREE_SHIPPING) {
            return BigDecimal.ZERO;
        }
        BigDecimal shippingFee = storeProperties.getShippingFee();
        BigDecimal freeShippingThreshold = storeProperties.getFreeShippingThreshold();
        if (shippingFee == null || shippingFee.signum() <= 0) return BigDecimal.ZERO;
        if (freeShippingThreshold != null && freeShippingThreshold.signum() >= 0
                && subtotal.compareTo(freeShippingThreshold) >= 0) {
            return BigDecimal.ZERO;
        }
        return shippingFee.setScale(0, RoundingMode.HALF_UP);
    }

    private static List<VoucherLine> safeLines(List<VoucherLine> lines) {
        return lines == null ? List.of() : lines;
    }

    private static String normalizeCode(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean hasCatalogScope(Voucher voucher) {
        return !voucher.getBrands().isEmpty() || !voucher.getCategories().isEmpty()
            || !voucher.getProducts().isEmpty() || !voucher.getVariants().isEmpty();
    }

    private static BigDecimal eligibleTotal(
            Voucher voucher, BigDecimal orderTotal, List<VoucherLine> lines) {
        if (!hasCatalogScope(voucher)) return orderTotal;
        return lines.stream()
            .filter(Objects::nonNull)
            .filter(line -> matches(voucher, line))
            .map(VoucherLine::lineTotal)
            .filter(Objects::nonNull)
            .filter(total -> total.signum() > 0)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static boolean matches(Voucher voucher, VoucherLine line) {
        Product product = line.product();
        ProductVariant variant = line.variant();
        if (product == null) return false;
        if (variant != null && voucher.getVariants().stream()
                .anyMatch(value -> Objects.equals(value.getId(), variant.getId()))) return true;
        if (voucher.getProducts().stream()
                .anyMatch(value -> Objects.equals(value.getId(), product.getId()))) return true;
        if (product.getBrandEntity() != null && voucher.getBrands().stream()
                .anyMatch(value -> Objects.equals(value.getId(), product.getBrandEntity().getId()))) {
            return true;
        }
        Category current = product.getCategory();
        int depth = 0;
        while (current != null && depth++ < 32) {
            Long categoryId = current.getId();
            if (voucher.getCategories().stream()
                    .anyMatch(value -> Objects.equals(value.getId(), categoryId))) return true;
            current = current.getParent();
        }
        return false;
    }

    private static boolean isAllowedUserGroup(Voucher voucher, User user) {
        String groups = voucher.getApplicableUserGroups();
        if (groups == null || groups.isBlank()) return true;
        List<String> allowed = java.util.Arrays.stream(groups.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .map(value -> value.toUpperCase(Locale.ROOT))
            .toList();
        if (user == null) return allowed.contains("GUEST");
        return user.getAuthorities().stream()
            .map(authority -> authority.getAuthority().toUpperCase(Locale.ROOT))
            .anyMatch(authority -> allowed.contains(authority)
                || allowed.contains(authority.replaceFirst("^ROLE_", "")));
    }
}
