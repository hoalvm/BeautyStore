package t4m.beauty_store.returns.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import t4m.beauty_store.auth.entity.User;
import t4m.beauty_store.config.StoreTime;
import t4m.beauty_store.order.entity.Order;
import t4m.beauty_store.order.entity.OrderItem;
import t4m.beauty_store.order.entity.AllocationStatus;
import t4m.beauty_store.order.entity.OrderStatus;
import t4m.beauty_store.order.repository.OrderItemRepository;
import t4m.beauty_store.order.repository.OrderRepository;
import t4m.beauty_store.order.service.GuestOrderAccessService;
import t4m.beauty_store.image.entity.EvidenceKind;
import t4m.beauty_store.image.service.EvidenceUploadService;
import t4m.beauty_store.returns.dto.ReturnCreateRequest;
import t4m.beauty_store.returns.dto.ReturnResponse;
import t4m.beauty_store.returns.entity.ReturnItem;
import t4m.beauty_store.returns.entity.ReturnItemBatchRestock;
import t4m.beauty_store.returns.entity.ReturnReason;
import t4m.beauty_store.returns.entity.ReturnRequest;
import t4m.beauty_store.returns.entity.ReturnStatus;
import t4m.beauty_store.returns.entity.ReturnStatusHistory;
import t4m.beauty_store.returns.repository.ReturnItemBatchRestockRepository;
import t4m.beauty_store.returns.repository.ReturnRequestRepository;
import t4m.beauty_store.product.entity.InventoryBatch;
import t4m.beauty_store.product.repository.InventoryBatchRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.net.URI;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReturnService {
    private static final Duration STANDARD_WINDOW = Duration.ofDays(7);
    private static final Duration DELIVERY_ISSUE_WINDOW = Duration.ofHours(48);

    private final ReturnRequestRepository returnRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final GuestOrderAccessService guestOrderAccessService;
    private final InventoryBatchRepository batchRepository;
    private final ReturnItemBatchRestockRepository batchRestockRepository;
    private final EvidenceUploadService evidenceUploadService;

    @Transactional
    public ReturnResponse create(ReturnCreateRequest request, User user, String guestToken) {
        Order order = orderRepository.findByOrderNumberForUpdate(request.getOrderNumber().trim())
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng"));
        authorize(order, user, guestToken);
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalArgumentException("Chỉ có thể yêu cầu đổi trả cho đơn đã giao");
        }
        LocalDateTime deliveredAt = order.getDeliveredAt() != null ? order.getDeliveredAt() : order.getUpdatedAt();
        if (deliveredAt == null || deliveredAt.plus(STANDARD_WINDOW).isBefore(StoreTime.now())) {
            throw new IllegalArgumentException("Đơn hàng đã quá thời hạn đổi trả 7 ngày");
        }

        ReturnRequest result = ReturnRequest.builder()
            .order(order)
            .user(user)
            .customerEmail(order.getCustomerEmail())
            .status(ReturnStatus.REQUESTED)
            .build();
        result.addHistory(ReturnStatusHistory.builder()
            .action("CREATED")
            .toStatus(ReturnStatus.REQUESTED)
            .note("Khách hàng đã gửi yêu cầu đổi trả")
            .changedBy(user == null ? "GUEST" : user.getEmail())
            .build());
        Set<Long> uniqueItems = new HashSet<>();
        for (ReturnCreateRequest.Item requested : request.getItems()) {
            if (!uniqueItems.add(requested.getOrderItemId())) {
                throw new IllegalArgumentException("Một sản phẩm chỉ được khai báo một lần trong yêu cầu");
            }
            OrderItem orderItem = orderItemRepository.findById(requested.getOrderItemId())
                .filter(item -> item.getOrder().getId().equals(order.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Sản phẩm không thuộc đơn hàng"));
            if (requested.getQuantity() > orderItem.getQuantity()) {
                throw new IllegalArgumentException("Số lượng đổi trả vượt quá số lượng đã mua");
            }
            long alreadyClaimed = returnRepository.sumClaimedQuantity(
                orderItem.getId(), ReturnStatus.REJECTED);
            if (alreadyClaimed + requested.getQuantity() > orderItem.getQuantity()) {
                throw new IllegalArgumentException(
                    "Tổng số lượng đang yêu cầu đổi trả vượt quá số lượng đã mua");
            }
            if (requested.getReason() == ReturnReason.UNOPENED_CHANGE_OF_MIND
                    && !Boolean.TRUE.equals(requested.getUnopenedAndSealed())) {
                throw new IllegalArgumentException("Sản phẩm đổi ý phải còn nguyên seal");
            }
            List<String> imageUrls = requested.getImageUrls() == null || requested.getImageUrls().isEmpty()
                ? List.of()
                : evidenceUploadService.claim(
                    EvidenceKind.RETURN, orderItem.getId(), requested.getImageUrls()).stream()
                    .map(EvidenceUploadService.ClaimedEvidence::url)
                    .toList();
            if (isDeliveryIssue(requested.getReason())) {
                if (deliveredAt.plus(DELIVERY_ISSUE_WINDOW).isBefore(StoreTime.now())) {
                    throw new IllegalArgumentException("Hàng sai, lỗi hoặc hư hỏng phải được báo trong 48 giờ");
                }
                if (imageUrls.isEmpty()) {
                    throw new IllegalArgumentException("Vui lòng cung cấp ảnh cho hàng sai, lỗi hoặc hư hỏng");
                }
            }
            result.addItem(ReturnItem.builder()
                .orderItem(orderItem)
                .quantity(requested.getQuantity())
                .reason(requested.getReason())
                .unopenedAndSealed(requested.getUnopenedAndSealed())
                .details(clean(requested.getDetails()))
                .imageUrls(imageUrls)
                .build());
        }
        return ReturnResponse.fromEntity(returnRepository.save(result));
    }

    @Transactional(readOnly = true)
    public Page<ReturnResponse> adminList(ReturnStatus status, Pageable pageable) {
        Page<ReturnRequest> requests = status == null
            ? returnRepository.findAll(pageable)
            : returnRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return requests.map(ReturnResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public ReturnResponse get(Long id, User user, String guestToken) {
        ReturnRequest request = returnRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu đổi trả"));
        authorize(request.getOrder(), user, guestToken);
        return ReturnResponse.fromEntity(request);
    }

    @Transactional(readOnly = true)
    public List<ReturnResponse> listForOrder(String orderNumber, User user, String guestToken) {
        Order order = orderRepository.findByOrderNumber(orderNumber.trim())
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng"));
        authorize(order, user, guestToken);
        return returnRepository.findByOrderIdOrderByCreatedAtDesc(order.getId()).stream()
            .map(ReturnResponse::fromEntity)
            .toList();
    }

    @Transactional
    public ReturnResponse transition(Long id, ReturnStatus status, String note,
                                     String refundReference, String changedBy) {
        if (status == null) throw new IllegalArgumentException("Trạng thái đổi trả là bắt buộc");
        ReturnRequest request = returnRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu đổi trả"));
        ReturnStatus previous = request.getStatus();
        validateTransition(previous, status, refundReference);
        request.setStatus(status);
        request.setAdminNote(clean(note));
        if (status == ReturnStatus.REFUNDED) {
            request.setRefundReference(refundReference.trim());
        }
        request.addHistory(ReturnStatusHistory.builder()
            .action("STATUS_CHANGED")
            .fromStatus(previous)
            .toStatus(status)
            .note(clean(note))
            .changedBy(clean(changedBy))
            .build());
        return ReturnResponse.fromEntity(returnRepository.save(request));
    }

    /**
     * Restocks only explicitly approved return items, back into the exact batches
     * originally allocated to their order item. Order-item and batch locks make
     * retries and concurrent partial return requests safe.
     */
    @Transactional
    public ReturnResponse restock(Long id, Set<Long> returnItemIds, String changedBy) {
        if (returnItemIds == null || returnItemIds.isEmpty()) {
            throw new IllegalArgumentException("Cần chọn ít nhất một sản phẩm đủ điều kiện nhập lại kho");
        }
        ReturnRequest request = returnRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu đổi trả"));
        if (request.getStatus() != ReturnStatus.RECEIVED) {
            throw new IllegalArgumentException("Chỉ nhập lại kho sau khi đã xác nhận nhận hàng trả");
        }
        List<ReturnItem> selected = request.getItems().stream()
            .filter(item -> returnItemIds.contains(item.getId()))
            .sorted(Comparator.comparing(item -> item.getOrderItem().getId()))
            .toList();
        if (selected.size() != returnItemIds.size()) {
            throw new IllegalArgumentException("Sản phẩm đổi trả không thuộc yêu cầu này");
        }
        if (selected.stream().anyMatch(item -> Boolean.TRUE.equals(item.getRestocked()))) {
            throw new IllegalArgumentException("Một hoặc nhiều sản phẩm đã được nhập lại kho trước đó");
        }

        selected.stream().map(item -> item.getOrderItem().getId()).distinct().sorted()
            .forEach(orderItemId -> orderItemRepository.findByIdForUpdate(orderItemId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy dòng đơn hàng gốc")));

        List<Long> batchIds = selected.stream()
            .flatMap(item -> item.getOrderItem().getBatchAllocations().stream())
            .filter(allocation -> allocation.getStatus() == AllocationStatus.COMMITTED)
            .map(allocation -> allocation.getInventoryBatch().getId())
            .filter(Objects::nonNull)
            .distinct().sorted().toList();
        if (batchIds.isEmpty()) {
            throw new IllegalStateException("Đơn hàng không có lịch sử phân bổ lô đã xuất");
        }
        Map<Long, InventoryBatch> lockedBatches = batchRepository.findAllByIdForUpdate(batchIds).stream()
            .collect(Collectors.toMap(InventoryBatch::getId, Function.identity()));
        if (lockedBatches.size() != batchIds.size()) {
            throw new IllegalStateException("Không tìm thấy đầy đủ lô tồn kho gốc");
        }

        for (ReturnItem item : selected) {
            int remaining = item.getQuantity();
            var allocations = item.getOrderItem().getBatchAllocations().stream()
                .filter(allocation -> allocation.getStatus() == AllocationStatus.COMMITTED)
                .sorted(Comparator
                    .comparing((t4m.beauty_store.order.entity.OrderItemBatchAllocation allocation) ->
                        allocation.getInventoryBatch().getExpiryDate())
                    .thenComparing(allocation -> allocation.getInventoryBatch().getId()))
                .toList();
            for (var allocation : allocations) {
                if (remaining == 0) break;
                Long batchId = allocation.getInventoryBatch().getId();
                long alreadyRestocked = batchRestockRepository.sumRestockedForOrderItemAndBatch(
                    item.getOrderItem().getId(), batchId);
                int capacity = Math.max(0, allocation.getQuantity() - Math.toIntExact(alreadyRestocked));
                int quantity = Math.min(remaining, capacity);
                if (quantity == 0) continue;
                InventoryBatch batch = lockedBatches.get(batchId);
                batch.setQuantityOnHand(batch.getQuantityOnHand() + quantity);
                item.addBatchRestock(ReturnItemBatchRestock.builder()
                    .inventoryBatch(batch)
                    .quantity(quantity)
                    .build());
                remaining -= quantity;
            }
            if (remaining != 0) {
                throw new IllegalStateException("Số lượng nhập lại vượt lịch sử lô đã xuất");
            }
            item.setRestocked(true);
            item.setRestockedAt(StoreTime.now());
        }
        batchRepository.saveAll(lockedBatches.values());
        request.addHistory(ReturnStatusHistory.builder()
            .action("BATCH_RESTOCKED")
            .fromStatus(request.getStatus())
            .toStatus(request.getStatus())
            .note("Đã nhập lại kho " + selected.size() + " dòng hàng đủ điều kiện")
            .changedBy(clean(changedBy))
            .build());
        return ReturnResponse.fromEntity(returnRepository.save(request));
    }

    private void authorize(Order order, User user, String token) {
        if (order.getUser() != null) {
            if (user == null || !order.getUser().getId().equals(user.getId())) {
                throw new IllegalArgumentException("Bạn không có quyền thao tác đơn hàng này");
            }
            return;
        }
        Order authorized = guestOrderAccessService.requireOrderAccess(order.getOrderNumber(), token);
        if (!authorized.getId().equals(order.getId())) {
            throw new IllegalArgumentException("Bạn không có quyền thao tác đơn hàng này");
        }
    }

    private static boolean isDeliveryIssue(ReturnReason reason) {
        return reason == ReturnReason.WRONG_ITEM || reason == ReturnReason.DAMAGED
            || reason == ReturnReason.DEFECTIVE;
    }

    private static void validateTransition(ReturnStatus current, ReturnStatus next, String refundReference) {
        boolean valid = switch (current) {
            case REQUESTED -> next == ReturnStatus.APPROVED || next == ReturnStatus.REJECTED;
            case APPROVED -> next == ReturnStatus.RECEIVED || next == ReturnStatus.REJECTED;
            case RECEIVED -> next == ReturnStatus.REFUNDED || next == ReturnStatus.REJECTED;
            case REJECTED, REFUNDED -> false;
        };
        if (!valid) {
            throw new IllegalArgumentException("Trạng thái đổi trả không hợp lệ");
        }
        if (next == ReturnStatus.REFUNDED && (refundReference == null || refundReference.isBlank())) {
            throw new IllegalArgumentException("Cần nhập mã tham chiếu hoàn tiền");
        }
        if (refundReference != null && refundReference.trim().length() > 255) {
            throw new IllegalArgumentException("Mã tham chiếu hoàn tiền không được quá 255 ký tự");
        }
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
                    throw new IllegalArgumentException("URL ảnh bằng chứng không hợp lệ");
                }
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("URL ảnh bằng chứng không hợp lệ");
            }
            result.add(normalized);
        }
        if (result.size() > 5) throw new IllegalArgumentException("Mỗi sản phẩm có tối đa 5 ảnh bằng chứng");
        return List.copyOf(result);
    }
}
