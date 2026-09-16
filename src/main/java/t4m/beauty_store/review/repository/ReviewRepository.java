package t4m.beauty_store.review.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import t4m.beauty_store.review.entity.Review;
import t4m.beauty_store.review.entity.ReviewStatus;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    boolean existsByOrderItemId(Long orderItemId);
    boolean existsByUserId(Long userId);
    Page<Review> findByProductIdAndStatusOrderByCreatedAtDesc(Long productId, ReviewStatus status, Pageable pageable);
    Page<Review> findByStatusOrderByCreatedAtDesc(ReviewStatus status, Pageable pageable);

    @Query("select avg(r.stars) from Review r where r.product.id = :productId and r.status = 'APPROVED'")
    Double averageApprovedStars(@Param("productId") Long productId);

    long countByProductIdAndStatus(Long productId, ReviewStatus status);
}
