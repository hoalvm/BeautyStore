package t4m.beauty_store.image.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import t4m.beauty_store.image.entity.EvidenceKind;
import t4m.beauty_store.config.StoreTime;
import t4m.beauty_store.image.entity.PendingEvidenceUpload;
import t4m.beauty_store.image.repository.PendingEvidenceUploadRepository;
import t4m.beauty_store.order.entity.Order;
import t4m.beauty_store.order.entity.OrderItem;
import t4m.beauty_store.order.entity.OrderStatus;
import t4m.beauty_store.order.repository.OrderItemRepository;
import t4m.beauty_store.product.service.CloudinaryService;
import t4m.beauty_store.review.repository.ReviewRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EvidenceUploadServiceTests {
    private PendingEvidenceUploadRepository uploadRepository;
    private OrderItemRepository orderItemRepository;
    private CloudinaryService cloudinaryService;
    private EvidenceUploadService service;
    private ReviewRepository reviewRepository;
    private OrderItem deliveredItem;

    @BeforeEach
    void setUp() {
        uploadRepository = mock(PendingEvidenceUploadRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        cloudinaryService = mock(CloudinaryService.class);
        reviewRepository = mock(ReviewRepository.class);
        service = new EvidenceUploadService(
            uploadRepository, orderItemRepository, cloudinaryService, reviewRepository, fixedTime());
        ReflectionTestUtils.setField(service, "pendingTtl", Duration.ofHours(2));
        Order order = Order.builder().id(10L).status(OrderStatus.DELIVERED).build();
        deliveredItem = OrderItem.builder().id(20L).order(order).build();
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void uploadIsPersistedAgainstTheExactOrderItemWithoutExposingPublicId() throws Exception {
        when(orderItemRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(deliveredItem));
        when(uploadRepository.countActiveForScope(eq(EvidenceKind.REVIEW), eq(20L), any()))
            .thenReturn(0L);
        when(cloudinaryService.uploadEvidenceImage(any())).thenReturn(Map.of(
            "url", "https://res.cloudinary.com/demo/image/upload/evidence/a.webp",
            "publicId", "beautystore-products/evidence/a"));

        EvidenceUploadService.UploadedEvidence result = service.upload(
            EvidenceKind.REVIEW, 20L, image());

        assertThat(result.url()).endsWith("/evidence/a.webp");
        verify(uploadRepository).saveAndFlush(argThat(upload ->
            upload.getEvidenceKind() == EvidenceKind.REVIEW
                && upload.getOrder().getId().equals(10L)
                && upload.getOrderItem().getId().equals(20L)
                && upload.getCloudinaryPublicId().endsWith("/evidence/a")
                && upload.getExpiresAt().isAfter(upload.getCreatedAt())));
    }

    @Test
    void sixthActiveImageIsRejectedBeforeNetworkUpload() {
        when(orderItemRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(deliveredItem));
        when(uploadRepository.countActiveForScope(eq(EvidenceKind.RETURN), eq(20L), any()))
            .thenReturn(5L);

        assertThatThrownBy(() -> service.upload(EvidenceKind.RETURN, 20L, image()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("5");
        verifyNoInteractions(cloudinaryService);
    }

    @Test
    void reviewUploadIsRejectedAfterTheOrderItemWasReviewed() {
        when(orderItemRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(deliveredItem));
        when(reviewRepository.existsByOrderItemId(20L)).thenReturn(true);

        assertThatThrownBy(() -> service.upload(EvidenceKind.REVIEW, 20L, image()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("đã được đánh giá");
        verifyNoInteractions(cloudinaryService);
    }

    @Test
    void transactionRollbackDeletesTheNewCloudinaryAsset() throws Exception {
        when(orderItemRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(deliveredItem));
        when(uploadRepository.countActiveForScope(eq(EvidenceKind.REVIEW), eq(20L), any()))
            .thenReturn(0L);
        when(cloudinaryService.uploadEvidenceImage(any())).thenReturn(Map.of(
            "url", "https://res.cloudinary.com/demo/image/upload/evidence/a.webp",
            "publicId", "beautystore-products/evidence/a"));
        TransactionSynchronizationManager.initSynchronization();

        service.upload(EvidenceKind.REVIEW, 20L, image());
        TransactionSynchronizationManager.getSynchronizations().forEach(
            synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(cloudinaryService).deleteImage("beautystore-products/evidence/a");
    }

    @Test
    void onlyUnexpiredUnclaimedUploadsInTheSameScopeCanBeClaimed() {
        String url = "https://res.cloudinary.com/demo/image/upload/evidence/a.webp";
        PendingEvidenceUpload upload = PendingEvidenceUpload.builder()
            .evidenceKind(EvidenceKind.REVIEW)
            .orderItem(deliveredItem)
            .order(deliveredItem.getOrder())
            .url(url)
            .cloudinaryPublicId("beautystore-products/evidence/a")
            .expiresAt(LocalDateTime.now().plusHours(1))
            .build();
        when(uploadRepository.findScopedForUpdate(EvidenceKind.REVIEW, 20L, List.of(url)))
            .thenReturn(List.of(upload));

        assertThat(service.claim(EvidenceKind.REVIEW, 20L, List.of(url)))
            .singleElement()
            .satisfies(image -> {
                assertThat(image.url()).isEqualTo(url);
                assertThat(image.publicId()).endsWith("/a");
            });
        assertThat(upload.getClaimedAt()).isNotNull();

        when(uploadRepository.findScopedForUpdate(EvidenceKind.RETURN, 99L, List.of(url)))
            .thenReturn(List.of());
        assertThatThrownBy(() -> service.claim(EvidenceKind.RETURN, 99L, List.of(url)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("không thuộc");
    }

    @Test
    void cleanupRetainsCloudinaryFailuresForRetryAndRemovesSuccessfulDeletes() {
        PendingEvidenceUpload retry = upload(1L, "evidence/retry");
        PendingEvidenceUpload deleted = upload(2L, "evidence/deleted");
        when(uploadRepository.findTop100ByClaimedAtIsNullAndExpiresAtBeforeOrderByExpiresAtAsc(any()))
            .thenReturn(List.of(retry, deleted));
        when(cloudinaryService.deleteImage("evidence/retry")).thenReturn(false);
        when(cloudinaryService.deleteImage("evidence/deleted")).thenReturn(true);

        service.cleanupExpiredUploads();

        verify(uploadRepository, never()).delete(retry);
        verify(uploadRepository).delete(deleted);
    }

    private PendingEvidenceUpload upload(Long id, String publicId) {
        return PendingEvidenceUpload.builder()
            .id(id)
            .evidenceKind(EvidenceKind.RETURN)
            .order(deliveredItem.getOrder())
            .orderItem(deliveredItem)
            .url("https://res.cloudinary.com/demo/" + id)
            .cloudinaryPublicId(publicId)
            .createdAt(LocalDateTime.now().minusDays(2))
            .expiresAt(LocalDateTime.now().minusDays(1))
            .build();
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("image", "proof.png", "image/png", new byte[]{1});
    }

    private static StoreTime fixedTime() {
        return new StoreTime(Clock.fixed(Instant.parse("2026-06-15T03:00:00Z"), StoreTime.ZONE));
    }
}
