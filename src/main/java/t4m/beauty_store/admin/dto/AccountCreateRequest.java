package t4m.beauty_store.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import t4m.beauty_store.auth.validation.StrongPassword;

@Data
public class AccountCreateRequest {
    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    @StrongPassword
    private String password;

    @NotBlank(message = "Xác nhận mật khẩu không được để trống")
    private String confirmPassword;

    @NotBlank(message = "Họ tên không được để trống")
    private String name;

    private String phone;

    @NotBlank(message = "Vai trò không được để trống")
    private String role; // "USER" or "SHIPPER"
}
