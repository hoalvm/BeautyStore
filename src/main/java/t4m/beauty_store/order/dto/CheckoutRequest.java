package t4m.beauty_store.order.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutRequest {
    
    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 120, message = "Họ tên không được vượt quá 120 ký tự")
    private String customerName;
    
    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    @Size(max = 255, message = "Email không được vượt quá 255 ký tự")
    private String customerEmail;
    
    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "^[0-9]{10,11}$", message = "Số điện thoại không hợp lệ")
    private String customerPhone;
    
    @Size(max = 255, message = "Địa chỉ chi tiết không được vượt quá 255 ký tự")
    private String addressLine;
    @Size(max = 120, message = "Phường/xã không được vượt quá 120 ký tự")
    private String ward;
    @Size(max = 120, message = "Quận/huyện không được vượt quá 120 ký tự")
    private String district;
    @Size(max = 120, message = "Tỉnh/thành phố không được vượt quá 120 ký tự")
    private String province;
    
    @NotBlank(message = "Phương thức thanh toán không được để trống")
    private String paymentMethod;
    
    @Size(max = 1000, message = "Ghi chú không được vượt quá 1000 ký tự")
    private String notes;
    
    private String voucherCode;

    @AssertTrue(message = "Vui lòng nhập đầy đủ địa chỉ giao hàng")
    public boolean isShippingAddressValid() {
        return addressLine != null && !addressLine.isBlank()
            && ward != null && !ward.isBlank()
            && district != null && !district.isBlank()
            && province != null && !province.isBlank();
    }

    public String resolvedShippingAddress() {
        return String.join(", ", addressLine.trim(), ward.trim(), district.trim(), province.trim());
    }
}
