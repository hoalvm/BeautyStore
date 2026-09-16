package t4m.beauty_store.cart.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import t4m.beauty_store.cart.dto.AddToCartRequest;
import t4m.beauty_store.cart.dto.CartResponse;
import t4m.beauty_store.cart.dto.UpdateCartItemRequest;
import t4m.beauty_store.cart.service.CartIdentity;
import t4m.beauty_store.cart.service.CartService;
import t4m.beauty_store.cart.service.GuestCartCookieService;

import java.util.Map;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {
    private final CartService cartService;
    private final GuestCartCookieService guestCookieService;

    @PostMapping("/add")
    public ResponseEntity<CartResponse> addToCart(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody AddToCartRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        return ResponseEntity.ok(cartService.addToCart(
            identity(principal, httpRequest, httpResponse, true), request));
    }

    @GetMapping
    public ResponseEntity<CartResponse> getCart(
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request,
            HttpServletResponse response) {
        return ResponseEntity.ok(cartService.getCart(identity(principal, request, response, false)));
    }

    @PutMapping("/items/{cartItemId}")
    public ResponseEntity<CartResponse> updateCartItem(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long cartItemId,
            @Valid @RequestBody UpdateCartItemRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        return ResponseEntity.ok(cartService.updateCartItem(
            identity(principal, httpRequest, httpResponse, false), cartItemId, request));
    }

    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<CartResponse> removeCartItem(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long cartItemId,
            HttpServletRequest request,
            HttpServletResponse response) {
        return ResponseEntity.ok(cartService.removeCartItem(
            identity(principal, request, response, false), cartItemId));
    }

    @DeleteMapping("/clear")
    public ResponseEntity<Map<String, String>> clearCart(
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request,
            HttpServletResponse response) {
        cartService.clearCart(identity(principal, request, response, false));
        return ResponseEntity.ok(Map.of("message", "Đã làm trống giỏ hàng"));
    }

    private CartIdentity identity(UserDetails principal, HttpServletRequest request,
            HttpServletResponse response, boolean createGuestToken) {
        if (principal != null) {
            String guestHash = guestCookieService.resolveExistingTokenHash(request);
            if (guestHash != null) {
                cartService.mergeGuestCart(principal.getUsername(), guestHash);
                guestCookieService.clearCookie(response);
            }
            return new CartIdentity(principal.getUsername(), null);
        }
        String guestHash = createGuestToken
            ? guestCookieService.resolveTokenHash(request, response)
            : guestCookieService.resolveExistingTokenHash(request);
        return new CartIdentity(null, guestHash);
    }
}
