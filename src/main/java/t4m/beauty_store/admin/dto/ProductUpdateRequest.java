package t4m.beauty_store.admin.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.validation.constraints.Min;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductUpdateRequest {
    private String name;
    private String slug;
    private Long brandId;
    @JsonIgnore @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    private boolean brandIdSpecified;
    private String benefits;
    @JsonIgnore @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    private boolean benefitsSpecified;
    private String inci;
    @JsonIgnore @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    private boolean inciSpecified;
    private String directions;
    @JsonIgnore @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    private boolean directionsSpecified;
    private String warnings;
    @JsonIgnore @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    private boolean warningsSpecified;
    private String spf;
    @JsonIgnore @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    private boolean spfSpecified;

    @Min(value = 0, message = "PAO months must be at least 0")
    private Integer paoMonths;
    @JsonIgnore @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    private boolean paoMonthsSpecified;

    @Min(value = 0, message = "Shelf life months must be at least 0")
    private Integer shelfLifeMonths;
    @JsonIgnore @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    private boolean shelfLifeMonthsSpecified;

    private Set<Long> facetIds;
    @JsonIgnore @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    private boolean facetIdsSpecified;

    private String material;
    private String origin;
    @Min(value = 0, message = "Warranty months must be at least 0")
    private Integer warrantyMonths;

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean warrantyMonthsSpecified;

    private String specifications;
    private String description;
    @JsonIgnore @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    private boolean descriptionSpecified;

    private Long categoryId;

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean categoryIdSpecified;

    private Boolean featured;
    private Boolean active;

    public void setBrandId(Long brandId) {
        this.brandId = brandId;
        this.brandIdSpecified = true;
    }

    @JsonIgnore
    public boolean isBrandIdSpecified() { return brandIdSpecified; }

    public void setBenefits(String value) { benefits = value; benefitsSpecified = true; }
    @JsonIgnore public boolean isBenefitsSpecified() { return benefitsSpecified; }

    public void setInci(String value) { inci = value; inciSpecified = true; }
    @JsonIgnore public boolean isInciSpecified() { return inciSpecified; }

    public void setDirections(String value) { directions = value; directionsSpecified = true; }
    @JsonIgnore public boolean isDirectionsSpecified() { return directionsSpecified; }

    public void setWarnings(String value) { warnings = value; warningsSpecified = true; }
    @JsonIgnore public boolean isWarningsSpecified() { return warningsSpecified; }

    public void setSpf(String value) { spf = value; spfSpecified = true; }
    @JsonIgnore public boolean isSpfSpecified() { return spfSpecified; }

    public void setPaoMonths(Integer value) { paoMonths = value; paoMonthsSpecified = true; }
    @JsonIgnore public boolean isPaoMonthsSpecified() { return paoMonthsSpecified; }

    public void setShelfLifeMonths(Integer value) { shelfLifeMonths = value; shelfLifeMonthsSpecified = true; }
    @JsonIgnore public boolean isShelfLifeMonthsSpecified() { return shelfLifeMonthsSpecified; }

    public void setFacetIds(Set<Long> value) { facetIds = value; facetIdsSpecified = true; }
    @JsonIgnore public boolean isFacetIdsSpecified() { return facetIdsSpecified; }

    public void setDescription(String value) { description = value; descriptionSpecified = true; }
    @JsonIgnore public boolean isDescriptionSpecified() { return descriptionSpecified; }

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean specificationsSpecified;

    public void setSpecifications(String specifications) {
        this.specifications = specifications;
        this.specificationsSpecified = true;
    }

    @JsonIgnore
    public boolean isSpecificationsSpecified() {
        return specificationsSpecified;
    }

    public void setWarrantyMonths(Integer warrantyMonths) {
        this.warrantyMonths = warrantyMonths;
        this.warrantyMonthsSpecified = true;
    }

    @JsonIgnore
    public boolean isWarrantyMonthsSpecified() {
        return warrantyMonthsSpecified;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
        this.categoryIdSpecified = true;
    }

    @JsonIgnore
    public boolean isCategoryIdSpecified() {
        return categoryIdSpecified;
    }

    @JsonSetter(value = "material", nulls = Nulls.AS_EMPTY)
    public void setMaterial(String material) {
        this.material = material;
    }

    @JsonSetter(value = "origin", nulls = Nulls.AS_EMPTY)
    public void setOrigin(String origin) {
        this.origin = origin;
    }

}
