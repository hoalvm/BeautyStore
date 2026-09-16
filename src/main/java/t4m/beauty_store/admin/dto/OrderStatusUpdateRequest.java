package t4m.beauty_store.admin.dto;

import lombok.Data;
import t4m.beauty_store.order.entity.OrderStatus;

@Data
public class OrderStatusUpdateRequest {
    private OrderStatus status;
}
