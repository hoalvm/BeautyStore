package t4m.beauty_store.payment.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import t4m.beauty_store.config.VNPayConfig;
import t4m.beauty_store.config.StoreTime;
import t4m.beauty_store.payment.util.VNPayUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * VNPay Payment Service
 * Xử lý tạo URL thanh toán VNPay
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VNPayService {

    private final VNPayConfig vnPayConfig;
    private static final DateTimeFormatter VNPAY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Pattern ORDER_NUMBER_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,100}");
    private static final Pattern SHA512_HEX_PATTERN = Pattern.compile("[A-Fa-f0-9]{128}");

    /**
     * Tạo URL thanh toán VNPay
     * 
     * @param orderNumber Mã đơn hàng
     * @param totalAmount Số tiền thanh toán
     * @param orderInfo Thông tin đơn hàng
     * @param request HttpServletRequest để lấy IP
     * @return URL thanh toán VNPay
     */
    public String createPaymentUrl(String orderNumber, BigDecimal totalAmount, String orderInfo, HttpServletRequest request) {
        return createPaymentUrl(orderNumber, totalAmount, orderInfo, request,
            StoreTime.now().plusMinutes(15));
    }

    public String createPaymentUrl(String orderNumber, BigDecimal totalAmount, String orderInfo,
            HttpServletRequest request, LocalDateTime reservationExpiresAt) {
        try {
            vnPayConfig.requireConfigured();
            validateOrderNumber(orderNumber);

            // VND does not use fractional units; VNPay represents the value multiplied by 100.
            long amount = toVnPayAmount(totalAmount);
            
            // Lấy IP Address
            String vnp_IpAddr = VNPayUtil.getIpAddress(request);
            
            // Tạo các tham số
            Map<String, String> vnp_Params = new HashMap<>();
            vnp_Params.put("vnp_Version", VNPayConfig.VERSION);
            vnp_Params.put("vnp_Command", VNPayConfig.COMMAND);
            vnp_Params.put("vnp_TmnCode", vnPayConfig.getTmnCode());
            vnp_Params.put("vnp_Amount", String.valueOf(amount));
            vnp_Params.put("vnp_CurrCode", VNPayConfig.CURRENCY_CODE);
            
            // Mã ngân hàng/ví điện tử - để trống để khách chọn
            // Hoặc có thể set: VNPAYQR, VNBANK, INTCARD
            // vnp_Params.put("vnp_BankCode", "");
            
            vnp_Params.put("vnp_TxnRef", orderNumber);
            vnp_Params.put("vnp_OrderInfo", sanitizeOrderInfo(orderInfo, orderNumber));
            vnp_Params.put("vnp_OrderType", VNPayConfig.ORDER_TYPE);
            vnp_Params.put("vnp_Locale", VNPayConfig.LOCALE);
            vnp_Params.put("vnp_ReturnUrl", vnPayConfig.getReturnUrl());
            vnp_Params.put("vnp_IpAddr", vnp_IpAddr);
            
            // Tạo thời gian
            LocalDateTime createdAt = StoreTime.now();
            if (reservationExpiresAt == null || !reservationExpiresAt.isAfter(createdAt)) {
                throw new IllegalArgumentException("Payment reservation has expired");
            }
            String vnp_CreateDate = createdAt.format(VNPAY_DATE_FORMAT);
            vnp_Params.put("vnp_CreateDate", vnp_CreateDate);
            
            // The gateway URL must never outlive the exact stock reservation.
            LocalDateTime maximumExpiry = createdAt.plusMinutes(15);
            LocalDateTime effectiveExpiry = reservationExpiresAt.isBefore(maximumExpiry)
                ? reservationExpiresAt : maximumExpiry;
            String vnp_ExpireDate = effectiveExpiry.format(VNPAY_DATE_FORMAT);
            vnp_Params.put("vnp_ExpireDate", vnp_ExpireDate);
            
            // Build hash data và query string
            String hashData = VNPayUtil.hashAllFields(vnp_Params);
            String vnpSecureHash = VNPayUtil.hmacSHA512(vnPayConfig.getHashSecret(), hashData);
            
            // Build query URL
            String queryUrl = VNPayUtil.buildQuery(vnp_Params);
            queryUrl += "&vnp_SecureHash=" + vnpSecureHash;
            
            String paymentUrl = vnPayConfig.getPayUrl() + "?" + queryUrl;
            
            log.info("Created a VNPay payment URL");
            return paymentUrl;
            
        } catch (Exception e) {
            log.warn("Could not create a VNPay payment URL: {}", e.getClass().getSimpleName());
            throw new IllegalStateException("Không thể tạo liên kết thanh toán VNPay", e);
        }
    }

    /**
     * Verify checksum từ VNPay response
     */
    public boolean verifyPaymentResponse(Map<String, String> params) {
        if (params == null || !vnPayConfig.isConfigured()) {
            return false;
        }

        String vnpSecureHash = params.get("vnp_SecureHash");
        if (vnpSecureHash == null || !SHA512_HEX_PATTERN.matcher(vnpSecureHash).matches()) {
            return false;
        }

        Map<String, String> fields = new HashMap<>();
        params.forEach((key, value) -> {
            if (key != null && key.startsWith("vnp_")
                    && !"vnp_SecureHash".equals(key)
                    && !"vnp_SecureHashType".equals(key)) {
                fields.put(key, value);
            }
        });
        String hashData = VNPayUtil.hashAllFields(fields);
        String calculatedHash = VNPayUtil.hmacSHA512(vnPayConfig.getHashSecret(), hashData);

        return MessageDigest.isEqual(
                calculatedHash.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII),
                vnpSecureHash.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }

    private long toVnPayAmount(BigDecimal totalAmount) {
        if (totalAmount == null || totalAmount.signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        BigDecimal roundedVnd = totalAmount.setScale(0, RoundingMode.UNNECESSARY);
        return roundedVnd.multiply(BigDecimal.valueOf(100)).longValueExact();
    }

    private void validateOrderNumber(String orderNumber) {
        if (orderNumber == null || !ORDER_NUMBER_PATTERN.matcher(orderNumber).matches()) {
            throw new IllegalArgumentException("Invalid order number");
        }
    }

    private String sanitizeOrderInfo(String orderInfo, String orderNumber) {
        String value = orderInfo == null || orderInfo.isBlank()
                ? "Thanh toan don hang " + orderNumber
                : orderInfo;
        value = value.replaceAll("[\\p{Cntrl}]", " ").strip();
        return value.length() <= 255 ? value : value.substring(0, 255);
    }
}
