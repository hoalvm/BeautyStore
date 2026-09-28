package t4m.beauty_store.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import t4m.beauty_store.auth.validation.StrongPassword;

@Data
public class ChangePasswordRequest {
    @NotBlank(message = "Mật khẩu hiện tại là bắt buộc")
    private String currentPassword;

    @NotBlank(message = "Mật khẩu mới là bắt buộc")
    @StrongPassword
    private String newPassword;
}
