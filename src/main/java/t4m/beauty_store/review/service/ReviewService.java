package t4m.beauty_store.review.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import t4m.beauty_store.auth.entity.User;
import t4m.beauty_store.order.entity.Order;
import t4m.beauty_store.order.entity.OrderItem;
import t4m.beauty_store.order.entity.OrderStatus;
import t4m.beauty_store.order.repository.OrderItemRepository;
import t4m.beauty_store.order.service.GuestOrderAccessService;
import t4m.beauty_store.image.entity.EvidenceKind;
import t4m.beauty_store.image.service.EvidenceUploadService;
import t4m.beauty_store.product.entity.Product;
import t4m.beauty_store.product.repository.ProductRepository;
import t4m.beauty_store.review.dto.ReviewRequest;
import t4m.beauty_store.review.dto.ReviewResponse;
import t4m.beauty_store.review.entity.Review;
import t4m.beauty_store.review.entity.ReviewImage;
import t4m.beauty_store.review.entity.ReviewStatus;
import t4m.beauty_store.review.repository.ReviewRepository;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final GuestOrderAccessService guestOrderAccessService;
    private final EvidenceUploadService evidenceUploadService;

    @Transactional
    public ReviewResponse create(ReviewRequest request, User user, String guestToken) {
        OrderItem orderItem = orderItemRepository.findByIdForUpdate(request.getOrderItemId())
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm trong đơn hàng"));
        Order order = orderItem.getOrder();
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalArgumentException("Chỉ có thể đánh giá đơn hàng đã giao");
        }
        if (reviewRepository.existsByOrderItemId(orderItem.getId())) {
            throw new IllegalArgumentException("Sản phẩm trong đơn hàng này đã được đánh giá");
        }

        if (order.getUser() != null) {
            if (user == null || !order.getUser().getId().equals(user.getId())) {
                throw new IllegalArgumentException("Bạn không có quyền đánh giá đơn hàng này");
            }
        } else {
            Order authorized = guestOrderAccessService.requireOrderAccess(order.getOrderNumber(), guestToken);
            if (!authorized.getId().equals(order.getId())) {
                throw new IllegalArgumentException("Bạn không có quyền đánh giá đơn hàng này");
            }
        }

        Review review = Review.builder()
            .product(orderItem.getProduct())
            .orderItem(orderItem)
            .user(user)
            .stars(request.getStars())
            .title(clean(request.getTitle()))
            .content(clean(request.getContent()))
            .skinType(clean(request.getSkinType()))
            .status(ReviewStatus.PENDING)
            .verifiedPurchase(true)
            .build();
        List<EvidenceUploadService.ClaimedEvidence> images =
            request.getImageUrls() == null || request.getImageUrls().isEmpty()
                ? List.of()
                : evidenceUploadService.claim(
                    EvidenceKind.REVIEW, orderItem.getId(), request.getImageUrls());
        for (int index = 0; index < images.size(); index++) {
            EvidenceUploadService.ClaimedEvidence image = images.get(index);
            review.addImage(ReviewImage.builder()
                .url(image.url())
                .cloudinaryPublicId(image.publicId())
                .displayOrder(index)
                .build());
        }
        return ReviewResponse.fromEntity(reviewRepository.save(review));
    }

    @Transactional(readOnly = true)
    public Page<ReviewResponse> publicReviews(Long productId, Pageable pageable) {
        return reviewRepository.findByProductIdAndStatusOrderByCreatedAtDesc(
            productId, ReviewStatus.APPROVED, pageable).map(ReviewResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<ReviewResponse> adminReviews(ReviewStatus status, Pageable pageable) {
        Page<Review> reviews = status == null
            ? reviewRepository.findAll(pageable)
            : reviewRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return reviews.map(ReviewResponse::fromEntity);
    }

    @Transactional
    public ReviewResponse moderate(Long reviewId, ReviewStatus status) {
        if (status == null) throw new IllegalArgumentException("Trạng thái đánh giá là bắt buộc");
        Review review = reviewRepository.findById(reviewId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đánh giá"));
        review.setStatus(status);
        Review saved = reviewRepository.save(review);
        refreshProductRating(review.getProduct().getId());
        return ReviewResponse.fromEntity(saved);
    }

    private void refreshProductRating(Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm"));
        Double average = reviewRepository.averageApprovedStars(productId);
        long count = reviewRepository.countByProductIdAndStatus(productId, ReviewStatus.APPROVED);
        product.setAverageRating(average == null ? 0.0 : average);
        product.setRatingCount(Math.toIntExact(count));
        productRepository.save(product);
    }

    private static String clean(String value) {
        return value == null ? null : value.trim();
    }

    private static List<String> validHttpsUrls(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            String normalized = value.trim();
            try {
                URI uri = URI.create(normalized);
                if (!"https".equalsIgnoreCase(uri.getScheme())
                        || uri.getHost() == null || uri.getUserInfo() != null) {
                    throw new IllegalArgumentException("URL ảnh đánh giá không hợp lệ");
                }
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("URL ảnh đánh giá không hợp lệ");
            }
            result.add(normalized);
        }
        if (result.size() > 5) throw new IllegalArgumentException("Mỗi đánh giá có tối đa 5 ảnh");
        return List.copyOf(result);
    }
}
