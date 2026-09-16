package t4m.beauty_store.support.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GuestSupportRequest {
    @Size(max = 120, message = "Tên không được quá 120 ký tự")
    private String name;

    @Email(message = "Email không đúng định dạng")
    @Size(max = 255, message = "Email không được quá 255 ký tự")
    private String email;
}
