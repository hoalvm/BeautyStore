package t4m.beauty_store.image.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import t4m.beauty_store.auth.entity.User;
import t4m.beauty_store.auth.repository.UserRepository;
import t4m.beauty_store.order.entity.Order;
import t4m.beauty_store.order.entity.OrderItem;
import t4m.beauty_store.order.entity.OrderStatus;
import t4m.beauty_store.order.repository.OrderItemRepository;
import t4m.beauty_store.order.service.GuestOrderAccessService;
import t4m.beauty_store.image.entity.EvidenceKind;
import t4m.beauty_store.image.service.EvidenceUploadService;

import java.io.IOException;

/** Ownership-gated upload endpoints for review and return evidence images. */
@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class EvidenceUploadController {
    private final EvidenceUploadService evidenceUploadService;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final GuestOrderAccessService guestOrderAccessService;

    @PostMapping(value = "/review", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EvidenceUploadService.UploadedEvidence uploadReviewImage(
            @RequestParam Long orderItemId,
            @RequestParam("image") MultipartFile image,
            @AuthenticationPrincipal UserDetails principal,
            @RequestHeader(value = "X-Order-Token", required = false) String guestToken) throws IOException {
        OrderItem item = orderItemRepository.findByIdWithOrder(orderItemId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm trong đơn hàng"));
        requireDeliveredOwner(item.getOrder(), principal, guestToken);
        return evidenceUploadService.upload(EvidenceKind.REVIEW, orderItemId, image);
    }

    @PostMapping(value = "/return", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EvidenceUploadService.UploadedEvidence uploadReturnEvidence(
            @RequestParam Long orderItemId,
            @RequestParam("image") MultipartFile image,
            @AuthenticationPrincipal UserDetails principal,
            @RequestHeader(value = "X-Order-Token", required = false) String guestToken) throws IOException {
        OrderItem item = orderItemRepository.findByIdWithOrder(orderItemId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng"));
        requireDeliveredOwner(item.getOrder(), principal, guestToken);
        return evidenceUploadService.upload(EvidenceKind.RETURN, orderItemId, image);
    }

    private void requireDeliveredOwner(Order order, UserDetails principal, String guestToken) {
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalArgumentException("Chỉ tải ảnh cho đơn hàng đã giao");
        }
        if (order.getUser() != null) {
            User user = principal == null ? null : userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản"));
            if (user == null || !order.getUser().getId().equals(user.getId())) {
                throw new IllegalArgumentException("Bạn không có quyền tải ảnh cho đơn hàng này");
            }
            return;
        }
        Order authorized = guestOrderAccessService.requireOrderAccess(order.getOrderNumber(), guestToken);
        if (!authorized.getId().equals(order.getId())) {
            throw new IllegalArgumentException("Bạn không có quyền tải ảnh cho đơn hàng này");
        }
    }
}
