package t4m.beauty_store.review.dto;

import lombok.Builder;
import lombok.Data;
import t4m.beauty_store.review.entity.Review;
import t4m.beauty_store.review.entity.ReviewStatus;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ReviewResponse {
    private Long id;
    private Long productId;
    private Long variantId;
    private String displayName;
    private Integer stars;
    private String title;
    private String content;
    private String skinType;
    private Boolean verifiedPurchase;
    private ReviewStatus status;
    private List<String> images;
    private LocalDateTime createdAt;

    public static ReviewResponse fromEntity(Review review) {
        String name = review.getUser() == null || review.getUser().getName() == null
            ? "Khách hàng BeautyStore"
            : maskName(review.getUser().getName());
        return ReviewResponse.builder()
            .id(review.getId())
            .productId(review.getProduct().getId())
            .variantId(review.getOrderItem().getVariant() == null ? null : review.getOrderItem().getVariant().getId())
            .displayName(name)
            .stars(review.getStars())
            .title(review.getTitle())
            .content(review.getContent())
            .skinType(review.getSkinType())
            .verifiedPurchase(review.getVerifiedPurchase())
            .status(review.getStatus())
            .images(review.getImages().stream().map(image -> image.getUrl()).toList())
            .createdAt(review.getCreatedAt())
            .build();
    }

    private static String maskName(String value) {
        String trimmed = value.trim();
        if (trimmed.length() <= 1) {
            return "Khách hàng BeautyStore";
        }
        return trimmed.charAt(0) + "***";
    }
}
