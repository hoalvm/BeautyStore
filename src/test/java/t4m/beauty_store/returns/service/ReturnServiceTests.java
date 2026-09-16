package t4m.beauty_store.returns.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import t4m.beauty_store.auth.entity.User;
import t4m.beauty_store.order.entity.AllocationStatus;
import t4m.beauty_store.order.entity.Order;
import t4m.beauty_store.order.entity.OrderItem;
import t4m.beauty_store.order.entity.OrderItemBatchAllocation;
import t4m.beauty_store.order.entity.OrderStatus;
import t4m.beauty_store.order.repository.OrderItemRepository;
import t4m.beauty_store.order.repository.OrderRepository;
import t4m.beauty_store.order.service.GuestOrderAccessService;
import t4m.beauty_store.image.service.EvidenceUploadService;
import t4m.beauty_store.image.entity.EvidenceKind;
import t4m.beauty_store.product.entity.InventoryBatch;
import t4m.beauty_store.product.repository.InventoryBatchRepository;
import t4m.beauty_store.returns.dto.ReturnCreateRequest;
import t4m.beauty_store.returns.dto.ReturnResponse;
import t4m.beauty_store.returns.entity.ReturnItem;
import t4m.beauty_store.returns.entity.ReturnReason;
import t4m.beauty_store.returns.entity.ReturnRequest;
import t4m.beauty_store.returns.entity.ReturnStatus;
import t4m.beauty_store.returns.repository.ReturnItemBatchRestockRepository;
import t4m.beauty_store.returns.repository.ReturnRequestRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReturnServiceTests {
    private ReturnRequestRepository returnRepository;
    private OrderRepository orderRepository;
    private OrderItemRepository orderItemRepository;
    private GuestOrderAccessService guestOrderAccessService;
    private InventoryBatchRepository batchRepository;
    private ReturnItemBatchRestockRepository restockRepository;
    private EvidenceUploadService evidenceUploadService;
    private ReturnService service;

    @BeforeEach
    void setUp() {
        returnRepository = mock(ReturnRequestRepository.class);
        orderRepository = mock(OrderRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        guestOrderAccessService = mock(GuestOrderAccessService.class);
        batchRepository = mock(InventoryBatchRepository.class);
        restockRepository = mock(ReturnItemBatchRestockRepository.class);
        evidenceUploadService = mock(EvidenceUploadService.class);
        service = new ReturnService(
            returnRepository,
            orderRepository,
            orderItemRepository,
            guestOrderAccessService,
            batchRepository,
            restockRepository,
            evidenceUploadService);
    }

    @Test
    void transitionAppendsAnAuditableHistoryEntry() {
        ReturnRequest request = returnRequest(50L, memberOrder(10L, user(1L, "owner@example.com")),
            ReturnStatus.REQUESTED);
        when(returnRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(request));
        when(returnRepository.save(request)).thenReturn(request);

        ReturnResponse response = service.transition(
            50L, ReturnStatus.APPROVED, "  Seal còn nguyên  ", null, " admin@example.com ");

        assertThat(response.getStatus()).isEqualTo(ReturnStatus.APPROVED);
        assertThat(response.getHistory()).singleElement().satisfies(entry -> {
            assertThat(entry.getAction()).isEqualTo("STATUS_CHANGED");
            assertThat(entry.getFromStatus()).isEqualTo(ReturnStatus.REQUESTED);
            assertThat(entry.getToStatus()).isEqualTo(ReturnStatus.APPROVED);
            assertThat(entry.getNote()).isEqualTo("Seal còn nguyên");
            assertThat(entry.getChangedBy()).isEqualTo("admin@example.com");
        });
    }

    @Test
    void duplicateOrBackwardTransitionIsRejectedWithoutWritingHistory() {
        ReturnRequest request = returnRequest(50L, memberOrder(10L, user(1L, "owner@example.com")),
            ReturnStatus.APPROVED);
        when(returnRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.transition(
            50L, ReturnStatus.APPROVED, null, null, "admin@example.com"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("không hợp lệ");

        assertThat(request.getHistory()).isEmpty();
        verify(returnRepository, never()).save(any());
    }

    @Test
    void memberCanOnlyReadReturnsBelongingToTheirAccount() {
        User owner = user(1L, "owner@example.com");
        ReturnRequest request = returnRequest(50L, memberOrder(10L, owner), ReturnStatus.REQUESTED);
        when(returnRepository.findById(50L)).thenReturn(Optional.of(request));

        assertThat(service.get(50L, user(1L, "same-account@example.com"), null).getId())
            .isEqualTo(50L);
        assertThatThrownBy(() -> service.get(50L, user(2L, "intruder@example.com"), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("không có quyền");
        verifyNoInteractions(guestOrderAccessService);
    }

    @Test
    void guestAccessMustResolveToTheSameOrder() {
        Order order = guestOrder(10L);
        ReturnRequest request = returnRequest(50L, order, ReturnStatus.REQUESTED);
        when(returnRepository.findById(50L)).thenReturn(Optional.of(request));
        when(guestOrderAccessService.requireOrderAccess("ORD-GUEST", "valid-token"))
            .thenReturn(order);

        assertThat(service.get(50L, null, "valid-token").getOrderNumber())
            .isEqualTo("ORD-GUEST");

        when(guestOrderAccessService.requireOrderAccess("ORD-GUEST", "wrong-order-token"))
            .thenReturn(guestOrder(99L));
        assertThatThrownBy(() -> service.get(50L, null, "wrong-order-token"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("không có quyền");
    }

    @Test
    void guestCreationRecordsInitialHistoryAndVerifiedGuestIdentity() {
        Order order = guestOrder(10L);
        OrderItem orderItem = orderItem(101L, order, 2);
        ReturnCreateRequest create = createRequest("ORD-GUEST", itemRequest(101L, 1));
        when(orderRepository.findByOrderNumberForUpdate("ORD-GUEST")).thenReturn(Optional.of(order));
        when(guestOrderAccessService.requireOrderAccess("ORD-GUEST", "valid-token"))
            .thenReturn(order);
        when(orderItemRepository.findById(101L)).thenReturn(Optional.of(orderItem));
        when(returnRepository.sumClaimedQuantity(101L, ReturnStatus.REJECTED)).thenReturn(0L);
        when(returnRepository.save(any(ReturnRequest.class)))
            .thenAnswer(invocation -> {
                ReturnRequest saved = invocation.getArgument(0);
                saved.setId(50L);
                return saved;
            });

        ReturnResponse response = service.create(create, null, "valid-token");

        assertThat(response.getId()).isEqualTo(50L);
        assertThat(response.getHistory()).singleElement().satisfies(entry -> {
            assertThat(entry.getAction()).isEqualTo("CREATED");
            assertThat(entry.getToStatus()).isEqualTo(ReturnStatus.REQUESTED);
            assertThat(entry.getChangedBy()).isEqualTo("GUEST");
        });
    }

    @Test
    void creationClaimsOnlyPersistedImagesScopedToTheOrderItem() {
        User owner = user(1L, "owner@example.com");
        Order order = memberOrder(10L, owner);
        OrderItem orderItem = orderItem(101L, order, 2);
        ReturnCreateRequest.Item requested = itemRequest(101L, 1);
        String url = "https://res.cloudinary.com/demo/evidence/a.webp";
        requested.setImageUrls(List.of(url));
        ReturnCreateRequest create = createRequest("ORD-MEMBER", requested);
        when(orderRepository.findByOrderNumberForUpdate("ORD-MEMBER")).thenReturn(Optional.of(order));
        when(orderItemRepository.findById(101L)).thenReturn(Optional.of(orderItem));
        when(returnRepository.sumClaimedQuantity(101L, ReturnStatus.REJECTED)).thenReturn(0L);
        when(evidenceUploadService.claim(EvidenceKind.RETURN, 101L, List.of(url)))
            .thenReturn(List.of(new EvidenceUploadService.ClaimedEvidence(url, "evidence/a")));
        when(returnRepository.save(any(ReturnRequest.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ReturnResponse response = service.create(create, owner, null);

        assertThat(response.getItems().getFirst().getImageUrls()).containsExactly(url);
        verify(evidenceUploadService).claim(EvidenceKind.RETURN, 101L, List.of(url));
    }

    @Test
    void duplicateItemInOneRequestIsRejectedBeforeItCanBePersisted() {
        User owner = user(1L, "owner@example.com");
        Order order = memberOrder(10L, owner);
        OrderItem orderItem = orderItem(101L, order, 2);
        ReturnCreateRequest.Item first = itemRequest(101L, 1);
        ReturnCreateRequest.Item duplicate = itemRequest(101L, 1);
        ReturnCreateRequest create = createRequest("ORD-MEMBER", first, duplicate);
        when(orderRepository.findByOrderNumberForUpdate("ORD-MEMBER")).thenReturn(Optional.of(order));
        when(orderItemRepository.findById(101L)).thenReturn(Optional.of(orderItem));
        when(returnRepository.sumClaimedQuantity(101L, ReturnStatus.REJECTED)).thenReturn(0L);

        assertThatThrownBy(() -> service.create(create, owner, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("một lần");

        verify(orderItemRepository, times(1)).findById(101L);
        verify(returnRepository, never()).save(any());
    }

    @Test
    void quantitiesClaimedByEarlierNonRejectedRequestsCannotBeClaimedAgain() {
        User owner = user(1L, "owner@example.com");
        Order order = memberOrder(10L, owner);
        OrderItem orderItem = orderItem(101L, order, 2);
        ReturnCreateRequest create = createRequest("ORD-MEMBER", itemRequest(101L, 2));
        when(orderRepository.findByOrderNumberForUpdate("ORD-MEMBER")).thenReturn(Optional.of(order));
        when(orderItemRepository.findById(101L)).thenReturn(Optional.of(orderItem));
        when(returnRepository.sumClaimedQuantity(101L, ReturnStatus.REJECTED)).thenReturn(1L);

        assertThatThrownBy(() -> service.create(create, owner, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Tổng số lượng");
        verify(returnRepository, never()).save(any());
    }

    @Test
    void restockUsesOriginalFefoBatchesAndRetryCannotIncreaseStockTwice() {
        Order order = memberOrder(10L, user(1L, "owner@example.com"));
        OrderItem orderItem = orderItem(101L, order, 5);
        InventoryBatch early = batch(11L, "EARLY", LocalDate.now().plusMonths(3), 5);
        InventoryBatch later = batch(12L, "LATER", LocalDate.now().plusMonths(8), 10);
        orderItem.addBatchAllocation(allocation(early, 2));
        orderItem.addBatchAllocation(allocation(later, 3));
        ReturnItem returnItem = ReturnItem.builder()
            .id(201L)
            .orderItem(orderItem)
            .quantity(3)
            .reason(ReturnReason.UNOPENED_CHANGE_OF_MIND)
            .unopenedAndSealed(true)
            .build();
        ReturnRequest request = returnRequest(50L, order, ReturnStatus.RECEIVED);
        request.addItem(returnItem);

        when(returnRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(request));
        when(orderItemRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(orderItem));
        when(batchRepository.findAllByIdForUpdate(List.of(11L, 12L)))
            .thenReturn(List.of(early, later));
        when(restockRepository.sumRestockedForOrderItemAndBatch(101L, 11L)).thenReturn(1L);
        when(restockRepository.sumRestockedForOrderItemAndBatch(101L, 12L)).thenReturn(0L);
        when(returnRepository.save(request)).thenReturn(request);

        ReturnResponse response = service.restock(50L, Set.of(201L), "admin@example.com");

        assertThat(early.getQuantityOnHand()).isEqualTo(6);
        assertThat(later.getQuantityOnHand()).isEqualTo(12);
        assertThat(returnItem.getRestocked()).isTrue();
        assertThat(response.getItems().getFirst().getBatchRestocks())
            .extracting(ReturnResponse.BatchRestock::getBatchCode,
                ReturnResponse.BatchRestock::getQuantity)
            .containsExactly(tuple("EARLY", 1), tuple("LATER", 2));
        assertThat(response.getHistory()).singleElement()
            .extracting(ReturnResponse.History::getAction)
            .isEqualTo("BATCH_RESTOCKED");

        assertThatThrownBy(() -> service.restock(50L, Set.of(201L), "admin@example.com"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("đã được nhập lại");
        assertThat(early.getQuantityOnHand()).isEqualTo(6);
        assertThat(later.getQuantityOnHand()).isEqualTo(12);
        verify(batchRepository, times(1)).saveAll(any());
    }

    @Test
    void restockIsRejectedUntilReturnedGoodsAreReceived() {
        ReturnRequest request = returnRequest(
            50L, memberOrder(10L, user(1L, "owner@example.com")), ReturnStatus.APPROVED);
        when(returnRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.restock(50L, Set.of(201L), "admin@example.com"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("sau khi đã xác nhận nhận hàng");
        verifyNoInteractions(orderItemRepository, batchRepository, restockRepository);
    }

    private static ReturnRequest returnRequest(Long id, Order order, ReturnStatus status) {
        return ReturnRequest.builder()
            .id(id)
            .order(order)
            .customerEmail(order.getCustomerEmail())
            .status(status)
            .createdAt(LocalDateTime.now().minusHours(1))
            .updatedAt(LocalDateTime.now())
            .build();
    }

    private static Order memberOrder(Long id, User owner) {
        return Order.builder()
            .id(id)
            .orderNumber("ORD-MEMBER")
            .user(owner)
            .customerEmail(owner.getEmail())
            .status(OrderStatus.DELIVERED)
            .deliveredAt(LocalDateTime.now().minusHours(12))
            .build();
    }

    private static Order guestOrder(Long id) {
        return Order.builder()
            .id(id)
            .orderNumber("ORD-GUEST")
            .customerEmail("guest@example.com")
            .status(OrderStatus.DELIVERED)
            .deliveredAt(LocalDateTime.now().minusHours(12))
            .build();
    }

    private static User user(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        return user;
    }

    private static OrderItem orderItem(Long id, Order order, int quantity) {
        return OrderItem.builder()
            .id(id)
            .order(order)
            .productName("Serum phục hồi")
            .variantLabel("30 ml")
            .quantity(quantity)
            .price(BigDecimal.valueOf(250_000))
            .build();
    }

    private static InventoryBatch batch(
            Long id, String code, LocalDate expiryDate, int onHand) {
        return InventoryBatch.builder()
            .id(id)
            .batchCode(code)
            .expiryDate(expiryDate)
            .quantityOnHand(onHand)
            .quantityReserved(0)
            .active(true)
            .build();
    }

    private static OrderItemBatchAllocation allocation(InventoryBatch batch, int quantity) {
        return OrderItemBatchAllocation.builder()
            .inventoryBatch(batch)
            .quantity(quantity)
            .status(AllocationStatus.COMMITTED)
            .build();
    }

    private static ReturnCreateRequest createRequest(
            String orderNumber, ReturnCreateRequest.Item... items) {
        ReturnCreateRequest request = new ReturnCreateRequest();
        request.setOrderNumber(orderNumber);
        request.setItems(List.of(items));
        return request;
    }

    private static ReturnCreateRequest.Item itemRequest(Long orderItemId, int quantity) {
        ReturnCreateRequest.Item item = new ReturnCreateRequest.Item();
        item.setOrderItemId(orderItemId);
        item.setQuantity(quantity);
        item.setReason(ReturnReason.UNOPENED_CHANGE_OF_MIND);
        item.setUnopenedAndSealed(true);
        item.setImageUrls(List.of());
        return item;
    }
}
