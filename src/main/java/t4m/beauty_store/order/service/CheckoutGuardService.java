package t4m.beauty_store.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import t4m.beauty_store.cart.service.CartIdentity;
import t4m.beauty_store.auth.repository.UserRepository;
import t4m.beauty_store.order.dto.CheckoutRequest;
import t4m.beauty_store.order.entity.OrderStatus;
import t4m.beauty_store.order.repository.OrderRepository;

import java.util.List;
import java.util.Locale;

/** Prevents anonymous COD orders from holding inventory without a practical bound. */
@Service
@RequiredArgsConstructor
public class CheckoutGuardService {
    private static final long MAX_OPEN_COD_ORDERS = 3;
    private static final List<OrderStatus> OPEN_COD_STATUSES = List.of(
        OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PROCESSING,
        OrderStatus.SHIPPING, OrderStatus.FAILED);

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public void verifyOpenOrderQuota(CartIdentity identity, CheckoutRequest request) {
        if (request == null || !"COD".equalsIgnoreCase(request.getPaymentMethod())) return;
        if (identity == null || (!identity.authenticated()
                && (identity.guestTokenHash() == null || identity.guestTokenHash().isBlank()))) {
            throw new IllegalArgumentException("Không xác định được danh tính checkout");
        }
        long identityOrders = identity != null && identity.authenticated()
            ? orderRepository.countByUserIdAndPaymentMethodIgnoreCaseAndStatusIn(
                requireUserId(identity.userEmail()), "COD", OPEN_COD_STATUSES)
            : orderRepository.countByCheckoutIdentityHashAndPaymentMethodIgnoreCaseAndStatusIn(
                identity == null ? null : identity.guestTokenHash(), "COD", OPEN_COD_STATUSES);
        String email = request.getCustomerEmail().trim().toLowerCase(Locale.ROOT);
        String phone = request.getCustomerPhone().trim();
        long emailOrders = orderRepository
            .countByCustomerEmailIgnoreCaseAndPaymentMethodIgnoreCaseAndStatusIn(
                email, "COD", OPEN_COD_STATUSES);
        long phoneOrders = orderRepository
            .countByCustomerPhoneAndPaymentMethodIgnoreCaseAndStatusIn(
                phone, "COD", OPEN_COD_STATUSES);
        if (identityOrders >= MAX_OPEN_COD_ORDERS || emailOrders >= MAX_OPEN_COD_ORDERS
                || phoneOrders >= MAX_OPEN_COD_ORDERS) {
            throw new IllegalArgumentException(
                "Bạn đang có quá nhiều đơn COD chưa hoàn tất; vui lòng xử lý đơn hiện có trước");
        }
    }

    private Long requireUserId(String email) {
        return userRepository.findByEmail(email)
            .map(user -> user.getId())
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản checkout"));
    }
}
