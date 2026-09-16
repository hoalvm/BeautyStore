package t4m.beauty_store.product.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import t4m.beauty_store.config.StoreTime;
import t4m.beauty_store.product.entity.ProductFacetType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Database-side query for the public catalog.
 *
 * <p>The query returns only the IDs for one requested page. Product graphs are
 * fetched in a second bounded query by {@code ProductService}; this avoids both
 * loading the complete catalog and paginating over collection fetch joins.</p>
 */
@Repository
@RequiredArgsConstructor
public class ProductCatalogQueryRepository {
    private static final String EFFECTIVE_PRICE_SUBQUERY = """
        (SELECT MIN(COALESCE(sort_variant.discount_price, sort_variant.price))
           FROM product_variants sort_variant
          WHERE sort_variant.product_id = p.id AND sort_variant.active = TRUE)
        """;

    private final EntityManager entityManager;

    public record CatalogQuery(
        String keyword,
        Set<Long> visibleCategoryIds,
        Set<Long> categoryIds,
        Long brandId,
        Map<ProductFacetType, String> facets,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Boolean inStock,
        Boolean onSale,
        String sort
    ) {
    }

    public Page<Long> findProductIds(CatalogQuery criteria, Pageable pageable) {
        SqlParts parts = buildSql(criteria);
        long total = count(parts);
        long offset = pageable.getOffset();

        // JPA's setFirstResult accepts an int. Avoid an overflow for very large
        // (but otherwise valid) PageRequest values and avoid a needless data query.
        if (total == 0 || offset >= total || offset > Integer.MAX_VALUE) {
            return new PageImpl<>(List.of(), pageable, total);
        }

        Query query = entityManager.createNativeQuery(
            "SELECT p.id " + parts.fromAndWhere() + orderBy(criteria.sort()));
        bind(query, parts.parameters());
        query.setFirstResult((int) offset);
        query.setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<Object> rows = query.getResultList();
        List<Long> ids = rows.stream().map(row -> ((Number) row).longValue()).toList();
        return new PageImpl<>(ids, pageable, total);
    }

    private long count(SqlParts parts) {
        Query query = entityManager.createNativeQuery("SELECT COUNT(*) " + parts.fromAndWhere());
        bind(query, parts.parameters());
        return ((Number) query.getSingleResult()).longValue();
    }

    private static SqlParts buildSql(CatalogQuery criteria) {
        StringBuilder sql = new StringBuilder("""
            FROM products p
            LEFT JOIN brands brand ON brand.id = p.brand_id
            WHERE p.active = TRUE
              AND (p.brand_id IS NULL OR brand.active = TRUE)
            """);
        Map<String, Object> parameters = new LinkedHashMap<>();

        Set<Long> visibleCategoryIds = criteria.visibleCategoryIds() == null
            ? Set.of() : criteria.visibleCategoryIds();
        if (visibleCategoryIds.isEmpty()) {
            sql.append(" AND p.category_id IS NULL");
        } else {
            sql.append(" AND (p.category_id IS NULL OR ");
            appendIn(sql, "p.category_id", "visibleCategory", visibleCategoryIds, parameters);
            sql.append(')');
        }

        if (criteria.categoryIds() != null) {
            if (criteria.categoryIds().isEmpty()) {
                sql.append(" AND 1 = 0");
            } else {
                sql.append(" AND ");
                appendIn(sql, "p.category_id", "category", criteria.categoryIds(), parameters);
            }
        }

        if (criteria.brandId() != null) {
            sql.append(" AND p.brand_id = :brandId");
            parameters.put("brandId", criteria.brandId());
        }

        if (criteria.keyword() != null) {
            sql.append("""
                 AND (
                    LOWER(p.name) LIKE :keyword ESCAPE '!'
                    OR LOWER(p.slug) LIKE :keyword ESCAPE '!'
                    OR LOWER(p.description) LIKE :keyword ESCAPE '!'
                    OR LOWER(p.benefits) LIKE :keyword ESCAPE '!'
                    OR LOWER(p.inci) LIKE :keyword ESCAPE '!'
                    OR LOWER(brand.name) LIKE :keyword ESCAPE '!'
                    OR EXISTS (
                        SELECT 1
                          FROM product_facet_assignments keyword_assignment
                          JOIN product_facets keyword_facet
                            ON keyword_facet.id = keyword_assignment.facet_id
                         WHERE keyword_assignment.product_id = p.id
                           AND keyword_facet.active = TRUE
                           AND (LOWER(keyword_facet.code) LIKE :keyword ESCAPE '!'
                                OR LOWER(keyword_facet.label) LIKE :keyword ESCAPE '!')
                    )
                    OR EXISTS (
                        SELECT 1
                          FROM product_variants keyword_variant
                         WHERE keyword_variant.product_id = p.id
                           AND (LOWER(keyword_variant.sku) LIKE :keyword ESCAPE '!'
                                OR LOWER(keyword_variant.barcode) LIKE :keyword ESCAPE '!')
                    )
                 )
                """);
            parameters.put("keyword", '%' + escapeLike(criteria.keyword()) + '%');
        }

        int facetIndex = 0;
        Map<ProductFacetType, String> facets = criteria.facets() == null ? Map.of() : criteria.facets();
        for (Map.Entry<ProductFacetType, String> facet : facets.entrySet()) {
            String typeParameter = "facetType" + facetIndex;
            String codeParameter = "facetCode" + facetIndex;
            sql.append("""
                 AND EXISTS (
                    SELECT 1
                      FROM product_facet_assignments filter_assignment
                      JOIN product_facets filter_facet ON filter_facet.id = filter_assignment.facet_id
                     WHERE filter_assignment.product_id = p.id
                       AND filter_facet.active = TRUE
                       AND filter_facet.facet_type = :%s
                       AND LOWER(filter_facet.code) = :%s
                 )
                """.formatted(typeParameter, codeParameter));
            parameters.put(typeParameter, facet.getKey().name());
            parameters.put(codeParameter, facet.getValue());
            facetIndex++;
        }

        if (criteria.minPrice() != null || criteria.maxPrice() != null) {
            sql.append("""
                 AND EXISTS (
                    SELECT 1
                      FROM product_variants price_variant
                     WHERE price_variant.product_id = p.id
                       AND price_variant.active = TRUE
                """);
            if (criteria.minPrice() != null) {
                sql.append(" AND COALESCE(price_variant.discount_price, price_variant.price) >= :minPrice");
                parameters.put("minPrice", criteria.minPrice());
            }
            if (criteria.maxPrice() != null) {
                sql.append(" AND COALESCE(price_variant.discount_price, price_variant.price) <= :maxPrice");
                parameters.put("maxPrice", criteria.maxPrice());
            }
            sql.append(')');
        }

        if (criteria.inStock() != null) {
            sql.append(Boolean.TRUE.equals(criteria.inStock()) ? " AND EXISTS (" : " AND NOT EXISTS (");
            sql.append("""
                SELECT 1
                  FROM product_variants stock_variant
                  JOIN inventory_batches stock_batch ON stock_batch.variant_id = stock_variant.id
                 WHERE stock_variant.product_id = p.id
                   AND stock_variant.active = TRUE
                   AND stock_batch.active = TRUE
                   AND stock_batch.expiry_date >= :today
                   AND stock_batch.quantity_on_hand > stock_batch.quantity_reserved)
                """);
            parameters.put("today", StoreTime.today());
        }

        if (criteria.onSale() != null) {
            sql.append(Boolean.TRUE.equals(criteria.onSale()) ? " AND EXISTS (" : " AND NOT EXISTS (");
            sql.append("""
                SELECT 1
                  FROM product_variants sale_variant
                 WHERE sale_variant.product_id = p.id
                   AND sale_variant.active = TRUE
                   AND sale_variant.discount_price IS NOT NULL
                   AND sale_variant.price IS NOT NULL
                   AND sale_variant.discount_price < sale_variant.price)
                """);
        }

        return new SqlParts(sql.toString(), parameters);
    }

    private static String orderBy(String requestedSort) {
        String sort = requestedSort == null ? "newest" : requestedSort.toLowerCase(Locale.ROOT);
        return switch (sort) {
            case "price-asc" -> " ORDER BY CASE WHEN " + EFFECTIVE_PRICE_SUBQUERY
                + " IS NULL THEN 1 ELSE 0 END, " + EFFECTIVE_PRICE_SUBQUERY + " ASC, p.id ASC";
            case "price-desc" -> " ORDER BY CASE WHEN " + EFFECTIVE_PRICE_SUBQUERY
                + " IS NULL THEN 1 ELSE 0 END, " + EFFECTIVE_PRICE_SUBQUERY + " DESC, p.id ASC";
            case "name", "name-asc" -> " ORDER BY LOWER(p.name) ASC, p.id ASC";
            case "rating", "rating-desc" -> """
                 ORDER BY CASE WHEN p.average_rating IS NULL THEN 1 ELSE 0 END,
                          p.average_rating DESC,
                          CASE WHEN p.created_at IS NULL THEN 1 ELSE 0 END,
                          p.created_at DESC,
                          p.id ASC
                """;
            default -> """
                 ORDER BY CASE WHEN p.created_at IS NULL THEN 1 ELSE 0 END,
                          p.created_at DESC,
                          p.id ASC
                """;
        };
    }

    private static void appendIn(
            StringBuilder sql,
            String column,
            String parameterPrefix,
            Collection<Long> values,
            Map<String, Object> parameters) {
        sql.append(column).append(" IN (");
        int index = 0;
        for (Long value : values) {
            if (index > 0) sql.append(',');
            String parameter = parameterPrefix + index++;
            sql.append(':').append(parameter);
            parameters.put(parameter, value);
        }
        sql.append(')');
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private static void bind(Query query, Map<String, Object> parameters) {
        parameters.forEach(query::setParameter);
    }

    private record SqlParts(String fromAndWhere, Map<String, Object> parameters) {
    }
}
