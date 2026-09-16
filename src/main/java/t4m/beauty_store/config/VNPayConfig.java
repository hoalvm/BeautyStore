package t4m.beauty_store.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import lombok.Getter;

/**
 * VNPay configuration
 * Lưu các thông số cấu hình để kết nối VNPay Payment Gateway
 */
@Configuration
@Getter
public class VNPayConfig {
    
    // Mã website của merchant trên hệ thống của VNPAY
    @Value("${vnpay.tmn-code:}")
    private String tmnCode;
    
    // Chuỗi bí mật sử dụng để kiểm tra toàn vẹn dữ liệu
    @Value("${vnpay.hash-secret:}")
    private String hashSecret;
    
    // URL thanh toán VNPay (Sandbox)
    @Value("${vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String payUrl;
    
    // URL Return sau khi thanh toán
    @Value("${vnpay.return-url:http://localhost:8080/api/payment/vnpay/return}")
    private String returnUrl;
    
    // IPN URL - URL để VNPay gọi về cập nhật kết quả thanh toán
    @Value("${vnpay.ipn-url:http://localhost:8080/api/payment/vnpay/ipn}")
    private String ipnUrl;
    
    // Phiên bản API
    public static final String VERSION = "2.1.0";
    
    // Command thanh toán
    public static final String COMMAND = "pay";
    
    // Đơn vị tiền tệ
    public static final String CURRENCY_CODE = "VND";
    
    // Loại hàng hóa (other - hàng hóa khác)
    public static final String ORDER_TYPE = "other";
    
    // Ngôn ngữ
    public static final String LOCALE = "vn";

    public boolean isConfigured() {
        return hasText(tmnCode) && hasText(hashSecret)
                && !"DEMOV210".equalsIgnoreCase(tmnCode.strip())
                && !"DEMOSECRETKEY".equalsIgnoreCase(hashSecret.strip());
    }

    public void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("VNPay credentials are not configured");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
