package t4m.beauty_store.order.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GuestOrderAccessRequest {
    @NotBlank
    @Size(max = 80)
    private String orderNumber;

    @NotBlank
    @Email
    @Size(max = 254)
    private String email;
}
