package t4m.beauty_store.order.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import t4m.beauty_store.auth.repository.UserRepository;
import t4m.beauty_store.cart.repository.CartRepository;
import t4m.beauty_store.config.StoreProperties;
import t4m.beauty_store.order.entity.AllocationStatus;
import t4m.beauty_store.order.entity.Order;
import t4m.beauty_store.order.entity.OrderItem;
import t4m.beauty_store.order.entity.OrderItemBatchAllocation;
import t4m.beauty_store.order.entity.OrderStatus;
import t4m.beauty_store.order.repository.OrderRepository;
import t4m.beauty_store.product.entity.InventoryBatch;
import t4m.beauty_store.product.entity.Product;
import t4m.beauty_store.product.entity.ProductVariant;
import t4m.beauty_store.product.repository.InventoryBatchRepository;
import t4m.beauty_store.product.service.InventoryService;
import t4m.beauty_store.payment.dto.VNPayPaymentOutcome;
import t4m.beauty_store.payment.dto.VNPayPaymentResult;
import t4m.beauty_store.voucher.service.VoucherService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OrderServiceCommerceTests {
    private OrderRepository orderRepository;
    private InventoryService inventoryService;
    private VoucherService voucherService;
    private OrderService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        inventoryService = mock(InventoryService.class);
        voucherService = mock(VoucherService.class);
        service = new OrderService(
            orderRepository,
            mock(UserRepository.class),
            mock(CartRepository.class),
            mock(InventoryBatchRepository.class),
            inventoryService,
            voucherService,
            new StoreProperties());
    }

    @Test
    void repeatedSuccessfulIpnCommitsReservationOnlyOnce() {
        Order order = pendingPaymentOrder(LocalDateTime.now().plusMinutes(10));
        when(orderRepository.findByOrderNumberForUpdate("ORD-1")).thenReturn(Optional.of(order));

        VNPayPaymentResult first = service.processVNPayIpn(
            "ORD-1", 200_000, true, "TX-1", "NCB", "00");
        VNPayPaymentResult repeated = service.processVNPayIpn(
            "ORD-1", 200_000, true, "TX-1", "NCB", "00");

        assertThat(first.outcome()).isEqualTo(VNPayPaymentOutcome.CONFIRMED);
        assertThat(first.paymentCommitted()).isTrue();
        assertThat(repeated.outcome()).isEqualTo(VNPayPaymentOutcome.ALREADY_CONFIRMED);
        assertThat(repeated.paymentCommitted()).isTrue();
        verify(inventoryService, times(1)).commitReservations(Map.of(10L, 2));
        assertThat(order.getInventoryCommitted()).isTrue();
        assertThat(order.getPaymentStatus()).isEqualTo("PAID");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getOrderItems().getFirst().getBatchAllocations().getFirst().getStatus())
            .isEqualTo(AllocationStatus.COMMITTED);
    }

    @Test
    void lateIpnReleasesExpiredReservationInsteadOfCommittingIt() {
        Order order = pendingPaymentOrder(LocalDateTime.now().minusSeconds(1));
        when(orderRepository.findByOrderNumberForUpdate("ORD-2")).thenReturn(Optional.of(order));

        VNPayPaymentResult result = service.processVNPayIpn(
            "ORD-2", 200_000, true, "TX-2", "NCB", "00");

        assertThat(result.outcome()).isEqualTo(VNPayPaymentOutcome.RECONCILIATION_REQUIRED);
        assertThat(result.paymentCommitted()).isFalse();
        verify(inventoryService).releaseReservations(Map.of(10L, 2));
        verify(inventoryService, never()).commitReservations(anyMap());
        assertThat(order.getPaymentStatus()).isEqualTo("RECONCILIATION_REQUIRED");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getVnpayTransactionNo()).isEqualTo("TX-2");
    }

    @Test
    void successAfterCleanupIsPersistedForManualReconciliation() {
        Order order = pendingPaymentOrder(LocalDateTime.now().minusMinutes(1));
        order.setStatus(OrderStatus.CANCELLED);
        order.setPaymentStatus("EXPIRED");
        order.getOrderItems().getFirst().getBatchAllocations().getFirst()
            .setStatus(AllocationStatus.RELEASED);
        when(orderRepository.findByOrderNumberForUpdate("ORD-3")).thenReturn(Optional.of(order));

        VNPayPaymentResult result = service.processVNPayIpn(
            "ORD-3", 200_000, true, "TX-LATE", "VCB", "00");

        assertThat(result.outcome()).isEqualTo(VNPayPaymentOutcome.RECONCILIATION_REQUIRED);
        assertThat(order.getPaymentStatus()).isEqualTo("RECONCILIATION_REQUIRED");
        assertThat(order.getVnpayTransactionNo()).isEqualTo("TX-LATE");
        verifyNoInteractions(inventoryService);
        verify(orderRepository).save(order);
    }

    @Test
    void ipnAmountIsCheckedInsideLockedTransaction() {
        Order order = pendingPaymentOrder(LocalDateTime.now().plusMinutes(10));
        when(orderRepository.findByOrderNumberForUpdate("ORD-4")).thenReturn(Optional.of(order));

        VNPayPaymentResult result = service.processVNPayIpn(
            "ORD-4", 199_999, true, "TX-4", "NCB", "00");

        assertThat(result.outcome()).isEqualTo(VNPayPaymentOutcome.INVALID_AMOUNT);
        assertThat(order.getPaymentStatus()).isEqualTo("PENDING");
        verifyNoInteractions(inventoryService);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void vnpayRetryPreparationIsNotExposedByOrderService() {
        // A VNPay TxnRef is single-use. Retrying an existing order could let a
        // delayed IPN from the first browser session race the replacement link.
        assertThat(java.util.Arrays.stream(OrderService.class.getDeclaredMethods())
            .map(java.lang.reflect.Method::getName))
            .doesNotContain("prepareVNPayPaymentRetry");
    }

    @Test
    void adminCannotBypassVnpayConfirmation() {
        Order order = pendingPaymentOrder(LocalDateTime.now().plusMinutes(10));
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.updateOrderStatus(1L, OrderStatus.PROCESSING))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("PENDING_PAYMENT");
        verifyNoInteractions(inventoryService);
    }

    @Test
    void adminCannotDirectlyCancelAPaidVnpayOrder() {
        Order order = pendingPaymentOrder(LocalDateTime.now().plusMinutes(10));
        order.setStatus(OrderStatus.CONFIRMED);
        order.setPaymentStatus("PAID");
        order.setInventoryCommitted(true);
        order.getOrderItems().getFirst().getBatchAllocations().getFirst()
            .setStatus(AllocationStatus.COMMITTED);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.updateOrderStatus(1L, OrderStatus.CANCELLED))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("VNPay");

        verifyNoInteractions(inventoryService);
        verifyNoInteractions(voucherService);
        verify(orderRepository, never()).save(any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void adminCannotSetRefundedWithoutTheReturnWorkflow() {
        Order order = pendingPaymentOrder(LocalDateTime.now().plusMinutes(10));
        order.setStatus(OrderStatus.DELIVERED);
        order.setPaymentStatus("PAID");
        order.setInventoryCommitted(true);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.updateOrderStatus(1L, OrderStatus.REFUNDED))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("mã tham chiếu hoàn tiền");

        verify(orderRepository, never()).save(any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void adminCanStillCancelAnUnpaidCodOrder() {
        Order order = pendingPaymentOrder(LocalDateTime.now().plusMinutes(10));
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentMethod("COD");
        order.setPaymentStatus("COD_PENDING");
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        Order cancelled = service.updateOrderStatus(1L, OrderStatus.CANCELLED);

        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryService).releaseReservations(Map.of(10L, 2));
        verify(orderRepository).save(order);
    }

    @Test
    void browserConfirmationAlsoRequiresCommittedInventory() {
        Order order = pendingPaymentOrder(LocalDateTime.now().plusMinutes(10));
        order.setPaymentStatus("PAID");
        order.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findByOrderNumber("ORD-1")).thenReturn(Optional.of(order));

        assertThat(service.isPaymentConfirmed("ORD-1")).isFalse();
        order.setInventoryCommitted(true);
        assertThat(service.isPaymentConfirmed("ORD-1")).isTrue();
    }

    private static Order pendingPaymentOrder(LocalDateTime expiresAt) {
        Product product = Product.builder()
            .id(1L).name("Serum").active(true)
            .build();
        ProductVariant variant = ProductVariant.builder()
            .id(2L).product(product).sku("BEA-1").price(BigDecimal.valueOf(100_000)).active(true)
            .build();
        InventoryBatch batch = InventoryBatch.builder()
            .id(10L).variant(variant).batchCode("B1")
            .quantityOnHand(2).quantityReserved(2).active(true)
            .expiryDate(java.time.LocalDate.now().plusYears(1)).build();
        OrderItem item = OrderItem.builder()
            .product(product).variant(variant).productName("Serum")
            .quantity(2).price(BigDecimal.valueOf(100_000)).build();
        item.addBatchAllocation(OrderItemBatchAllocation.builder()
            .inventoryBatch(batch).quantity(2).status(AllocationStatus.RESERVED).build());
        Order order = Order.builder()
            .id(1L).orderNumber("ORD-1").status(OrderStatus.PENDING_PAYMENT)
            .paymentMethod("VNPAY").totalAmount(BigDecimal.valueOf(200_000))
            .paymentStatus("PENDING").inventoryCommitted(false)
            .reservationExpiresAt(expiresAt).build();
        order.addItem(item);
        return order;
    }
}
