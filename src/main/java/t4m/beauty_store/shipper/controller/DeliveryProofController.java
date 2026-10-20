package t4m.beauty_store.shipper.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import t4m.beauty_store.shipper.service.DeliveryProofAccessService;

@RestController
@RequiredArgsConstructor
public class DeliveryProofController {
    private final DeliveryProofAccessService proofAccess;

    /** Guest access is allowed only with an order-scoped, unexpired OTP access token. */
    @GetMapping("/api/delivery-proofs/{orderId}")
    public ResponseEntity<byte[]> read(@PathVariable Long orderId,
            @AuthenticationPrincipal UserDetails principal,
            @RequestHeader(value = "X-Order-Token", required = false) String guestToken) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"delivery-proof.jpg\"")
            .header("X-Content-Type-Options", "nosniff")
            .contentType(MediaType.IMAGE_JPEG)
            .body(proofAccess.read(orderId, principal, guestToken));
    }
}
