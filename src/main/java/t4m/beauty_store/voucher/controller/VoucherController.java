package t4m.beauty_store.voucher.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import t4m.beauty_store.auth.entity.User;
import t4m.beauty_store.auth.repository.UserRepository;
import t4m.beauty_store.cart.service.CartIdentity;
import t4m.beauty_store.cart.service.CartService;
import t4m.beauty_store.cart.service.GuestCartCookieService;
import t4m.beauty_store.voucher.dto.VoucherValidationResponse;
import t4m.beauty_store.voucher.service.VoucherService;

@RestController
@RequestMapping("/api/vouchers")
@RequiredArgsConstructor
@Validated
public class VoucherController {
    private final VoucherService voucherService;
    private final CartService cartService;
    private final GuestCartCookieService guestCookieService;
    private final UserRepository userRepository;

    @PostMapping("/validate")
    public ResponseEntity<VoucherValidationResponse> validateVoucher(
            @RequestParam @Size(max = 50) String code,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request) {
        User user = principal == null ? null : userRepository.findByEmail(principal.getUsername())
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản"));
        CartIdentity identity = user == null
            ? new CartIdentity(null, guestCookieService.resolveExistingTokenHash(request))
            : new CartIdentity(user.getEmail(), null);
        CartService.VoucherCartSnapshot cart = cartService.getVoucherCartSnapshot(identity);
        var voucherLines = cart.lines().stream()
            .map(line -> new VoucherService.VoucherLine(
                line.product(), line.variant(), line.lineTotal()))
            .toList();
        VoucherValidationResponse response = voucherService.validateVoucher(
            code, cart.subtotal(), user, null, voucherLines);
        return ResponseEntity.ok(response);
    }
}
