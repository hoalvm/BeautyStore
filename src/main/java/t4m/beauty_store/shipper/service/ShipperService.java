package t4m.beauty_store.shipper.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import t4m.beauty_store.auth.entity.User;
import t4m.beauty_store.auth.exception.UserNotFoundException;
import t4m.beauty_store.auth.repository.UserRepository;
import t4m.beauty_store.config.StoreTime;
import t4m.beauty_store.order.entity.Order;
import t4m.beauty_store.order.entity.OrderStatus;
import t4m.beauty_store.order.repository.OrderRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShipperService {
    
    private static final Logger logger = LoggerFactory.getLogger(ShipperService.class);
    
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    /**
     * Lấy danh sách đơn hàng có trạng thái PROCESSING (chờ shipper nhận)
     * Shipper có thể xem các đơn này để chọn nhận
     */
    public List<Order> getAvailableOrders() {
        logger.info("Getting available orders for shippers (PROCESSING status)");
        return orderRepository.findByStatus(OrderStatus.PROCESSING, null).getContent();
    }

    /**
     * Lấy danh sách đơn hàng đang giao của shipper cụ thể
     * Chỉ lấy các đơn có trạng thái SHIPPING và được gán cho shipper này
     */
    public List<Order> getShipperActiveOrders(String shipperEmail) {
        User shipper = userRepository.findByEmail(shipperEmail)
                .orElseThrow(() -> new UserNotFoundException("Shipper not found"));
        
        logger.debug("Loading active deliveries for shipperId={}", shipper.getId());
        return orderRepository.findByShipperIdAndStatus(shipper.getId(), OrderStatus.SHIPPING);
    }

    /**
     * Lấy danh sách tất cả đơn hàng liên quan đến shipper
     * Bao gồm cả đơn đang giao (SHIPPING) và có thể nhận (PROCESSING)
     */
    public List<Order> getShipperOrders(String shipperEmail) {
        User shipper = userRepository.findByEmail(shipperEmail)
                .orElseThrow(() -> new UserNotFoundException("Shipper not found"));
        
        logger.debug("Loading delivery queue for shipperId={}", shipper.getId());
        // Lấy các đơn đang giao của shipper này
        List<Order> shippingOrders = orderRepository.findByShipperIdAndStatus(shipper.getId(), OrderStatus.SHIPPING);
        // Thêm các đơn đang chờ nhận (PROCESSING)
        List<Order> processingOrders = getAvailableOrders();
        
        shippingOrders.addAll(processingOrders);
        return shippingOrders;
    }

    /**
     * Lấy lịch sử đơn hàng đã giao của shipper
     * Bao gồm các đơn có trạng thái DELIVERED, FAILED và CANCELLED
     */
    public List<Order> getShipperHistory(String shipperEmail) {
        User shipper = userRepository.findByEmail(shipperEmail)
                .orElseThrow(() -> new UserNotFoundException("Shipper not found"));
        
        logger.debug("Loading delivery history for shipperId={}", shipper.getId());
        
        // Lấy các đơn đã hoàn tất (thành công hoặc thất bại)
        List<Order> history = new java.util.ArrayList<>();
        history.addAll(orderRepository.findByShipperIdAndStatus(shipper.getId(), OrderStatus.DELIVERED));
        history.addAll(orderRepository.findByShipperIdAndStatus(shipper.getId(), OrderStatus.FAILED));
        history.addAll(orderRepository.findByShipperIdAndStatus(shipper.getId(), OrderStatus.CANCELLED));
        
        // Sắp xếp theo thời gian tạo giảm dần
        history.sort((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()));
        
        return history;
    }

    /**
     * Shipper nhận đơn hàng
     * Chuyển trạng thái từ PROCESSING -> SHIPPING và gán shipper_id
     */
    @Transactional
    public Order acceptOrder(Long orderId, String shipperEmail) {
        User shipper = userRepository.findByEmail(shipperEmail)
                .orElseThrow(() -> new UserNotFoundException("Shipper not found"));
        
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng"));
        
        // Kiểm tra trạng thái đơn hàng
        if (order.getStatus() != OrderStatus.PROCESSING) {
            throw new IllegalArgumentException("Chỉ có thể nhận đơn hàng đang ở trạng thái 'Đang xử lý'");
        }
        
        // Kiểm tra đơn hàng đã được shipper khác nhận chưa
        if (order.getShipper() != null) {
            throw new IllegalArgumentException("Đơn hàng này đã được shipper khác nhận");
        }
        
        // Cập nhật trạng thái và gán shipper
        order.setStatus(OrderStatus.SHIPPING);
        order.setShipper(shipper);
        
        Order savedOrder = orderRepository.save(order);
        logger.info("Delivery accepted: orderId={}, shipperId={}", orderId, shipper.getId());
        
        return initializeForResponse(savedOrder);
    }

    /**
     * Shipper hoàn thành giao hàng
     * Chuyển trạng thái từ SHIPPING -> DELIVERED
     */
    @Transactional
    public Order completeDelivery(Long orderId, String shipperEmail) {
        User shipper = userRepository.findByEmail(shipperEmail)
                .orElseThrow(() -> new UserNotFoundException("Shipper not found"));
        
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng"));
        
        // Kiểm tra trạng thái đơn hàng
        if (order.getStatus() != OrderStatus.SHIPPING) {
            throw new IllegalArgumentException("Chỉ có thể hoàn thành đơn hàng đang ở trạng thái 'Đang giao'");
        }
        
        // Kiểm tra đơn hàng có thuộc về shipper này không
        if (order.getShipper() == null || !order.getShipper().getId().equals(shipper.getId())) {
            throw new IllegalArgumentException("Bạn không có quyền hoàn thành đơn hàng này");
        }
        
        // Cập nhật trạng thái
        order.setStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(StoreTime.now());
        if ("COD".equalsIgnoreCase(order.getPaymentMethod())) {
            order.setPaymentStatus("COD_PAID");
        }
        
        Order savedOrder = orderRepository.save(order);
        logger.info("Delivery completed: orderId={}, shipperId={}", orderId, shipper.getId());
        
        return initializeForResponse(savedOrder);
    }

    /**
     * Lấy chi tiết một đơn hàng
     */
    public Order getOrderById(Long orderId, String shipperEmail) {
        User shipper = userRepository.findByEmail(shipperEmail)
                .orElseThrow(() -> new UserNotFoundException("Shipper not found"));
        
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng"));
        
        // Kiểm tra quyền xem đơn hàng:
        // - Đơn đang PROCESSING (có thể nhận) thì tất cả shipper đều xem được
        // - Đơn đang SHIPPING, DELIVERED, FAILED thì chỉ shipper được gán mới xem được
        if (order.getStatus() != OrderStatus.PROCESSING) {
            if (order.getShipper() == null || !order.getShipper().getId().equals(shipper.getId())) {
                throw new IllegalArgumentException("Bạn không có quyền xem đơn hàng này");
            }
        }
        
        logger.debug("Delivery details accessed: orderId={}, shipperId={}", orderId, shipper.getId());
        return order;
    }

    /**
     * Lấy thống kê cho shipper dashboard
     */
    public ShipperStats getShipperStats(String shipperEmail) {
        User shipper = userRepository.findByEmail(shipperEmail)
                .orElseThrow(() -> new UserNotFoundException("Shipper not found"));
        
        long availableOrders = orderRepository.countByStatus(OrderStatus.PROCESSING);
        long activeDeliveries = orderRepository.countByShipperIdAndStatus(shipper.getId(), OrderStatus.SHIPPING);
        long completedDeliveries = orderRepository.countByShipperIdAndStatus(shipper.getId(), OrderStatus.DELIVERED);
        long failedDeliveries = orderRepository.countByShipperIdAndStatus(shipper.getId(), OrderStatus.FAILED);
        // Tổng đã giao = Đã giao thành công + Giao thất bại
        long totalDeliveries = completedDeliveries + failedDeliveries;
        
        logger.debug("Delivery metrics calculated for shipperId={}", shipper.getId());
        
        return ShipperStats.builder()
                .availableOrders(availableOrders)
                .activeDeliveries(activeDeliveries)
                .completedDeliveries(completedDeliveries)
                .failedDeliveries(failedDeliveries)
                .totalDeliveries(totalDeliveries)
                .build();
    }

    /**
     * Shipper báo cáo giao hàng thất bại
     * Chuyển trạng thái từ SHIPPING -> FAILED
     */
    @Transactional
    public Order failDelivery(Long orderId, String shipperEmail, String reason) {
        User shipper = userRepository.findByEmail(shipperEmail)
                .orElseThrow(() -> new UserNotFoundException("Shipper not found"));
        
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng"));
        
        // Kiểm tra trạng thái đơn hàng
        if (order.getStatus() != OrderStatus.SHIPPING) {
            throw new IllegalArgumentException("Chỉ có thể báo cáo thất bại cho đơn hàng đang ở trạng thái 'Đang giao'");
        }
        
        // Kiểm tra đơn hàng có thuộc về shipper này không
        if (order.getShipper() == null || !order.getShipper().getId().equals(shipper.getId())) {
            throw new IllegalArgumentException("Bạn không có quyền cập nhật đơn hàng này");
        }
        
        // Cập nhật trạng thái
        order.setStatus(OrderStatus.FAILED);
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isBlank()) {
            normalizedReason = "Không thể hoàn tất giao hàng";
        }
        order.setDeliveryFailureReason(normalizedReason.length() <= 500
            ? normalizedReason : normalizedReason.substring(0, 500));
        
        Order savedOrder = orderRepository.save(order);
        logger.info("Delivery marked failed: orderId={}, shipperId={}", orderId, shipper.getId());
        
        return initializeForResponse(savedOrder);
    }

    /**
     * Keep the locking query portable by not fetch-joining a collection, then
     * initialize the items before the controller renders a detached response.
     */
    private static Order initializeForResponse(Order order) {
        order.getOrderItems().size();
        return order;
    }
}
