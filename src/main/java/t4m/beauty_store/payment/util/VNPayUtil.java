package t4m.beauty_store.payment.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

/**
 * VNPay Utility class
 * Các hàm tiện ích để xử lý checksum, hash, query string cho VNPay
 */
public class VNPayUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Generate HMACSHA512 hash
     */
    public static String hmacSHA512(String key, String data) {
        try {
            if (key == null || key.isBlank() || data == null) {
                throw new IllegalArgumentException("HMAC key and data are required");
            }
            Mac hmac512 = Mac.getInstance("HmacSHA512");
            byte[] hmacKeyBytes = key.getBytes(StandardCharsets.UTF_8);
            SecretKeySpec secretKey = new SecretKeySpec(hmacKeyBytes, "HmacSHA512");
            hmac512.init(secretKey);
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            byte[] result = hmac512.doFinal(dataBytes);
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not calculate VNPay checksum", ex);
        }
    }

    /**
     * Generate SHA256 hash
     */
    public static String sha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Không thể tạo chữ ký thanh toán", e);
        }
    }

    /**
     * Build hash data from sorted params (dùng cho tính checksum)
     * Theo tài liệu VNPay: hashData phải encode bằng US_ASCII
     */
    public static String hashAllFields(Map<String, String> fields) {
        return encodedEntries(fields, false);
    }

    /**
     * Build query string from params (dùng cho URL)
     * Theo tài liệu VNPay: query cũng phải encode bằng US_ASCII
     */
    public static String buildQuery(Map<String, String> params) {
        return encodedEntries(params, true);
    }

    /**
     * Get IP Address from request
     */
    public static String getIpAddress(jakarta.servlet.http.HttpServletRequest request) {
        if (request == null || request.getRemoteAddr() == null || request.getRemoteAddr().isBlank()) {
            return "127.0.0.1";
        }
        // Forwarded headers must only be resolved by a trusted reverse-proxy configuration.
        return request.getRemoteAddr();
    }

    /**
     * Generate random number
     */
    public static String getRandomNumber(int len) {
        if (len <= 0 || len > 64) {
            throw new IllegalArgumentException("Length must be between 1 and 64");
        }
        String chars = "0123456789";
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(chars.charAt(SECURE_RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static String encodedEntries(Map<String, String> values, boolean encodeKey) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null && !entry.getValue().isEmpty())
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    String key = encodeKey
                            ? URLEncoder.encode(entry.getKey(), StandardCharsets.US_ASCII)
                            : entry.getKey();
                    return key + '=' + URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII);
                })
                .collect(Collectors.joining("&"));
    }
}
