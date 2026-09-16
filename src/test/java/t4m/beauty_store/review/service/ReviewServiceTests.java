package t4m.beauty_store.review.service;

import org.junit.jupiter.api.Test;
import t4m.beauty_store.auth.entity.User;
import t4m.beauty_store.image.entity.EvidenceKind;
import t4m.beauty_store.image.service.EvidenceUploadService;
import t4m.beauty_store.order.entity.Order;
import t4m.beauty_store.order.entity.OrderItem;
import t4m.beauty_store.order.entity.OrderStatus;
import t4m.beauty_store.order.repository.OrderItemRepository;
import t4m.beauty_store.order.service.GuestOrderAccessService;
import t4m.beauty_store.product.entity.Product;
import t4m.beauty_store.product.repository.ProductRepository;
import t4m.beauty_store.review.dto.ReviewRequest;
import t4m.beauty_store.review.entity.Review;
import t4m.beauty_store.review.repository.ReviewRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReviewServiceTests {

    @Test
    void createClaimsScopedUploadAndPersistsItsCloudinaryIdentity() {
        ReviewRepository reviewRepository = mock(ReviewRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        GuestOrderAccessService guestOrderAccessService = mock(GuestOrderAccessService.class);
        EvidenceUploadService evidenceUploadService = mock(EvidenceUploadService.class);
        ReviewService service = new ReviewService(
            reviewRepository, orderItemRepository, productRepository,
            guestOrderAccessService, evidenceUploadService);

        User owner = new User();
        owner.setId(1L);
        owner.setEmail("owner@example.com");
        Product product = Product.builder().id(30L).name("Serum").build();
        Order order = Order.builder().id(10L).user(owner).status(OrderStatus.DELIVERED).build();
        OrderItem item = OrderItem.builder().id(20L).order(order).product(product).build();
        String url = "https://res.cloudinary.com/demo/evidence/review.webp";
        ReviewRequest request = new ReviewRequest();
        request.setOrderItemId(20L);
        request.setStars(5);
        request.setImageUrls(List.of(url));
        when(orderItemRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(item));
        when(reviewRepository.existsByOrderItemId(20L)).thenReturn(false);
        when(evidenceUploadService.claim(EvidenceKind.REVIEW, 20L, List.of(url)))
            .thenReturn(List.of(new EvidenceUploadService.ClaimedEvidence(url, "evidence/review")));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(request, owner, null);

        verify(reviewRepository).save(argThat(review -> {
            assertThat(review.getImages()).singleElement().satisfies(image -> {
                assertThat(image.getUrl()).isEqualTo(url);
                assertThat(image.getCloudinaryPublicId()).isEqualTo("evidence/review");
            });
            return true;
        }));
    }
}
