package t4m.beauty_store.returns.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import t4m.beauty_store.auth.entity.User;
import t4m.beauty_store.auth.repository.UserRepository;
import t4m.beauty_store.returns.dto.ReturnCreateRequest;
import t4m.beauty_store.returns.dto.ReturnResponse;
import t4m.beauty_store.returns.service.ReturnService;

import java.util.List;

@RestController
@RequestMapping("/api/returns")
@RequiredArgsConstructor
public class ReturnController {
    private final ReturnService returnService;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<ReturnResponse> create(
            @AuthenticationPrincipal UserDetails principal,
            @RequestHeader(value = "X-Order-Token", required = false) String guestToken,
            @Valid @RequestBody ReturnCreateRequest request) {
        User user = principal == null ? null : userRepository.findByEmail(principal.getUsername())
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản"));
        return ResponseEntity.ok(returnService.create(request, user, guestToken));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReturnResponse> get(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal,
            @RequestHeader(value = "X-Order-Token", required = false) String guestToken) {
        return ResponseEntity.ok(returnService.get(id, resolveUser(principal), guestToken));
    }

    @GetMapping
    public ResponseEntity<List<ReturnResponse>> listForOrder(
            @RequestParam String orderNumber,
            @AuthenticationPrincipal UserDetails principal,
            @RequestHeader(value = "X-Order-Token", required = false) String guestToken) {
        return ResponseEntity.ok(returnService.listForOrder(
            orderNumber, resolveUser(principal), guestToken));
    }

    private User resolveUser(UserDetails principal) {
        return principal == null ? null : userRepository.findByEmail(principal.getUsername())
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản"));
    }
}
