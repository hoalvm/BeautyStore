package t4m.beauty_store.review.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ReviewRequest {
    @NotNull
    private Long orderItemId;

    @NotNull
    @Min(1)
    @Max(5)
    private Integer stars;

    @Size(max = 120)
    private String title;

    @Size(max = 3000)
    private String content;

    @Size(max = 50)
    private String skinType;

    @Size(max = 5, message = "Mỗi đánh giá có tối đa 5 ảnh")
    private List<@Size(max = 1000) String> imageUrls = new ArrayList<>();
}
