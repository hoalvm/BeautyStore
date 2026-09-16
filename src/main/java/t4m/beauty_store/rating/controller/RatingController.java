package t4m.beauty_store.rating.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import t4m.beauty_store.rating.dto.ProductRatingSummary;
import t4m.beauty_store.rating.service.RatingService;

/**
 * One-release compatibility alias. New submissions and moderation live under
 * /api/reviews; legacy Rating rows remain read-only for order-history safety.
 */
@Deprecated(forRemoval = true)
@RestController
@RequestMapping("/api/ratings")
@RequiredArgsConstructor
public class RatingController {
    private final RatingService ratingService;

    @GetMapping("/product/{productId}/summary")
    public ResponseEntity<ProductRatingSummary> getProductRatingSummary(@PathVariable Long productId) {
        return ResponseEntity.ok()
            .header("Deprecation", "true")
            .header("Link", "</api/reviews/product/" + productId + ">; rel=successor-version")
            .body(ratingService.getProductRatingSummary(productId));
    }
}
