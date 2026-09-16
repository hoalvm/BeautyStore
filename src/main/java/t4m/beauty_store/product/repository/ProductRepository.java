package t4m.beauty_store.product.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import t4m.beauty_store.product.entity.Product;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);
    List<Product> findByFeaturedTrueAndActiveTrue();

    @Query("SELECT p FROM Product p LEFT JOIN p.category c LEFT JOIN p.brandEntity b " +
           "WHERE p.active = true AND (c IS NULL OR c.active = true) AND (b IS NULL OR b.active = true)")
    Page<Product> findByActiveTrue(Pageable pageable);

    @Query("SELECT p FROM Product p LEFT JOIN p.category c LEFT JOIN p.brandEntity b " +
           "WHERE p.active = true AND (c IS NULL OR c.active = true) AND (b IS NULL OR b.active = true)")
    List<Product> findAllByActiveTrue();

    @Query("SELECT DISTINCT p FROM Product p " +
           "LEFT JOIN FETCH p.brandEntity " +
           "LEFT JOIN FETCH p.category c " +
           "LEFT JOIN FETCH c.parent " +
           "WHERE p.id IN :ids")
    List<Product> findCatalogPageByIdIn(@Param("ids") List<Long> ids);

    Optional<Product> findBySlug(String slug);
    boolean existsBySlugIgnoreCase(String slug);
    boolean existsBySlugIgnoreCaseAndIdNot(String slug, Long id);
}
