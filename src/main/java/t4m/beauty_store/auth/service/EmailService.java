package t4m.beauty_store.auth.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import t4m.beauty_store.config.StoreProperties;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final StoreProperties storeProperties;
    private final String fromAddress;

    public EmailService(JavaMailSender mailSender,
                        TemplateEngine templateEngine,
                        StoreProperties storeProperties,
                        @Value("${spring.mail.from:${spring.mail.username:}}") String fromAddress) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.storeProperties = storeProperties;
        this.fromAddress = fromAddress;
    }

    @Async
    public CompletableFuture<Void> sendOtpEmail(String email, String otp, String action) {
        sendTemplate(
                email,
                storeProperties.getName() + " - Mã xác thực OTP của bạn",
                "email/otp-email",
                Map.of(
                        "otpCode", otp,
                        "action", action,
                        "storeName", storeProperties.getName(),
                        "supportEmail", storeProperties.getSupportEmail()));
        return CompletableFuture.completedFuture(null);
    }

    @Async
    public void sendGuestOrderOtp(String email, String customerName, String orderNumber, String otp) {
        sendTemplate(
                email,
                storeProperties.getName() + " - Mã xác thực tra cứu đơn hàng",
                "email/otp-email",
                Map.of(
                        "otpCode", otp,
                        "action", "tra cứu đơn hàng " + safeText(orderNumber, 100),
                        "userName", safeText(customerName, 100),
                        "orderNumber", safeText(orderNumber, 100),
                        "storeName", storeProperties.getName(),
                        "supportEmail", storeProperties.getSupportEmail()));
    }

    @Async
    public void sendWelcomeEmail(String email, String userName, String ctaLink) {
        String safeName = safeText(userName, 100);
        sendTemplate(
                email,
                "Chào mừng " + safeName + " đến với " + storeProperties.getName() + "!",
                "email/welcome-email",
                Map.of(
                        "userName", safeName,
                        "ctaLink", ctaLink,
                        "storeName", storeProperties.getName(),
                        "supportEmail", storeProperties.getSupportEmail()));
    }

    @Async
    public void sendResetPasswordEmail(String email, String resetLink) {
        sendTemplate(
                email,
                storeProperties.getName() + " - Yêu cầu đặt lại mật khẩu",
                "email/reset-password-email",
                Map.of(
                        "resetLink", resetLink,
                        "storeName", storeProperties.getName(),
                        "supportEmail", storeProperties.getSupportEmail()));
    }

    @Async
    public void sendThankYouEmail(String email, String ctaLink) {
        sendTemplate(
                email,
                storeProperties.getName() + " - Cảm ơn bạn đã quan tâm",
                "email/thank-you-email",
                Map.of(
                        "ctaLink", ctaLink,
                        "storeName", storeProperties.getName(),
                        "supportEmail", storeProperties.getSupportEmail()));
    }

    private void sendTemplate(String recipient, String subject, String template, Map<String, Object> variables) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, true, StandardCharsets.UTF_8.name());
            if (fromAddress != null && !fromAddress.isBlank()) {
                helper.setFrom(fromAddress.strip());
            }
            helper.setTo(recipient);
            helper.setSubject(safeHeader(subject));

            Context context = new Context();
            context.setVariable("store", storeProperties);
            variables.forEach(context::setVariable);
            String htmlContent = templateEngine.process(template, context);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            logger.info("Sent a BeautyStore transactional email using template {}", template);
        } catch (MessagingException | RuntimeException exception) {
            logger.error("Could not send BeautyStore transactional email using template {}: {}",
                    template, exception.getClass().getSimpleName());
            throw new IllegalStateException("Không thể gửi email", exception);
        }
    }

    private String safeHeader(String value) {
        return safeText(value, 180);
    }

    private String safeText(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replaceAll("[\\r\\n]", " ").strip();
        return sanitized.length() <= maxLength ? sanitized : sanitized.substring(0, maxLength);
    }
}
