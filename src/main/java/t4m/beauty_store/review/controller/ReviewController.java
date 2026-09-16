package t4m.beauty_store.review.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import t4m.beauty_store.auth.entity.User;
import t4m.beauty_store.auth.repository.UserRepository;
import t4m.beauty_store.review.dto.ReviewRequest;
import t4m.beauty_store.review.dto.ReviewResponse;
import t4m.beauty_store.review.service.ReviewService;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;
    private final UserRepository userRepository;

    @GetMapping("/product/{productId}")
    public ResponseEntity<Page<ReviewResponse>> list(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(reviewService.publicReviews(productId,
            PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 50)))));
    }

    @PostMapping
    public ResponseEntity<ReviewResponse> create(
            @AuthenticationPrincipal UserDetails principal,
            @RequestHeader(value = "X-Order-Token", required = false) String guestToken,
            @Valid @RequestBody ReviewRequest request) {
        User user = principal == null ? null : userRepository.findByEmail(principal.getUsername())
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản"));
        return ResponseEntity.ok(reviewService.create(request, user, guestToken));
    }
}
