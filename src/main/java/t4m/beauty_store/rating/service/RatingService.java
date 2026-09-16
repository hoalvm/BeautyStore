package t4m.beauty_store.rating.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import t4m.beauty_store.product.entity.Product;
import t4m.beauty_store.product.repository.ProductRepository;
import t4m.beauty_store.rating.dto.ProductRatingSummary;

@Service
@RequiredArgsConstructor
public class RatingService {
    private final ProductRepository productRepository;

    /**
     * One-release compatibility view. The stored summary is maintained only by
     * approved Review rows; legacy Rating rows are immutable migration history.
     */
    public ProductRatingSummary getProductRatingSummary(Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        
        return ProductRatingSummary.builder()
            .productId(productId)
            .averageRating(product.getAverageRating() != null ? product.getAverageRating() : 0.0)
            .ratingCount(product.getRatingCount() != null ? product.getRatingCount() : 0)
            .build();
    }
}
