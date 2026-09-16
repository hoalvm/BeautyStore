package t4m.beauty_store.favorite.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import t4m.beauty_store.favorite.entity.Favorite;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    
    List<Favorite> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT f FROM Favorite f WHERE f.user.id = :userId AND f.product.active = true " +
           "ORDER BY f.createdAt DESC")
    List<Favorite> findActiveByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);
    
    Optional<Favorite> findByUserIdAndProductId(Long userId, Long productId);
    
    boolean existsByUserIdAndProductId(Long userId, Long productId);

    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM Favorite f " +
           "WHERE f.user.id = :userId AND f.product.id = :productId AND f.product.active = true")
    boolean existsActiveByUserIdAndProductId(@Param("userId") Long userId,
                                              @Param("productId") Long productId);
    
    void deleteByUserIdAndProductId(Long userId, Long productId);
    
    @Query("SELECT COUNT(f) FROM Favorite f WHERE f.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(f) FROM Favorite f WHERE f.user.id = :userId AND f.product.active = true")
    long countActiveByUserId(@Param("userId") Long userId);
    
    @Query("SELECT f.product.id FROM Favorite f WHERE f.user.id = :userId")
    List<Long> findProductIdsByUserId(@Param("userId") Long userId);

    @Query("SELECT f.product.id FROM Favorite f WHERE f.user.id = :userId AND f.product.active = true")
    List<Long> findActiveProductIdsByUserId(@Param("userId") Long userId);
}
