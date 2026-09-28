package t4m.beauty_store.order.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;
import t4m.beauty_store.order.entity.Order;
import t4m.beauty_store.order.entity.OrderStatus;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    @EntityGraph(attributePaths = {"orderItems", "shipper"})
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"orderItems", "shipper"})
    Optional<Order> findByOrderNumber(String orderNumber);

    @Override
    @EntityGraph(attributePaths = {"orderItems", "shipper"})
    Optional<Order> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"orderItems", "shipper"})
    Page<Order> findAll(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderNumber = :orderNumber")
    Optional<Order> findByOrderNumberForUpdate(@Param("orderNumber") String orderNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    @Query("select o from Order o where o.status = t4m.beauty_store.order.entity.OrderStatus.PENDING_PAYMENT " +
           "and o.inventoryCommitted = false and o.reservationExpiresAt < :now")
    List<Order> findExpiredPaymentReservations(@Param("now") LocalDateTime now);
    
    // Admin methods
    @EntityGraph(attributePaths = {"orderItems", "shipper"})
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);
    @EntityGraph(attributePaths = {"orderItems", "shipper"})
    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);
    long countByStatus(OrderStatus status);

    @Query("select coalesce(sum(o.totalAmount), 0) from Order o where o.status = t4m.beauty_store.order.entity.OrderStatus.DELIVERED")
    BigDecimal sumDeliveredRevenue();

    @Query("select coalesce(sum(o.totalAmount), 0) from Order o where o.status = t4m.beauty_store.order.entity.OrderStatus.DELIVERED and o.createdAt >= :from and o.createdAt < :to")
    BigDecimal sumDeliveredRevenueBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime from, LocalDateTime to);
    
    // Shipper methods
    @EntityGraph(attributePaths = {"orderItems", "shipper"})
    List<Order> findByShipperIdAndStatus(Long shipperId, OrderStatus status);
    @EntityGraph(attributePaths = {"orderItems", "shipper"})
    List<Order> findByShipperId(Long shipperId);
    long countByShipperIdAndStatus(Long shipperId, OrderStatus status);
    long countByShipperId(Long shipperId);
    boolean existsByUserIdOrShipperId(Long userId, Long shipperId);

    long countByUserIdAndPaymentMethodIgnoreCaseAndStatusIn(
        Long userId, String paymentMethod, List<OrderStatus> statuses);
    long countByCheckoutIdentityHashAndPaymentMethodIgnoreCaseAndStatusIn(
        String checkoutIdentityHash, String paymentMethod, List<OrderStatus> statuses);
    long countByCustomerEmailIgnoreCaseAndPaymentMethodIgnoreCaseAndStatusIn(
        String customerEmail, String paymentMethod, List<OrderStatus> statuses);
    long countByCustomerPhoneAndPaymentMethodIgnoreCaseAndStatusIn(
        String customerPhone, String paymentMethod, List<OrderStatus> statuses);

}
