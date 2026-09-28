package t4m.beauty_store.order.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import t4m.beauty_store.auth.entity.User;
import t4m.beauty_store.auth.exception.UserNotFoundException;
import t4m.beauty_store.auth.repository.UserRepository;
import t4m.beauty_store.cart.entity.Cart;
import t4m.beauty_store.cart.entity.CartItem;
import t4m.beauty_store.cart.repository.CartRepository;
import t4m.beauty_store.cart.service.CartIdentity;
import t4m.beauty_store.config.StoreProperties;
import t4m.beauty_store.config.StoreTime;
import t4m.beauty_store.order.dto.CheckoutRequest;
import t4m.beauty_store.order.dto.OrderResponse;
import t4m.beauty_store.order.entity.*;
import t4m.beauty_store.order.repository.OrderRepository;
import t4m.beauty_store.payment.dto.VNPayPaymentOutcome;
import t4m.beauty_store.payment.dto.VNPayPaymentResult;
import t4m.beauty_store.product.entity.InventoryBatch;
import t4m.beauty_store.product.entity.Product;
import t4m.beauty_store.product.entity.ProductImage;
import t4m.beauty_store.product.entity.ProductVariant;
import t4m.beauty_store.product.repository.InventoryBatchRepository;
import t4m.beauty_store.product.service.InventoryService;
import t4m.beauty_store.voucher.entity.DiscountType;
import t4m.beauty_store.voucher.entity.Voucher;
import t4m.beauty_store.voucher.service.VoucherCustomerIdentity;
import t4m.beauty_store.voucher.service.VoucherService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final InventoryBatchRepository batchRepository;
    private final InventoryService inventoryService;
    private final VoucherService voucherService;
    private final StoreProperties storeProperties;

    @Transactional
    public OrderResponse createOrder(String userEmail, CheckoutRequest request) {
        return createOrder(new CartIdentity(userEmail, null), request);
    }

    @Transactional
    public OrderResponse createOrder(CartIdentity identity, CheckoutRequest request) {
        if (identity == null || request == null || (!identity.authenticated()
                && (identity.guestTokenHash() == null || identity.guestTokenHash().isBlank()))) {
            throw new IllegalArgumentException("Không xác định được thông tin checkout");
        }
        User user = identity.authenticated()
            ? userRepository.findByEmail(identity.userEmail())
                .orElseThrow(() -> new UserNotFoundException("Không tìm thấy tài khoản"))
            : null;
        Cart cart = user != null
            ? cartRepository.findByUserIdForUpdate(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Giỏ hàng trống"))
            : cartRepository.findByGuestTokenHashForUpdate(identity.guestTokenHash())
                .orElseThrow(() -> new IllegalArgumentException("Giỏ hàng trống"));

        List<CartItem> sellableItems = cart.getCartItems().stream()
            .filter(this::isSellableCartItem)
            .sorted(Comparator.comparing(item -> item.getVariant().getId()))
            .toList();
        if (sellableItems.isEmpty()) throw new IllegalArgumentException("Giỏ hàng không có sản phẩm khả dụng");

        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem item : sellableItems) {
            BigDecimal currentPrice = effectivePrice(item.getProduct(), item.getVariant());
            if (currentPrice == null || currentPrice.signum() < 0) {
                throw new IllegalStateException("Giá sản phẩm không hợp lệ");
            }
            item.setPrice(currentPrice);
            subtotal = subtotal.add(currentPrice.multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        BigDecimal shippingFee = subtotal.compareTo(nonNegative(
                storeProperties.getFreeShippingThreshold(), "Ngưỡng miễn phí vận chuyển")) >= 0
            ? BigDecimal.ZERO
            : nonNegative(storeProperties.getShippingFee(), "Phí vận chuyển");
        BigDecimal voucherDiscount = BigDecimal.ZERO;
        Voucher voucher = null;
        String guestVoucherIdentifier = user == null
            ? VoucherCustomerIdentity.emailHash(request.getCustomerEmail()) : null;
        if (request.getVoucherCode() != null && !request.getVoucherCode().isBlank()) {
            List<VoucherService.VoucherLine> voucherLines = sellableItems.stream()
                .map(item -> new VoucherService.VoucherLine(
                    item.getProduct(), item.getVariant(),
                    item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()))))
                .toList();
            VoucherService.CheckoutVoucher checkoutVoucher =
                voucherService.validateVoucherForCheckout(
                    request.getVoucherCode(), subtotal, user,
                    guestVoucherIdentifier, voucherLines);
            var validation = checkoutVoucher.validation();
            if (!validation.isValid()) throw new IllegalArgumentException(validation.getMessage());
            voucher = checkoutVoucher.voucher();
            if (voucher == null) throw new IllegalArgumentException("Mã giảm giá không hợp lệ");
            if (voucher.getDiscountType() == DiscountType.FREE_SHIPPING) {
                shippingFee = BigDecimal.ZERO;
            } else {
                voucherDiscount = Optional.ofNullable(validation.getDiscountAmount()).orElse(BigDecimal.ZERO);
            }
        }

        String paymentMethod = normalizePaymentMethod(request.getPaymentMethod());
        boolean online = "VNPAY".equals(paymentMethod);
        BigDecimal total = subtotal.subtract(voucherDiscount).max(BigDecimal.ZERO).add(shippingFee);
        Order order = Order.builder()
            .user(user)
            .customerName(request.getCustomerName().trim())
            .customerEmail(request.getCustomerEmail().trim().toLowerCase(Locale.ROOT))
            .customerPhone(request.getCustomerPhone().trim())
            .shippingAddress(request.resolvedShippingAddress())
            .shippingAddressLine(clean(request.getAddressLine()))
            .shippingWard(clean(request.getWard()))
            .shippingDistrict(clean(request.getDistrict()))
            .shippingProvince(clean(request.getProvince()))
            .paymentMethod(paymentMethod)
            .subtotal(subtotal)
            .productDiscount(BigDecimal.ZERO)
            .shippingFee(shippingFee)
            .totalAmount(total)
            .status(online ? OrderStatus.PENDING_PAYMENT : OrderStatus.PENDING)
            .paymentStatus(online ? "PENDING" : "COD_PENDING")
            .reservationExpiresAt(online ? StoreTime.now().plusMinutes(15) : null)
            .inventoryCommitted(false)
            .checkoutIdentityHash(user == null ? identity.guestTokenHash() : null)
            .notes(clean(request.getNotes()))
            .voucherCode(voucher == null ? null : voucher.getCode())
            .voucherDiscount(voucherDiscount)
            .voucherType(voucher == null ? null : voucher.getDiscountType().name())
            .build();

        for (CartItem cartItem : sellableItems) {
            Product product = cartItem.getProduct();
            ProductVariant variant = cartItem.getVariant();
            OrderItem orderItem = OrderItem.builder()
                .product(product)
                .variant(variant)
                .productName(product.getName())
                .productImageUrl(resolveImage(product, variant))
                .productSku(variant == null ? null : variant.getSku())
                .variantLabel(variant == null ? null : variant.getLabel())
                .shadeName(variant == null ? null : variant.getShadeName())
                .netContent(netContent(variant))
                .quantity(cartItem.getQuantity())
                .price(effectivePrice(product, variant))
                .build();
            List<InventoryService.BatchReservation> reservations =
                inventoryService.reserveFefo(variant.getId(), cartItem.getQuantity());
            for (InventoryService.BatchReservation reservation : reservations) {
                InventoryBatch batch = batchRepository.findById(reservation.batchId())
                    .orElseThrow(() -> new IllegalStateException("Không tìm thấy lô vừa giữ tồn"));
                orderItem.addBatchAllocation(OrderItemBatchAllocation.builder()
                    .inventoryBatch(batch).quantity(reservation.quantity())
                    .status(AllocationStatus.RESERVED).build());
            }
            order.addItem(orderItem);
        }

        Order saved = orderRepository.save(order);
        if (voucher != null) {
            voucherService.recordVoucherUsage(voucher, user, guestVoucherIdentifier, saved);
        }
        cart.getCartItems().clear();
        cartRepository.save(cart);
        log.info("Created a BeautyStore order");
        return OrderResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getUserOrders(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new UserNotFoundException("Không tìm thấy tài khoản"));
        return orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
            .map(OrderResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderByNumber(String orderNumber) {
        return OrderResponse.fromEntity(orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng")));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderByNumberForUser(String orderNumber, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new UserNotFoundException("Không tìm thấy tài khoản"));
        Order order = orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng"));
        if (order.getUser() == null || !order.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Bạn không có quyền xem đơn hàng này");
        }
        return OrderResponse.fromEntity(order);
    }

    @Transactional(readOnly = true)
    public Page<Order> getAllOrders(Pageable pageable) { return orderRepository.findAll(pageable); }
    @Transactional(readOnly = true)
    public Page<Order> getOrdersByStatus(OrderStatus status, Pageable pageable) { return orderRepository.findByStatus(status, pageable); }
    @Transactional(readOnly = true)
    public Order getOrderById(Long id) { return orderRepository.findById(id).orElse(null); }
    public long getTotalOrders() { return orderRepository.count(); }
    public long getOrderCountByStatus(OrderStatus status) { return orderRepository.countByStatus(status); }

    @Transactional
    public Order updateOrderStatus(Long id, OrderStatus newStatus) {
        if (newStatus == null) throw new IllegalArgumentException("Trạng thái đơn hàng là bắt buộc");
        Order order = orderRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng"));
        OrderStatus previous = order.getStatus();
        if (previous == newStatus) return order;
        validateAdminTransition(order, newStatus);
        if ((newStatus == OrderStatus.CONFIRMED || newStatus == OrderStatus.PROCESSING)
                && previous == OrderStatus.PENDING && !Boolean.TRUE.equals(order.getInventoryCommitted())) {
            commitInventory(order);
        }
        if (newStatus == OrderStatus.CANCELLED) {
            if (previous != OrderStatus.DELIVERED) restoreVoucher(order);
            if (previous != OrderStatus.SHIPPING
                    && previous != OrderStatus.DELIVERED && previous != OrderStatus.FAILED) {
                if (Boolean.TRUE.equals(order.getInventoryCommitted())) restoreCommittedInventory(order);
                else releaseInventory(order);
            }
            if (previous == OrderStatus.PENDING_PAYMENT
                    && "PENDING".equalsIgnoreCase(order.getPaymentStatus())) {
                order.setPaymentStatus("CANCELLED");
            }
        }
        if (newStatus == OrderStatus.DELIVERED && order.getDeliveredAt() == null) {
            order.setDeliveredAt(StoreTime.now());
        }
        order.setStatus(newStatus);
        return orderRepository.save(order);
    }

    /**
     * Restocks the exact committed batches after an administrator has inspected
     * merchandise from a failed delivery. No shipper transition calls this
     * method automatically.
     */
    @Transactional
    public Order restockFailedDelivery(Long id) {
        Order order = orderRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng"));
        if (order.getStatus() != OrderStatus.FAILED) {
            throw new IllegalArgumentException("Chỉ có thể nhập lại kho cho đơn giao thất bại");
        }
        if (!Boolean.TRUE.equals(order.getInventoryCommitted())) {
            throw new IllegalArgumentException("Tồn kho của đơn này đã được xử lý");
        }
        restoreCommittedInventory(order);
        return orderRepository.save(order);
    }

    public BigDecimal getTotalRevenue() { return zeroIfNull(orderRepository.sumDeliveredRevenue()); }
    public BigDecimal getMonthlyRevenue() {
        LocalDateTime now = StoreTime.now();
        LocalDateTime start = now.withDayOfMonth(1).toLocalDate().atStartOfDay();
        return zeroIfNull(orderRepository.sumDeliveredRevenueBetween(start, start.plusMonths(1)));
    }
    public BigDecimal getTodayRevenue() {
        LocalDateTime start = StoreTime.today().atStartOfDay();
        return zeroIfNull(orderRepository.sumDeliveredRevenueBetween(start, start.plusDays(1)));
    }
    public BigDecimal getAverageOrderValue() {
        long delivered = orderRepository.countByStatus(OrderStatus.DELIVERED);
        if (delivered == 0) return BigDecimal.ZERO;
        return getTotalRevenue().divide(BigDecimal.valueOf(delivered), 0, java.math.RoundingMode.HALF_UP);
    }
    public long getTodayOrderCount() {
        LocalDateTime start = StoreTime.today().atStartOfDay();
        return orderRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, start.plusDays(1));
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId, String userEmail) {
        Order order = orderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng"));
        requireOwner(order, userEmail);
        cancelPending(order);
        return OrderResponse.fromEntity(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse cancelOrderByNumber(String orderNumber, String userEmail) {
        Order order = orderRepository.findByOrderNumberForUpdate(orderNumber)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng"));
        requireOwner(order, userEmail);
        cancelPending(order);
        return OrderResponse.fromEntity(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse cancelGuestOrder(String orderNumber) {
        Order order = orderRepository.findByOrderNumberForUpdate(orderNumber)
            .filter(candidate -> candidate.getUser() == null)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy guest order"));
        cancelPending(order);
        return OrderResponse.fromEntity(orderRepository.save(order));
    }

    public boolean orderExists(String orderNumber) { return orderRepository.findByOrderNumber(orderNumber).isPresent(); }
    public boolean verifyOrderAmount(String orderNumber, long amount) {
        try {
            return orderRepository.findByOrderNumber(orderNumber)
                .map(order -> order.getTotalAmount().longValueExact() == amount)
                .orElse(false);
        } catch (ArithmeticException exception) {
            return false;
        }
    }
    public boolean isPaymentConfirmed(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
            .map(OrderService::isCommittedPayment).orElse(false);
    }

    /**
     * Applies a verified IPN while holding the order write lock. A successful
     * gateway response is only reported as {@code CONFIRMED} after the exact
     * reserved batches have been committed. Late success is retained as a
     * durable reconciliation state instead of being acknowledged as fulfilled.
     */
    @Transactional
    public VNPayPaymentResult processVNPayIpn(String orderNumber, long amount,
            boolean success, String transactionNo, String bankCode, String responseCode) {
        Optional<Order> lockedOrder = orderRepository.findByOrderNumberForUpdate(orderNumber);
        if (lockedOrder.isEmpty()) {
            return paymentResult(VNPayPaymentOutcome.ORDER_NOT_FOUND, null);
        }

        Order order = lockedOrder.get();
        if (!matchesAmount(order, amount)) {
            return paymentResult(VNPayPaymentOutcome.INVALID_AMOUNT, order);
        }

        if (isCommittedPayment(order)) {
            return paymentResult(VNPayPaymentOutcome.ALREADY_CONFIRMED, order);
        }

        if (success && requiresPaymentReconciliation(order)) {
            recordGatewayResult(order, transactionNo, bankCode, responseCode);
            order.setPaymentStatus("RECONCILIATION_REQUIRED");
            orderRepository.save(order);
            log.warn("VNPay success requires manual reconciliation for order id={}", order.getId());
            return paymentResult(VNPayPaymentOutcome.RECONCILIATION_REQUIRED, order);
        }

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT
                || !"PENDING".equals(order.getPaymentStatus())) {
            return paymentResult(VNPayPaymentOutcome.ALREADY_FINAL, order);
        }

        recordGatewayResult(order, transactionNo, bankCode, responseCode);
        LocalDateTime now = StoreTime.now();
        if (success && (order.getReservationExpiresAt() == null
                || !order.getReservationExpiresAt().isAfter(now))) {
            restoreVoucher(order);
            releaseInventory(order);
            order.setPaymentStatus("RECONCILIATION_REQUIRED");
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
            log.warn("Late VNPay success requires manual reconciliation for order id={}", order.getId());
            return paymentResult(VNPayPaymentOutcome.RECONCILIATION_REQUIRED, order);
        }

        if (success) {
            commitInventory(order);
            order.setPaymentStatus("PAID");
            order.setStatus(OrderStatus.CONFIRMED);
            orderRepository.save(order);
            return paymentResult(VNPayPaymentOutcome.CONFIRMED, order);
        }

        restoreVoucher(order);
        releaseInventory(order);
        order.setPaymentStatus("FAILED");
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        return paymentResult(VNPayPaymentOutcome.FAILURE_RECORDED, order);
    }

    private static boolean matchesAmount(Order order, long amount) {
        if (amount <= 0 || order.getTotalAmount() == null) {
            return false;
        }
        try {
            return order.getTotalAmount().longValueExact() == amount;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    private static boolean isCommittedPayment(Order order) {
        return "PAID".equals(order.getPaymentStatus())
            && Boolean.TRUE.equals(order.getInventoryCommitted());
    }

    private static boolean requiresPaymentReconciliation(Order order) {
        return "RECONCILIATION_REQUIRED".equals(order.getPaymentStatus())
            || Boolean.TRUE.equals(order.getInventoryCommitted())
            || order.getStatus() != OrderStatus.PENDING_PAYMENT
            || !"PENDING".equals(order.getPaymentStatus());
    }

    private static void recordGatewayResult(Order order, String transactionNo,
            String bankCode, String responseCode) {
        order.setVnpayTransactionNo(transactionNo);
        order.setVnpayBankCode(bankCode);
        order.setVnpayResponseCode(responseCode);
    }

    private static VNPayPaymentResult paymentResult(VNPayPaymentOutcome outcome, Order order) {
        return new VNPayPaymentResult(
            outcome,
            order == null ? null : order.getPaymentStatus(),
            order != null && Boolean.TRUE.equals(order.getInventoryCommitted()));
    }

    @Scheduled(fixedDelayString = "${orders.reservation-cleanup-ms:60000}")
    @Transactional
    public void releaseExpiredPaymentReservations() {
        for (Order order : orderRepository.findExpiredPaymentReservations(StoreTime.now())) {
            Order locked = orderRepository.findByOrderNumberForUpdate(order.getOrderNumber()).orElse(null);
            if (locked == null || locked.getStatus() != OrderStatus.PENDING_PAYMENT
                    || !"PENDING".equals(locked.getPaymentStatus())
                    || Boolean.TRUE.equals(locked.getInventoryCommitted())) continue;
            restoreVoucher(locked);
            releaseInventory(locked);
            locked.setStatus(OrderStatus.CANCELLED);
            locked.setPaymentStatus("EXPIRED");
            orderRepository.save(locked);
        }
    }

    private void cancelPending(Order order) {
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalArgumentException("Chỉ có thể hủy đơn đang chờ xử lý hoặc thanh toán");
        }
        restoreVoucher(order);
        releaseInventory(order);
        order.setStatus(OrderStatus.CANCELLED);
        order.setPaymentStatus("CANCELLED");
    }

    private void requireOwner(Order order, String email) {
        if (order.getUser() == null || email == null
                || !order.getUser().getEmail().equalsIgnoreCase(email)) {
            throw new IllegalArgumentException("Bạn không có quyền thao tác đơn hàng này");
        }
    }

    private void commitInventory(Order order) {
        if (Boolean.TRUE.equals(order.getInventoryCommitted())) return;
        Map<Long, Integer> allocations = allocations(order, AllocationStatus.RESERVED);
        validateAllocationCoverage(order, allocations);
        inventoryService.commitReservations(allocations);
        order.getOrderItems().forEach(item -> item.getBatchAllocations().stream()
            .filter(allocation -> allocation.getStatus() == AllocationStatus.RESERVED)
            .forEach(allocation -> allocation.setStatus(AllocationStatus.COMMITTED)));
        order.setInventoryCommitted(true);
    }

    private void releaseInventory(Order order) {
        if (Boolean.TRUE.equals(order.getInventoryCommitted())) {
            throw new IllegalStateException("Tồn kho đã được ghi nhận, không thể giải phóng reservation");
        }
        Map<Long, Integer> allocations = allocations(order, AllocationStatus.RESERVED);
        inventoryService.releaseReservations(allocations);
        order.getOrderItems().forEach(item ->
            item.getBatchAllocations().stream()
                .filter(allocation -> allocation.getStatus() == AllocationStatus.RESERVED)
                .forEach(allocation -> allocation.setStatus(AllocationStatus.RELEASED)));
    }

    private void restoreCommittedInventory(Order order) {
        if (!Boolean.TRUE.equals(order.getInventoryCommitted())) return;
        Map<Long, Integer> allocations = allocations(order, AllocationStatus.COMMITTED);
        if (!allocations.isEmpty()) {
            List<InventoryBatch> batches = batchRepository.findAllByIdForUpdate(
                allocations.keySet().stream().sorted().toList());
            if (batches.size() != allocations.size()) {
                throw new IllegalStateException("Không tìm thấy đầy đủ lô tồn kho của đơn hàng");
            }
            for (InventoryBatch batch : batches) {
                batch.setQuantityOnHand(batch.getQuantityOnHand() + allocations.get(batch.getId()));
            }
            batchRepository.saveAll(batches);
        }
        order.getOrderItems().forEach(item -> item.getBatchAllocations().stream()
            .filter(allocation -> allocation.getStatus() == AllocationStatus.COMMITTED)
            .forEach(allocation -> allocation.setStatus(AllocationStatus.RETURNED)));
        order.setInventoryCommitted(false);
    }

    private static Map<Long, Integer> allocations(Order order, AllocationStatus status) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        order.getOrderItems().stream().flatMap(item -> item.getBatchAllocations().stream())
            .filter(allocation -> allocation.getStatus() == status)
            .forEach(allocation -> result.merge(allocation.getInventoryBatch().getId(),
                allocation.getQuantity(), Integer::sum));
        return result;
    }

    private static void validateAllocationCoverage(Order order, Map<Long, Integer> allocations) {
        if (order.getOrderItems().isEmpty() || allocations.isEmpty()) {
            throw new IllegalStateException("Đơn hàng không có reservation tồn kho hợp lệ");
        }
        for (OrderItem item : order.getOrderItems()) {
            if (item.getVariant() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new IllegalStateException("Dòng sản phẩm không có biến thể hoặc số lượng hợp lệ");
            }
            int reserved = item.getBatchAllocations().stream()
                .filter(allocation -> allocation.getStatus() == AllocationStatus.RESERVED)
                .mapToInt(OrderItemBatchAllocation::getQuantity)
                .sum();
            if (reserved != item.getQuantity()) {
                throw new IllegalStateException("Reservation tồn kho không khớp số lượng đặt mua");
            }
        }
    }

    private static void validateAdminTransition(Order order, OrderStatus next) {
        OrderStatus current = order.getStatus();
        if (next == OrderStatus.REFUNDED) {
            throw new IllegalArgumentException(
                "Hoàn tiền phải thực hiện qua quy trình đổi trả và có mã tham chiếu hoàn tiền");
        }
        if (next == OrderStatus.CANCELLED && hasCapturedVnpayPayment(order)) {
            throw new IllegalArgumentException(
                "Không thể hủy trực tiếp đơn VNPay đã ghi nhận thanh toán; hãy tạo yêu cầu đổi trả");
        }
        boolean allowed = switch (current) {
            case PENDING_PAYMENT -> next == OrderStatus.CANCELLED;
            case PENDING -> next == OrderStatus.CONFIRMED
                || next == OrderStatus.PROCESSING || next == OrderStatus.CANCELLED;
            case CONFIRMED -> next == OrderStatus.PROCESSING || next == OrderStatus.CANCELLED;
            case PROCESSING -> next == OrderStatus.CANCELLED;
            case SHIPPING -> false;
            case DELIVERED -> false;
            case FAILED -> next == OrderStatus.CANCELLED;
            case CANCELLED, REFUNDED -> false;
        };
        if (!allowed) {
            throw new IllegalArgumentException(
                "Không thể chuyển trạng thái đơn hàng từ " + current + " sang " + next);
        }
    }

    private static boolean hasCapturedVnpayPayment(Order order) {
        if (!isVnpayMethod(order.getPaymentMethod())) return false;
        return "PAID".equalsIgnoreCase(order.getPaymentStatus())
            || "RECONCILIATION_REQUIRED".equalsIgnoreCase(order.getPaymentStatus());
    }

    private static boolean isVnpayMethod(String paymentMethod) {
        return "VNPAY".equalsIgnoreCase(paymentMethod)
            || "E_WALLET".equalsIgnoreCase(paymentMethod);
    }

    private boolean isSellableCartItem(CartItem item) {
        if (item == null || item.getQuantity() == null || item.getQuantity() <= 0) return false;
        Product product = item.getProduct();
        ProductVariant variant = item.getVariant();
        if (product == null || variant == null
                || !Boolean.TRUE.equals(product.getActive())
                || !Boolean.TRUE.equals(variant.getActive())
                || variant.getProduct() == null
                || !Objects.equals(variant.getProduct().getId(), product.getId())) return false;
        if (product.getBrandEntity() != null
                && !Boolean.TRUE.equals(product.getBrandEntity().getActive())) return false;
        var category = product.getCategory();
        int depth = 0;
        while (category != null && depth++ < 32) {
            if (!Boolean.TRUE.equals(category.getActive())) return false;
            category = category.getParent();
        }
        return category == null;
    }

    private static BigDecimal nonNegative(BigDecimal value, String label) {
        if (value == null || value.signum() < 0) {
            throw new IllegalStateException(label + " chưa được cấu hình hợp lệ");
        }
        return value;
    }

    private void restoreVoucher(Order order) {
        if (order.getVoucherCode() == null) return;
        voucherService.restoreVoucherUsageForOrder(order);
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String normalizePaymentMethod(String value) {
        if (value == null) throw new IllegalArgumentException("Vui lòng chọn phương thức thanh toán");
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!"COD".equals(normalized) && !"VNPAY".equals(normalized)) {
            throw new IllegalArgumentException("BeautyStore chỉ hỗ trợ COD và VNPay");
        }
        return normalized;
    }

    private static BigDecimal effectivePrice(Product product, ProductVariant variant) {
        if (variant == null) {
            throw new IllegalStateException("Dòng giỏ hàng chưa có biến thể sản phẩm");
        }
        return variant.getEffectivePrice();
    }

    private static String netContent(ProductVariant variant) {
        if (variant == null || variant.getSizeValue() == null) return null;
        return variant.getSizeValue().stripTrailingZeros().toPlainString()
            + (variant.getSizeUnit() == null ? "" : " " + variant.getSizeUnit());
    }

    private static String resolveImage(Product product, ProductVariant variant) {
        return product.getImages().stream()
            .filter(image -> variant != null && image.getVariant() != null
                && image.getVariant().getId().equals(variant.getId()))
            .min(Comparator.comparing(ProductImage::getSortOrder).thenComparing(ProductImage::getId))
            .or(() -> product.getImages().stream().filter(image -> image.getVariant() == null)
                .min(Comparator.comparing(ProductImage::getSortOrder).thenComparing(ProductImage::getId)))
            .map(ProductImage::getUrl).orElse(null);
    }

    private static String clean(String value) { return value == null ? null : value.trim(); }
}
