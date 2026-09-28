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
import java.time.LocalDate;

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

    @Query(value = """
        SELECT p.id AS productId,
               COALESCE(SUM(CASE WHEN v.active = TRUE AND b.active = TRUE
                    AND b.expiry_date >= :today
                    THEN GREATEST(b.quantity_on_hand - b.quantity_reserved, 0) ELSE 0 END), 0) AS availableStock,
               COALESCE(MIN(CASE WHEN v.active = TRUE THEN v.low_stock_threshold END), 10) AS lowStockThreshold
          FROM products p
          LEFT JOIN product_variants v ON v.product_id = p.id
          LEFT JOIN inventory_batches b ON b.variant_id = v.id
         WHERE p.active = TRUE
         GROUP BY p.id
         ORDER BY p.id DESC
        """, nativeQuery = true)
    List<ProductStockProjection> findActiveProductStock(@Param("today") LocalDate today);

    @Query(value = "select distinct p from Product p left join p.brandEntity b left join p.variants v left join p.facets f " +
        "where lower(p.name) like lower(concat('%', :keyword, '%')) " +
        "or lower(coalesce(p.slug, '')) like lower(concat('%', :keyword, '%')) " +
        "or lower(coalesce(p.description, '')) like lower(concat('%', :keyword, '%')) " +
        "or lower(coalesce(b.name, '')) like lower(concat('%', :keyword, '%')) " +
        "or lower(coalesce(v.sku, '')) like lower(concat('%', :keyword, '%')) " +
        "or lower(coalesce(f.label, '')) like lower(concat('%', :keyword, '%'))",
        countQuery = "select count(distinct p.id) from Product p left join p.brandEntity b left join p.variants v left join p.facets f " +
        "where lower(p.name) like lower(concat('%', :keyword, '%')) " +
        "or lower(coalesce(p.slug, '')) like lower(concat('%', :keyword, '%')) " +
        "or lower(coalesce(p.description, '')) like lower(concat('%', :keyword, '%')) " +
        "or lower(coalesce(b.name, '')) like lower(concat('%', :keyword, '%')) " +
        "or lower(coalesce(v.sku, '')) like lower(concat('%', :keyword, '%')) " +
        "or lower(coalesce(f.label, '')) like lower(concat('%', :keyword, '%'))")
    Page<Product> findAdminSearch(@Param("keyword") String keyword, Pageable pageable);

    Optional<Product> findBySlug(String slug);
    boolean existsBySlugIgnoreCase(String slug);
    boolean existsBySlugIgnoreCaseAndIdNot(String slug, Long id);
}
