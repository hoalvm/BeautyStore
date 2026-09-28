package t4m.beauty_store.support.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import t4m.beauty_store.support.entity.SupportMessage;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {
    List<SupportMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    Optional<SupportMessage> findTopBySessionIdOrderByCreatedAtDescIdDesc(String sessionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update SupportMessage message
           set message.isRead = true
         where message.sessionId = :sessionId
           and message.senderType <> :readerType
           and message.isRead = false
        """)
    int markUnreadFromOtherSenderAsRead(
        @Param("sessionId") String sessionId,
        @Param("readerType") String readerType);

    long countBySessionIdAndSenderTypeAndIsReadFalse(String sessionId, String senderType);
}
