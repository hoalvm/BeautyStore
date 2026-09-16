package t4m.beauty_store.voucher.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import t4m.beauty_store.voucher.entity.Voucher;
import t4m.beauty_store.voucher.entity.VoucherUsage;
import t4m.beauty_store.auth.entity.User;

import java.util.List;

@Repository
public interface VoucherUsageRepository extends JpaRepository<VoucherUsage, Long> {
    
    @Query("SELECT COUNT(vu) FROM VoucherUsage vu WHERE vu.voucher = :voucher AND vu.user = :user")
    long countByVoucherAndUser(@Param("voucher") Voucher voucher, @Param("user") User user);

    @Query("""
        SELECT COUNT(vu) FROM VoucherUsage vu
        WHERE vu.voucher = :voucher
          AND (vu.user = :user OR vu.guestIdentifierHash = :identifierHash)
        """)
    long countByVoucherAndUserOrIdentifierHash(
        @Param("voucher") Voucher voucher,
        @Param("user") User user,
        @Param("identifierHash") String identifierHash);

    long countByVoucherAndGuestIdentifierHash(Voucher voucher, String guestIdentifierHash);
    
    List<VoucherUsage> findByVoucher(Voucher voucher);
    
    List<VoucherUsage> findByUser(User user);
    boolean existsByUserId(Long userId);
    
    // Find and delete the most recent usage for restore operation
    @Query("SELECT vu FROM VoucherUsage vu WHERE vu.voucher = :voucher AND vu.user = :user ORDER BY vu.usedAt DESC")
    List<VoucherUsage> findTopByVoucherAndUserOrderByUsedAtDesc(@Param("voucher") Voucher voucher, @Param("user") User user);

    List<VoucherUsage> findByOrderId(Long orderId);
}
