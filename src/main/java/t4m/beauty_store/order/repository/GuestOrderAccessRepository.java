package t4m.beauty_store.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import t4m.beauty_store.order.entity.GuestOrderAccess;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface GuestOrderAccessRepository extends JpaRepository<GuestOrderAccess, Long> {
    Optional<GuestOrderAccess> findTopByOrderIdAndEmailIgnoreCaseOrderByCreatedAtDesc(Long orderId, String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from GuestOrderAccess a where a.id = (" +
           "select max(latest.id) from GuestOrderAccess latest " +
           "where latest.order.id = :orderId and lower(latest.email) = lower(:email))")
    Optional<GuestOrderAccess> findLatestForUpdate(
        @Param("orderId") Long orderId, @Param("email") String email);
    Optional<GuestOrderAccess> findTopByOrderOrderNumberAndAccessTokenHashOrderByCreatedAtDesc(
        String orderNumber, String accessTokenHash);

    @Modifying
    @Transactional
    @Query("delete from GuestOrderAccess access where access.id = :id and access.verifiedAt is null")
    int deleteUnverifiedById(@Param("id") Long id);
}
