package t4m.beauty_store.shipper.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import t4m.beauty_store.auth.repository.UserRepository;
import t4m.beauty_store.config.ApiException;
import t4m.beauty_store.order.entity.Order;
import t4m.beauty_store.order.repository.OrderRepository;
import t4m.beauty_store.order.service.GuestOrderAccessService;

@Service
@RequiredArgsConstructor
public class DeliveryProofAccessService {
    private final OrderRepository orders;
    private final UserRepository users;
    private final GuestOrderAccessService guestAccess;

    @Transactional(readOnly = true)
    public byte[] read(Long orderId, UserDetails principal, String guestToken) {
        Order order = orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Không tìm thấy ảnh giao hàng"));
        boolean allowed = false;
        if (principal != null) {
            boolean admin = hasRole(principal, "ADMIN");
            boolean shipper = hasRole(principal, "SHIPPER");
            Long userId = users.findByEmail(principal.getUsername()).map(user -> user.getId()).orElse(null);
            allowed = admin || (userId != null && (
                order.getUser() != null && userId.equals(order.getUser().getId())
                || shipper && order.getShipper() != null && userId.equals(order.getShipper().getId())));
        }
        if (!allowed && order.getUser() == null && guestToken != null) {
            try {
                allowed = orderId.equals(guestAccess.requireOrderAccess(order.getOrderNumber(), guestToken).getId());
            } catch (IllegalArgumentException invalidToken) {
                throw ApiException.forbidden("Bạn không có quyền xem ảnh giao hàng");
            }
        }
        if (!allowed) throw ApiException.forbidden("Bạn không có quyền xem ảnh giao hàng");
        if (order.getDeliveryProof() == null) throw ApiException.notFound("Đơn hàng chưa có ảnh giao hàng");
        return order.getDeliveryProof().getImageBytes();
    }

    private static boolean hasRole(UserDetails principal, String role) {
        return principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }
}
