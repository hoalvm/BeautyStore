package t4m.beauty_store.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import t4m.beauty_store.auth.validation.StrongPassword;

@Data
public class AccountUpdateRequest {
    @NotBlank(message = "Họ tên không được để trống")
    private String name;

    private String phone;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    private String email;

    private String role; // Optional: change role

    @StrongPassword
    private String password; // Optional: change password
    private String confirmPassword; // Optional: confirm new password
}
