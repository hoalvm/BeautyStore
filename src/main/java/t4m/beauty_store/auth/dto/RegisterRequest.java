package t4m.beauty_store.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import t4m.beauty_store.auth.validation.StrongPassword;

@Data
public class RegisterRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @StrongPassword
    private String password;

    // Role is not required from client, will be set to ROLE_USER by default in service
    private String role;
}
