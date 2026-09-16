package t4m.beauty_store.support.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import t4m.beauty_store.support.entity.SupportSession;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupportSessionRepository extends JpaRepository<SupportSession, Long> {
    Optional<SupportSession> findBySessionId(String sessionId);

    Optional<SupportSession> findByUserId(Long userId);

    Optional<SupportSession> findByGuestTokenHash(String guestTokenHash);

    List<SupportSession> findByStatusOrderByUpdatedAtDesc(String status);
}
