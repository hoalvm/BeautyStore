package t4m.beauty_store.product.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
    name = "product_facets",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_product_facets_type_code",
        columnNames = {"facet_type", "code"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductFacet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "facet_type", nullable = false, length = 40)
    private ProductFacetType type;

    @Column(nullable = false, length = 100)
    private String code;

    @Column(nullable = false, length = 160)
    private String label;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @Builder.Default
    @ManyToMany(mappedBy = "facets")
    @JsonIgnore
    private Set<Product> products = new HashSet<>();

    @PrePersist
    @PreUpdate
    void applyDefaults() {
        if (active == null) {
            active = true;
        }
        if (code != null) {
            code = code.trim().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
