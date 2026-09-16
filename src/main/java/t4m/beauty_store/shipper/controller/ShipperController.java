package t4m.beauty_store.shipper.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import t4m.beauty_store.order.dto.OrderResponse;
import t4m.beauty_store.order.entity.Order;
import t4m.beauty_store.shipper.service.ShipperService;
import t4m.beauty_store.shipper.service.ShipperStats;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/shipper")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_SHIPPER')")
public class ShipperController {
    
    private final ShipperService shipperService;

    /**
     * Lấy danh sách đơn hàng liên quan đến shipper
     * Bao gồm: đơn có thể nhận (PROCESSING) và đơn đang giao (SHIPPING)
     */
    @GetMapping("/orders")
    public ResponseEntity<List<OrderResponse>> getShipperOrders(
            Authentication authentication) {
        List<Order> orders = shipperService.getShipperOrders(authentication.getName());
        List<OrderResponse> response = orders.stream()
                .map(OrderResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    /**
     * Lấy danh sách đơn hàng có thể nhận (PROCESSING)
     */
    @GetMapping("/orders/available")
    public ResponseEntity<List<OrderResponse>> getAvailableOrders() {
        List<Order> orders = shipperService.getAvailableOrders();
        List<OrderResponse> response = orders.stream()
                .map(OrderResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    /**
     * Lấy danh sách đơn hàng đang giao của shipper
     */
    @GetMapping("/orders/active")
    public ResponseEntity<List<OrderResponse>> getActiveOrders(
            Authentication authentication) {
        List<Order> orders = shipperService.getShipperActiveOrders(authentication.getName());
        List<OrderResponse> response = orders.stream()
                .map(OrderResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    /**
     * Lấy lịch sử giao hàng của shipper
     */
    @GetMapping("/orders/history")
    public ResponseEntity<List<OrderResponse>> getDeliveryHistory(
            Authentication authentication) {
        List<Order> orders = shipperService.getShipperHistory(authentication.getName());
        List<OrderResponse> response = orders.stream()
                .map(OrderResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    /**
     * Shipper nhận đơn hàng
     * Chuyển trạng thái từ PROCESSING -> SHIPPING và gán shipper_id
     */
    @PutMapping("/orders/{id}/accept")
    public ResponseEntity<?> acceptOrder(
            @PathVariable Long id,
            Authentication authentication) {
        Order order = shipperService.acceptOrder(id, authentication.getName());
        return ResponseEntity.ok(OrderResponse.fromEntity(order));
    }

    /**
     * Shipper hoàn thành giao hàng
     * Chuyển trạng thái từ SHIPPING -> DELIVERED
     */
    @PutMapping("/orders/{id}/complete")
    public ResponseEntity<?> completeDelivery(
            @PathVariable Long id,
            Authentication authentication) {
        Order order = shipperService.completeDelivery(id, authentication.getName());
        return ResponseEntity.ok(OrderResponse.fromEntity(order));
    }

    /**
     * Shipper báo cáo giao hàng thất bại
     * Chuyển trạng thái từ SHIPPING -> FAILED
     */
    @PutMapping("/orders/{id}/fail")
    public ResponseEntity<?> failDelivery(
            @PathVariable Long id,
            Authentication authentication,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null
            ? body.getOrDefault("reason", "Không thể liên hệ khách hàng")
            : "Không thể liên hệ khách hàng";
        Order order = shipperService.failDelivery(id, authentication.getName(), reason);
        return ResponseEntity.ok(OrderResponse.fromEntity(order));
    }

    /**
     * Lấy chi tiết một đơn hàng
     */
    @GetMapping("/orders/{id}")
    public ResponseEntity<OrderResponse> getOrderById(
            @PathVariable Long id,
            Authentication authentication) {
        Order order = shipperService.getOrderById(id, authentication.getName());
        return ResponseEntity.ok(OrderResponse.fromEntity(order));
    }

    /**
     * Lấy thống kê cho shipper dashboard
     */
    @GetMapping("/stats")
    public ResponseEntity<ShipperStats> getStats(
            Authentication authentication) {
        ShipperStats stats = shipperService.getShipperStats(authentication.getName());
        return ResponseEntity.ok(stats);
    }
}
