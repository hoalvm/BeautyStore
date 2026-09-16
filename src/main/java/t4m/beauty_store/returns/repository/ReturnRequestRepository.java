package t4m.beauty_store.returns.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import t4m.beauty_store.returns.entity.ReturnRequest;
import t4m.beauty_store.returns.entity.ReturnStatus;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {
    boolean existsByUserId(Long userId);
    List<ReturnRequest> findByOrderIdOrderByCreatedAtDesc(Long orderId);
    Page<ReturnRequest> findByStatusOrderByCreatedAtDesc(ReturnStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from ReturnRequest request where request.id = :id")
    Optional<ReturnRequest> findByIdForUpdate(@Param("id") Long id);

    @Query("select coalesce(sum(item.quantity), 0) from ReturnItem item " +
           "where item.orderItem.id = :orderItemId and item.returnRequest.status <> :rejected")
    long sumClaimedQuantity(
        @Param("orderItemId") Long orderItemId,
        @Param("rejected") ReturnStatus rejected);
}
