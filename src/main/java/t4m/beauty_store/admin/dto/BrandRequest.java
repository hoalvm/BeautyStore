package t4m.beauty_store.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BrandRequest {
    @NotBlank(message = "Brand name is required")
    @Size(max = 160)
    private String name;
    @Size(max = 180)
    private String slug;
    private String description;
    @Size(max = 1000)
    private String logoUrl;
    @Size(max = 120)
    private String country;
    private Boolean active;
}
