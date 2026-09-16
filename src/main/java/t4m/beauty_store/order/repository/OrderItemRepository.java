package t4m.beauty_store.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import t4m.beauty_store.order.entity.OrderItem;
import jakarta.persistence.LockModeType;

import java.util.Optional;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    @Query("""
        select item from OrderItem item
        join fetch item.order orderEntity
        left join fetch orderEntity.user
        where item.id = :id
        """)
    Optional<OrderItem> findByIdWithOrder(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from OrderItem item where item.id = :id")
    Optional<OrderItem> findByIdForUpdate(@Param("id") Long id);
}
