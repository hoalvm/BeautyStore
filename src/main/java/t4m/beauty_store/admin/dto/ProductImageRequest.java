package t4m.beauty_store.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductImageRequest {
    @NotBlank(message = "Image URL is required")
    @Size(max = 1000, message = "Image URL must not exceed 1000 characters")
    private String url;
    private String cloudinaryPublicId;

    @NotBlank(message = "Image alt text is required")
    @Size(max = 300, message = "Image alt text must not exceed 300 characters")
    private String altText;

    @Min(value = 0, message = "Image order must be at least 0")
    private Integer sortOrder;
    private Boolean primary;
    private Long variantId;
}
