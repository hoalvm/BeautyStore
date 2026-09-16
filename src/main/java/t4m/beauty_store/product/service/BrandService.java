package t4m.beauty_store.product.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import t4m.beauty_store.admin.dto.BrandRequest;
import t4m.beauty_store.product.entity.Brand;
import t4m.beauty_store.product.repository.BrandRepository;
import t4m.beauty_store.product.util.Slugifier;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BrandService {
    private final BrandRepository brandRepository;

    public List<Brand> getBrands(boolean includeInactive) {
        return includeInactive ? brandRepository.findAll() : brandRepository.findByActiveTrueOrderByNameAsc();
    }

    public Brand getBrand(Long id) {
        return brandRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Brand not found"));
    }

    @Transactional
    public Brand create(BrandRequest request) {
        String name = required(request.getName(), "Brand name is required");
        if (brandRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("Brand name already exists");
        }
        String slug = uniqueSlug(request.getSlug(), name, null);
        return brandRepository.save(Brand.builder()
            .name(name)
            .slug(slug)
            .description(clean(request.getDescription()))
            .logoUrl(clean(request.getLogoUrl()))
            .country(clean(request.getCountry()))
            .active(request.getActive() == null || request.getActive())
            .build());
    }

    @Transactional
    public Brand update(Long id, BrandRequest request) {
        Brand brand = getBrand(id);
        String name = required(request.getName(), "Brand name is required");
        if (brandRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new IllegalArgumentException("Brand name already exists");
        }
        brand.setName(name);
        brand.setSlug(uniqueSlug(request.getSlug(), name, id));
        brand.setDescription(clean(request.getDescription()));
        brand.setLogoUrl(clean(request.getLogoUrl()));
        brand.setCountry(clean(request.getCountry()));
        if (request.getActive() != null) brand.setActive(request.getActive());
        return brandRepository.save(brand);
    }

    @Transactional
    public Brand setActive(Long id, boolean active) {
        Brand brand = getBrand(id);
        brand.setActive(active);
        return brandRepository.save(brand);
    }

    private String uniqueSlug(String requested, String name, Long currentId) {
        String base = Slugifier.slugify(clean(requested) == null ? name : requested);
        String candidate = base;
        int suffix = 2;
        while (currentId == null
            ? brandRepository.existsBySlugIgnoreCase(candidate)
            : brandRepository.existsBySlugIgnoreCaseAndIdNot(candidate, currentId)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    static String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    static String required(String value, String message) {
        String cleaned = clean(value);
        if (cleaned == null) throw new IllegalArgumentException(message);
        return cleaned;
    }
}
