package t4m.beauty_store.review.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import t4m.beauty_store.review.dto.ReviewResponse;
import t4m.beauty_store.review.entity.ReviewStatus;
import t4m.beauty_store.review.service.ReviewService;
import t4m.beauty_store.config.PageResponse;

@RestController
@RequestMapping("/api/admin/reviews")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminReviewController {
    private final ReviewService reviewService;

    @GetMapping
    public ResponseEntity<PageResponse<ReviewResponse>> list(
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.from(reviewService.adminReviews(status,
            PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100))))));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ReviewResponse> moderate(
            @PathVariable Long id,
            @RequestParam ReviewStatus status) {
        return ResponseEntity.ok(reviewService.moderate(id, status));
    }
}
