package t4m.beauty_store.image.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import t4m.beauty_store.image.entity.EvidenceKind;
import t4m.beauty_store.image.entity.PendingEvidenceUpload;
import t4m.beauty_store.image.repository.PendingEvidenceUploadRepository;
import t4m.beauty_store.order.entity.OrderItem;
import t4m.beauty_store.order.entity.OrderStatus;
import t4m.beauty_store.order.repository.OrderItemRepository;
import t4m.beauty_store.product.service.CloudinaryService;
import t4m.beauty_store.review.repository.ReviewRepository;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Owns the short-lived bridge between a Cloudinary upload and the review or
 * return transaction that consumes it. Every asset is scoped to one purchased
 * order item, can be claimed once, and is deleted if abandoned.
 */
@Service
@RequiredArgsConstructor
public class EvidenceUploadService {
    public static final int MAX_IMAGES_PER_SCOPE = 5;

    private final PendingEvidenceUploadRepository uploadRepository;
    private final OrderItemRepository orderItemRepository;
    private final CloudinaryService cloudinaryService;
    private final ReviewRepository reviewRepository;

    @Value("${uploads.evidence.pending-ttl:PT24H}")
    private Duration pendingTtl = Duration.ofHours(24);

    @Transactional
    public UploadedEvidence upload(EvidenceKind kind, Long orderItemId, MultipartFile image)
            throws IOException {
        if (kind == null || orderItemId == null) {
            throw new IllegalArgumentException("Phạm vi ảnh bằng chứng không hợp lệ");
        }
        if (pendingTtl == null || pendingTtl.isZero() || pendingTtl.isNegative()) {
            throw new IllegalStateException("Cấu hình thời hạn ảnh bằng chứng không hợp lệ");
        }
        OrderItem orderItem = orderItemRepository.findByIdForUpdate(orderItemId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm trong đơn hàng"));
        if (orderItem.getOrder().getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalArgumentException("Chỉ tải ảnh cho đơn hàng đã giao");
        }
        if (kind == EvidenceKind.REVIEW && reviewRepository.existsByOrderItemId(orderItemId)) {
            throw new IllegalArgumentException("Sản phẩm trong đơn hàng này đã được đánh giá");
        }

        LocalDateTime now = LocalDateTime.now();
        if (uploadRepository.countActiveForScope(kind, orderItemId, now) >= MAX_IMAGES_PER_SCOPE) {
            throw new IllegalArgumentException("Mỗi sản phẩm có tối đa 5 ảnh");
        }

        Map<String, String> uploaded = cloudinaryService.uploadEvidenceImage(image);
        String publicId = uploaded.get("publicId");
        String url = uploaded.get("url");
        boolean synchronizedRollbackCleanup = registerRollbackCleanup(publicId);
        try {
            uploadRepository.saveAndFlush(PendingEvidenceUpload.builder()
                .evidenceKind(kind)
                .order(orderItem.getOrder())
                .orderItem(orderItem)
                .url(url)
                .cloudinaryPublicId(publicId)
                .createdAt(now)
                .expiresAt(now.plus(pendingTtl))
                .build());
        } catch (RuntimeException exception) {
            if (!synchronizedRollbackCleanup) {
                cloudinaryService.deleteImage(publicId);
            }
            throw exception;
        }
        return new UploadedEvidence(url);
    }

    @Transactional
    public List<ClaimedEvidence> claim(
            EvidenceKind kind, Long orderItemId, List<String> requestedUrls) {
        List<String> urls = normalizeUrls(requestedUrls);
        if (urls.isEmpty()) return List.of();

        List<PendingEvidenceUpload> uploads = uploadRepository.findScopedForUpdate(
            kind, orderItemId, urls);
        Map<String, PendingEvidenceUpload> byUrl = uploads.stream()
            .collect(Collectors.toMap(PendingEvidenceUpload::getUrl, Function.identity()));
        LocalDateTime now = LocalDateTime.now();
        for (String url : urls) {
            PendingEvidenceUpload upload = byUrl.get(url);
            if (upload == null || upload.getClaimedAt() != null || !upload.getExpiresAt().isAfter(now)) {
                throw new IllegalArgumentException("Ảnh không thuộc sản phẩm này, đã dùng hoặc đã hết hạn");
            }
        }
        return urls.stream().map(url -> {
            PendingEvidenceUpload upload = byUrl.get(url);
            upload.setClaimedAt(now);
            return new ClaimedEvidence(upload.getUrl(), upload.getCloudinaryPublicId());
        }).toList();
    }

    /** Delete abandoned Cloudinary assets in small, bounded batches. */
    @Scheduled(fixedDelayString = "${uploads.evidence.cleanup-ms:3600000}")
    @Transactional
    public void cleanupExpiredUploads() {
        List<PendingEvidenceUpload> expired = uploadRepository
            .findTop100ByClaimedAtIsNullAndExpiresAtBeforeOrderByExpiresAtAsc(LocalDateTime.now());
        for (PendingEvidenceUpload upload : expired) {
            if (cloudinaryService.deleteImage(upload.getCloudinaryPublicId())) {
                uploadRepository.delete(upload);
            }
        }
    }

    private boolean registerRollbackCleanup(String publicId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return false;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    cloudinaryService.deleteImage(publicId);
                }
            }
        });
        return true;
    }

    private static List<String> normalizeUrls(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            String normalized = value.trim();
            if (normalized.length() > 1000) {
                throw new IllegalArgumentException("URL ảnh quá dài");
            }
            try {
                URI uri = URI.create(normalized);
                if (!"https".equalsIgnoreCase(uri.getScheme())
                        || uri.getHost() == null || uri.getUserInfo() != null) {
                    throw new IllegalArgumentException("URL ảnh không hợp lệ");
                }
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("URL ảnh không hợp lệ");
            }
            result.add(normalized);
        }
        if (result.size() > MAX_IMAGES_PER_SCOPE) {
            throw new IllegalArgumentException("Mỗi sản phẩm có tối đa 5 ảnh");
        }
        return List.copyOf(result);
    }

    public record UploadedEvidence(String url) {}
    public record ClaimedEvidence(String url, String publicId) {}
}
