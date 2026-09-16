package t4m.beauty_store.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import t4m.beauty_store.product.entity.ProductVariant;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    Optional<ProductVariant> findByIdAndActiveTrue(Long id);

    @Query("SELECT v FROM ProductVariant v JOIN v.product p " +
           "LEFT JOIN p.category c LEFT JOIN p.brandEntity b " +
           "WHERE v.id = :id AND v.active = true AND p.active = true " +
           "AND (c IS NULL OR c.active = true) AND (b IS NULL OR b.active = true)")
    Optional<ProductVariant> findByIdAndActiveTrueAndProductActiveTrue(@Param("id") Long id);

    List<ProductVariant> findByProductIdOrderByDefaultVariantDescIdAsc(Long productId);
    List<ProductVariant> findByProductIdAndActiveTrueOrderByDefaultVariantDescIdAsc(Long productId);
    Optional<ProductVariant> findFirstByProductIdAndDefaultVariantTrue(Long productId);
    boolean existsBySkuIgnoreCase(String sku);
    boolean existsBySkuIgnoreCaseAndIdNot(String sku, Long id);
    boolean existsByBarcodeIgnoreCase(String barcode);
    boolean existsByBarcodeIgnoreCaseAndIdNot(String barcode, Long id);
    long countByProductIdAndActiveTrue(Long productId);
}
