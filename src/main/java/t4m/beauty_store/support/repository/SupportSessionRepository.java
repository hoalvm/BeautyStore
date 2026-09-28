package t4m.beauty_store.support.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import t4m.beauty_store.support.entity.SupportSession;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupportSessionRepository extends JpaRepository<SupportSession, Long> {
    Optional<SupportSession> findBySessionId(String sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from SupportSession session where session.sessionId = :sessionId")
    Optional<SupportSession> findBySessionIdForUpdate(@Param("sessionId") String sessionId);

    Optional<SupportSession> findByUserId(Long userId);

    Optional<SupportSession> findByGuestTokenHash(String guestTokenHash);

    List<SupportSession> findByStatusOrderByUpdatedAtDesc(String status);
}
