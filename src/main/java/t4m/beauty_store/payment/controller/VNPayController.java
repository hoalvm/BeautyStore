package t4m.beauty_store.payment.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import t4m.beauty_store.order.service.OrderService;
import t4m.beauty_store.payment.dto.VNPayPaymentResult;
import t4m.beauty_store.payment.service.VNPayService;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** VNPay browser return and server-to-server IPN endpoints. */
@Controller
@RequestMapping("/api/payment/vnpay")
@RequiredArgsConstructor
@Slf4j
public class VNPayController {

    private static final Pattern ORDER_NUMBER_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,100}");
    private static final int MAX_VNPAY_PARAMETERS = 40;
    private static final int MAX_PARAMETER_LENGTH = 1_024;

    private final VNPayService vnPayService;
    private final OrderService orderService;

    /**
     * Browser redirects are not authoritative. This endpoint validates what it
     * displays, but only the signed IPN endpoint below mutates an order.
     */
    @GetMapping("/return")
    public String paymentReturn(HttpServletRequest request, Model model) {
        try {
            Map<String, String> params = extractVnPayParameters(request);
            if (!vnPayService.verifyPaymentResponse(params)) {
                return invalidReturn(model, "Chữ ký thanh toán không hợp lệ");
            }

            String orderNumber = required(params, "vnp_TxnRef");
            validateOrderNumber(orderNumber);
            long amount = parseVndAmount(required(params, "vnp_Amount"));
            if (!orderService.orderExists(orderNumber)
                    || !orderService.verifyOrderAmount(orderNumber, amount)) {
                return invalidReturn(model, "Thông tin đơn hàng không khớp");
            }

            String responseCode = required(params, "vnp_ResponseCode");
            String transactionStatus = params.get("vnp_TransactionStatus");
            boolean gatewayReportsSuccess = "00".equals(responseCode)
                    && "00".equals(transactionStatus);

            if (gatewayReportsSuccess) {
                // IPN can arrive shortly after the browser redirect.
                return orderService.isPaymentConfirmed(orderNumber)
                        ? "redirect:/order-confirmation/" + orderNumber
                        : "redirect:/payment-pending/" + orderNumber;
            }

            model.addAttribute("success", false);
            model.addAttribute("orderNumber", orderNumber);
            model.addAttribute("responseCode", responseCode);
            model.addAttribute("message", getResponseMessage(responseCode));
            return "payment-result";
        } catch (IllegalArgumentException exception) {
            log.warn("Rejected an invalid VNPay return payload");
            return invalidReturn(model, "Dữ liệu thanh toán không hợp lệ");
        } catch (Exception exception) {
            log.error("Could not process VNPay browser return: {}",
                    exception.getClass().getSimpleName());
            return invalidReturn(model, "Không thể kiểm tra kết quả thanh toán");
        }
    }

    /** IPN is the only callback allowed to commit a VNPay payment result. */
    @GetMapping("/ipn")
    @ResponseBody
    public ResponseEntity<Map<String, String>> paymentIpn(HttpServletRequest request) {
        try {
            Map<String, String> params = extractVnPayParameters(request);
            if (!vnPayService.verifyPaymentResponse(params)) {
                return ipnResponse("97", "Invalid Checksum");
            }

            String orderNumber = required(params, "vnp_TxnRef");
            validateOrderNumber(orderNumber);
            long amount = parseVndAmount(required(params, "vnp_Amount"));
            String responseCode = required(params, "vnp_ResponseCode");
            String transactionStatus = required(params, "vnp_TransactionStatus");
            String transactionNo = required(params, "vnp_TransactionNo");
            boolean success = "00".equals(responseCode) && "00".equals(transactionStatus);

            VNPayPaymentResult result = orderService.processVNPayIpn(
                    orderNumber,
                    amount,
                    success,
                    transactionNo,
                    params.get("vnp_BankCode"),
                    responseCode);

            return ipnResponse(result);
        } catch (IllegalArgumentException exception) {
            log.warn("Rejected an invalid VNPay IPN payload");
            return ipnResponse("99", "Invalid Request");
        } catch (Exception exception) {
            log.error("Could not process VNPay IPN: {}", exception.getClass().getSimpleName());
            return ipnResponse("99", "Unknown error");
        }
    }

    /**
     * A VNPay TxnRef is single-use. Reissuing a link for the same order allows
     * a delayed IPN from the older browser session to race the newer payment.
     * V1 therefore requires a fresh checkout after a failed/abandoned attempt.
     */
    @PostMapping("/create-url/{orderNumber}")
    @ResponseBody
    public ResponseEntity<?> createPaymentUrlForOrder(@PathVariable String orderNumber,
                                                       Authentication authentication,
                                                       @org.springframework.web.bind.annotation.RequestHeader(
                                                           value = "X-Order-Token", required = false)
                                                       String guestOrderToken,
                                                       HttpServletRequest httpRequest) {
        return createServerPricedPaymentUrl(
            orderNumber, authentication, guestOrderToken, httpRequest);
    }

    /**
     * Backward-compatible endpoint. Client-supplied amount/orderInfo are ignored;
     * the server loads both the order status and total before creating the URL.
     */
    @PostMapping("/create-payment-link")
    @ResponseBody
    public ResponseEntity<?> createPaymentLink(@RequestBody Map<String, Object> requestData,
                                                Authentication authentication,
                                                @org.springframework.web.bind.annotation.RequestHeader(
                                                    value = "X-Order-Token", required = false)
                                                String guestOrderToken,
                                                HttpServletRequest httpRequest) {
        Object value = requestData.get("orderNumber");
        if (!(value instanceof String orderNumber)) {
            throw new IllegalArgumentException("Thiếu mã đơn hàng");
        }
        return createServerPricedPaymentUrl(
            orderNumber, authentication, guestOrderToken, httpRequest);
    }

    private ResponseEntity<?> createServerPricedPaymentUrl(String orderNumber,
                                                             Authentication authentication,
                                                             String guestOrderToken,
                                                             HttpServletRequest request) {
        validateOrderNumber(orderNumber);
        throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Liên kết VNPay không được cấp lại; vui lòng tạo checkout mới để bảo đảm an toàn thanh toán");
    }

    private Map<String, String> extractVnPayParameters(HttpServletRequest request) {
        Map<String, String> result = new HashMap<>();
        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            if (result.size() >= MAX_VNPAY_PARAMETERS) {
                throw new IllegalArgumentException("Too many VNPay parameters");
            }
            String name = names.nextElement();
            if (name == null || !name.startsWith("vnp_")) {
                continue;
            }
            String[] values = request.getParameterValues(name);
            if (values == null || values.length != 1 || values[0] == null
                    || values[0].length() > MAX_PARAMETER_LENGTH) {
                throw new IllegalArgumentException("Invalid VNPay parameter");
            }
            result.put(name, values[0]);
        }
        return result;
    }

    private String required(Map<String, String> params, String name) {
        String value = params.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing VNPay parameter");
        }
        return value;
    }

    private long parseVndAmount(String rawAmount) {
        try {
            long amountInHundredths = Long.parseLong(rawAmount);
            if (amountInHundredths <= 0 || amountInHundredths % 100 != 0) {
                throw new IllegalArgumentException("Invalid VNPay amount");
            }
            return amountInHundredths / 100;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid VNPay amount", exception);
        }
    }

    private void validateOrderNumber(String orderNumber) {
        if (orderNumber == null || !ORDER_NUMBER_PATTERN.matcher(orderNumber).matches()) {
            throw new IllegalArgumentException("Invalid order number");
        }
    }

    private String invalidReturn(Model model, String message) {
        model.addAttribute("success", false);
        model.addAttribute("message", message);
        return "payment-result";
    }

    private ResponseEntity<Map<String, String>> ipnResponse(String code, String message) {
        return ResponseEntity.ok(Map.of("RspCode", code, "Message", message));
    }

    private ResponseEntity<Map<String, String>> ipnResponse(VNPayPaymentResult result) {
        if (result == null || result.outcome() == null) {
            return ipnResponse("99", "Unknown error");
        }
        return switch (result.outcome()) {
            case ORDER_NOT_FOUND -> ipnResponse("01", "Order not Found");
            case INVALID_AMOUNT -> ipnResponse("04", "Invalid Amount");
            case ALREADY_CONFIRMED -> ipnResponse("02", "Order already confirmed");
            case ALREADY_FINAL -> ipnResponse("02", "Order already processed");
            case RECONCILIATION_REQUIRED ->
                ipnResponse("99", "Payment requires reconciliation");
            case FAILURE_RECORDED -> {
                log.info("Recorded a valid VNPay failure result");
                yield ipnResponse("00", "Confirm Success");
            }
            case CONFIRMED -> {
                if (!result.paymentCommitted()) {
                    log.error("Refused to acknowledge an inconsistent VNPay confirmation outcome");
                    yield ipnResponse("99", "Payment was not committed");
                }
                log.info("Committed a valid VNPay payment result");
                yield ipnResponse("00", "Confirm Success");
            }
        };
    }

    private String getResponseMessage(String responseCode) {
        if (responseCode == null) {
            return "Giao dịch thất bại. Vui lòng liên hệ hỗ trợ.";
        }
        return switch (responseCode) {
            case "00" -> "Giao dịch thành công";
            case "07" -> "Giao dịch thành công nhưng cần được kiểm tra thêm.";
            case "09" -> "Tài khoản chưa đăng ký dịch vụ Internet Banking.";
            case "10" -> "Thông tin xác thực thẻ hoặc tài khoản không chính xác.";
            case "11" -> "Đã hết thời gian chờ thanh toán. Vui lòng thực hiện lại.";
            case "12" -> "Thẻ hoặc tài khoản đang bị khóa.";
            case "13" -> "Mã xác thực giao dịch không chính xác.";
            case "24" -> "Bạn đã hủy giao dịch.";
            case "51" -> "Tài khoản không đủ số dư.";
            case "65" -> "Tài khoản đã vượt hạn mức giao dịch trong ngày.";
            case "75" -> "Ngân hàng thanh toán đang bảo trì.";
            case "79" -> "Bạn đã nhập sai mật khẩu thanh toán quá số lần cho phép.";
            default -> "Giao dịch thất bại. Vui lòng liên hệ hỗ trợ.";
        };
    }
}
