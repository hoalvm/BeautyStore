package t4m.beauty_store.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import t4m.beauty_store.order.entity.OrderItemBatchAllocation;

import java.util.List;

public interface OrderItemBatchAllocationRepository extends JpaRepository<OrderItemBatchAllocation, Long> {
    List<OrderItemBatchAllocation> findByOrderItemOrderId(Long orderId);
}
