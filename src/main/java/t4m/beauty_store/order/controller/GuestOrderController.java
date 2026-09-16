package t4m.beauty_store.order.controller;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import t4m.beauty_store.order.dto.GuestOrderAccessRequest;
import t4m.beauty_store.order.dto.GuestOrderVerifyRequest;
import t4m.beauty_store.order.dto.OrderResponse;
import t4m.beauty_store.order.service.GuestOrderAccessService;
import t4m.beauty_store.order.service.OrderService;

import java.util.Map;

@RestController
@RequestMapping("/api/guest-orders")
@RequiredArgsConstructor
public class GuestOrderController {
    private final GuestOrderAccessService guestOrderAccessService;
    private final OrderService orderService;

    @PostMapping("/access/request")
    public ResponseEntity<Map<String, String>> requestAccess(
            @Valid @RequestBody GuestOrderAccessRequest request,
            HttpServletRequest httpRequest) {
        guestOrderAccessService.requestOtp(
            request.getOrderNumber(), request.getEmail(), httpRequest.getRemoteAddr());
        return ResponseEntity.ok(Map.of("message", "Nếu thông tin khớp, BeautyStore đã gửi OTP đến email của bạn"));
    }

    @PostMapping("/access/verify")
    public ResponseEntity<Map<String, String>> verifyAccess(
            @Valid @RequestBody GuestOrderVerifyRequest request,
            HttpServletRequest httpRequest) {
        String token = guestOrderAccessService.verifyOtp(
            request.getOrderNumber(), request.getEmail(), request.getOtp(),
            httpRequest.getRemoteAddr());
        return ResponseEntity.ok(Map.of("accessToken", token, "expiresIn", "1800"));
    }

    @GetMapping("/{orderNumber}")
    public ResponseEntity<OrderResponse> getOrder(
            @PathVariable String orderNumber,
            @RequestHeader("X-Order-Token") String token) {
        return ResponseEntity.ok(guestOrderAccessService.requireOrderAccessResponse(orderNumber, token));
    }

    @PostMapping("/{orderNumber}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable String orderNumber,
            @RequestHeader("X-Order-Token") String token) {
        guestOrderAccessService.requireOrderAccess(orderNumber, token);
        return ResponseEntity.ok(orderService.cancelGuestOrder(orderNumber));
    }
}
