package t4m.beauty_store.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import t4m.beauty_store.product.entity.ProductImage;

import java.util.List;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    List<ProductImage> findByProductIdOrderBySortOrderAscIdAsc(Long productId);
    List<ProductImage> findByVariantIdOrderBySortOrderAscIdAsc(Long variantId);
    long countByProductIdAndVariantIsNull(Long productId);
    long countByVariantId(Long variantId);
}
