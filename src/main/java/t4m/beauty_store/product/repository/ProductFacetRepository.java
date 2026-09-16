package t4m.beauty_store.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import t4m.beauty_store.product.entity.ProductFacet;
import t4m.beauty_store.product.entity.ProductFacetType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductFacetRepository extends JpaRepository<ProductFacet, Long> {
    Optional<ProductFacet> findByTypeAndCodeIgnoreCase(ProductFacetType type, String code);
    List<ProductFacet> findByActiveTrueOrderByTypeAscLabelAsc();
    List<ProductFacet> findByIdIn(Collection<Long> ids);
}
