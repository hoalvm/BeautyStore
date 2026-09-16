package t4m.beauty_store.shipper.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import t4m.beauty_store.auth.entity.User;
import t4m.beauty_store.auth.repository.UserRepository;
import t4m.beauty_store.order.entity.Order;
import t4m.beauty_store.order.entity.OrderStatus;
import t4m.beauty_store.order.repository.OrderRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ShipperServiceTests {
    private OrderRepository orderRepository;
    private UserRepository userRepository;
    private ShipperService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        userRepository = mock(UserRepository.class);
        service = new ShipperService(orderRepository, userRepository);
    }

    @Test
    void acceptUsesWriteLockAndAssignsOnlyAProcessingOrder() {
        User shipper = shipper(10L, "shipper@beautystore.test");
        Order order = order(OrderStatus.PROCESSING, null);
        when(userRepository.findByEmail(shipper.getEmail())).thenReturn(Optional.of(shipper));
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        Order accepted = service.acceptOrder(1L, shipper.getEmail());

        assertThat(accepted.getStatus()).isEqualTo(OrderStatus.SHIPPING);
        assertThat(accepted.getShipper()).isSameAs(shipper);
        verify(orderRepository).findByIdForUpdate(1L);
        verify(orderRepository, never()).findById(1L);
    }

    @Test
    void serializedSecondAcceptanceCannotStealAnAssignedOrder() {
        User first = shipper(10L, "first@beautystore.test");
        User second = shipper(11L, "second@beautystore.test");
        Order order = order(OrderStatus.PROCESSING, null);
        when(userRepository.findByEmail(first.getEmail())).thenReturn(Optional.of(first));
        when(userRepository.findByEmail(second.getEmail())).thenReturn(Optional.of(second));
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        service.acceptOrder(1L, first.getEmail());

        assertThatThrownBy(() -> service.acceptOrder(1L, second.getEmail()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(order.getShipper()).isSameAs(first);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPING);
        verify(orderRepository, times(1)).save(order);
    }

    @Test
    void completeAndFailBothReloadTheOrderWithAWriteLock() {
        User shipper = shipper(10L, "shipper@beautystore.test");
        Order completing = order(OrderStatus.SHIPPING, shipper);
        Order failing = order(OrderStatus.SHIPPING, shipper);
        when(userRepository.findByEmail(shipper.getEmail())).thenReturn(Optional.of(shipper));
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(completing));
        when(orderRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(failing));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order completed = service.completeDelivery(1L, shipper.getEmail());
        Order failed = service.failDelivery(2L, shipper.getEmail(), "Customer unavailable");

        assertThat(completed.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(completed.getDeliveredAt()).isNotNull();
        assertThat(completed.getPaymentStatus()).isEqualTo("COD_PAID");
        assertThat(failed.getStatus()).isEqualTo(OrderStatus.FAILED);
        verify(orderRepository).findByIdForUpdate(1L);
        verify(orderRepository).findByIdForUpdate(2L);
        verify(orderRepository, never()).findById(anyLong());
    }

    private static User shipper(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setPasswd("unused");
        user.setActivated(true);
        return user;
    }

    private static Order order(OrderStatus status, User shipper) {
        return Order.builder()
            .id(1L)
            .orderNumber("ORD-SHIPPER-TEST")
            .customerName("Customer")
            .customerEmail("customer@beautystore.test")
            .customerPhone("0900000000")
            .shippingAddress("Ho Chi Minh City")
            .paymentMethod("COD")
            .paymentStatus("COD_PENDING")
            .totalAmount(BigDecimal.valueOf(100_000))
            .inventoryCommitted(true)
            .status(status)
            .shipper(shipper)
            .build();
    }
}
