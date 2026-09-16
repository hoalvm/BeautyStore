package t4m.beauty_store.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategoryRequest {
    @NotBlank(message = "Category name is required")
    @Size(max = 255)
    private String name;
    @Size(max = 180)
    private String slug;
    private String description;
    private String icon;
    private Long parentId;
    @Min(value = 0, message = "Display order must be at least 0")
    private Integer displayOrder;
    private Boolean active;
}
