package t4m.beauty_store.image.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import t4m.beauty_store.image.entity.EvidenceKind;
import t4m.beauty_store.image.entity.PendingEvidenceUpload;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface PendingEvidenceUploadRepository extends JpaRepository<PendingEvidenceUpload, Long> {

    @Query("""
        select count(upload) from PendingEvidenceUpload upload
        where upload.evidenceKind = :kind
          and upload.orderItem.id = :orderItemId
          and upload.claimedAt is null
          and upload.expiresAt > :now
        """)
    long countActiveForScope(
        @Param("kind") EvidenceKind kind,
        @Param("orderItemId") Long orderItemId,
        @Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select upload from PendingEvidenceUpload upload
        where upload.evidenceKind = :kind
          and upload.orderItem.id = :orderItemId
          and upload.url in :urls
        """)
    List<PendingEvidenceUpload> findScopedForUpdate(
        @Param("kind") EvidenceKind kind,
        @Param("orderItemId") Long orderItemId,
        @Param("urls") Collection<String> urls);

    List<PendingEvidenceUpload> findTop100ByClaimedAtIsNullAndExpiresAtBeforeOrderByExpiresAtAsc(
        LocalDateTime now);
}
