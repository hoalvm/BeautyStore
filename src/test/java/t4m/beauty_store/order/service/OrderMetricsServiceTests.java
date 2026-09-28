package t4m.beauty_store.order.service;

import org.junit.jupiter.api.Test;
import t4m.beauty_store.auth.repository.UserRepository;
import t4m.beauty_store.cart.repository.CartRepository;
import t4m.beauty_store.config.StoreProperties;
import t4m.beauty_store.order.entity.OrderStatus;
import t4m.beauty_store.order.repository.OrderRepository;
import t4m.beauty_store.product.repository.InventoryBatchRepository;
import t4m.beauty_store.product.service.InventoryService;
import t4m.beauty_store.voucher.service.VoucherService;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OrderMetricsServiceTests {
    @Test
    void calculatesAverageFromDatabaseAggregates() {
        OrderRepository orders = mock(OrderRepository.class);
        when(orders.countByStatus(OrderStatus.DELIVERED)).thenReturn(4L);
        when(orders.sumDeliveredRevenue()).thenReturn(new BigDecimal("1000001"));
        OrderService service = new OrderService(orders, mock(UserRepository.class), mock(CartRepository.class),
            mock(InventoryBatchRepository.class), mock(InventoryService.class), mock(VoucherService.class),
            new StoreProperties());

        assertThat(service.getAverageOrderValue()).isEqualByComparingTo("250000");
        verify(orders, never()).findAll();
    }
}
