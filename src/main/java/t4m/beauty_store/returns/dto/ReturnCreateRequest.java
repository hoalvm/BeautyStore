package t4m.beauty_store.returns.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import t4m.beauty_store.returns.entity.ReturnReason;

import java.util.ArrayList;
import java.util.List;

@Data
public class ReturnCreateRequest {
    @NotBlank
    private String orderNumber;

    @NotEmpty
    private List<@Valid Item> items = new ArrayList<>();

    @Data
    public static class Item {
        @NotNull
        private Long orderItemId;

        @NotNull
        @Min(1)
        private Integer quantity;

        @NotNull
        private ReturnReason reason;

        @NotNull
        private Boolean unopenedAndSealed;

        @Size(max = 2000)
        private String details;

        @Size(max = 5)
        private List<@Size(max = 1000) String> imageUrls = new ArrayList<>();
    }
}
