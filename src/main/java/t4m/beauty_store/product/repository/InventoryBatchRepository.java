package t4m.beauty_store.product.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import t4m.beauty_store.product.entity.InventoryBatch;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, Long> {
    List<InventoryBatch> findByVariantIdOrderByExpiryDateAscIdAsc(Long variantId);
    Optional<InventoryBatch> findByVariantIdAndBatchCodeIgnoreCase(Long variantId, String batchCode);
    boolean existsByVariantIdAndBatchCodeIgnoreCase(Long variantId, String batchCode);
    boolean existsByVariantIdAndBatchCodeIgnoreCaseAndIdNot(Long variantId, String batchCode, Long id);

    @Query("SELECT COALESCE(SUM(b.quantityOnHand - b.quantityReserved), 0) " +
           "FROM InventoryBatch b WHERE b.variant.id = :variantId AND b.active = true " +
           "AND b.expiryDate >= :today AND b.quantityOnHand > b.quantityReserved")
    int getAvailableStock(@Param("variantId") Long variantId, @Param("today") LocalDate today);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM InventoryBatch b WHERE b.variant.id = :variantId " +
           "AND b.active = true AND b.expiryDate >= :today " +
           "AND b.quantityOnHand > b.quantityReserved ORDER BY b.expiryDate ASC, b.id ASC")
    List<InventoryBatch> findSellableFefoForUpdate(
        @Param("variantId") Long variantId,
        @Param("today") LocalDate today
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM InventoryBatch b WHERE b.id IN :ids ORDER BY b.id ASC")
    List<InventoryBatch> findAllByIdForUpdate(@Param("ids") List<Long> ids);

    @Query("SELECT b FROM InventoryBatch b WHERE b.active = true AND b.expiryDate < :today " +
           "ORDER BY b.expiryDate ASC")
    List<InventoryBatch> findExpired(@Param("today") LocalDate today);

    @Query("SELECT b FROM InventoryBatch b WHERE b.active = true " +
           "AND b.expiryDate BETWEEN :from AND :to ORDER BY b.expiryDate ASC")
    List<InventoryBatch> findExpiringBetween(
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );
}
