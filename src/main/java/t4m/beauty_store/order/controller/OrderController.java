package t4m.beauty_store.order.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import t4m.beauty_store.cart.service.CartIdentity;
import t4m.beauty_store.cart.service.CartService;
import t4m.beauty_store.cart.service.GuestCartCookieService;
import t4m.beauty_store.order.dto.CheckoutRequest;
import t4m.beauty_store.order.dto.OrderResponse;
import t4m.beauty_store.order.service.CheckoutResult;
import t4m.beauty_store.order.service.CheckoutService;
import t4m.beauty_store.order.service.OrderService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;
    private final CheckoutService checkoutService;
    private final GuestCartCookieService guestCookieService;
    private final CartService cartService;

    @PostMapping("/checkout")
    public ResponseEntity<?> checkout(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody CheckoutRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        CartIdentity identity;
        if (principal == null) {
            identity = new CartIdentity(null,
                guestCookieService.resolveTokenHash(httpRequest, httpResponse));
        } else {
            String guestHash = guestCookieService.resolveExistingTokenHash(httpRequest);
            if (guestHash != null) {
                cartService.mergeGuestCart(principal.getUsername(), guestHash);
                guestCookieService.clearCookie(httpResponse);
            }
            identity = new CartIdentity(principal.getUsername(), null);
        }
        CheckoutResult result = checkoutService.checkout(identity, request, httpRequest);
        OrderResponse order = result.order();
        if (result.redirectsToPayment()) {
            return ResponseEntity.ok(Map.of(
                "order", order,
                "paymentUrl", result.paymentUrl(),
                "redirectToPayment", true));
        }
        return ResponseEntity.ok(order);
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getUserOrders(@AuthenticationPrincipal UserDetails principal) {
        requirePrincipal(principal);
        return ResponseEntity.ok(orderService.getUserOrders(principal.getUsername()));
    }

    @GetMapping("/{orderNumber}")
    public ResponseEntity<OrderResponse> getOrder(
            @PathVariable String orderNumber,
            @AuthenticationPrincipal UserDetails principal) {
        requirePrincipal(principal);
        boolean admin = principal.getAuthorities().stream()
            .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return ResponseEntity.ok(admin
            ? orderService.getOrderByNumber(orderNumber)
            : orderService.getOrderByNumberForUser(orderNumber, principal.getUsername()));
    }

    @PutMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancel(
            @PathVariable Long orderId,
            @AuthenticationPrincipal UserDetails principal) {
        requirePrincipal(principal);
        return ResponseEntity.ok(orderService.cancelOrder(orderId, principal.getUsername()));
    }

    @PostMapping("/{orderNumber}/cancel")
    public ResponseEntity<OrderResponse> cancelByNumber(
            @PathVariable String orderNumber,
            @AuthenticationPrincipal UserDetails principal) {
        requirePrincipal(principal);
        return ResponseEntity.ok(orderService.cancelOrderByNumber(orderNumber, principal.getUsername()));
    }

    private static void requirePrincipal(UserDetails principal) {
        if (principal == null) throw new IllegalArgumentException("Vui lòng đăng nhập");
    }
}
