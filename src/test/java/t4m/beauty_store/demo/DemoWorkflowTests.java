package t4m.beauty_store.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:demo-workflow;MODE=MySQL;NON_KEYWORDS=USER;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop", "server.address=127.0.0.1",
        "spring.flyway.enabled=false", "orders.reservation-cleanup-ms=86400000"
})
@ActiveProfiles("demo")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DemoWorkflowTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DemoMailbox mailbox;
    @Autowired t4m.beauty_store.auth.service.EmailService emailService;

    @Test
    void backgroundEmailTemplatesRenderWorkingLinksWithoutWebContext() {
        String email = "email-rendering@example.test";
        emailService.sendWelcomeEmail(email, "<Demo user>", "http://localhost:8080/products");
        emailService.sendResetPasswordEmail(email, "http://localhost:8080/reset-password");
        emailService.sendThankYouEmail(email, "http://localhost:8080/products");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var mails = mailbox.list().stream().filter(mail -> mail.recipient().contains(email)).toList();
            assertThat(mails).hasSize(3);
            for (var mail : mails) {
                assertThat(mailbox.read(mail.id()))
                        .contains("href=\"http://localhost:8080/")
                        .doesNotContain("[[${", "@storeProperties", "th:href");
            }
        });
    }

    @Test
    void registerActivateAndResetPasswordUsingRenderedLocalMail() throws Exception {
        String email = "fresh-clone@example.test";
        String originalPassword = "FreshClone123!";
        postJson("/api/auth/register", Map.of("email", email, "password", originalPassword));
        String activation = otp(email, "đăng ký tài khoản");
        postJson("/api/auth/verify-account", Map.of("email", email, "otp", activation));
        String login = postJson("/api/auth/login", Map.of("email", email, "password", originalPassword));
        assertThat(json.readTree(login).path("token").asText()).isNotBlank();
        postJson("/api/auth/forgot-password", Map.of("email", email));
        String reset = otp(email, "đặt lại mật khẩu");
        postJson("/api/auth/reset-password", Map.of("email", email, "otp", reset, "newPassword", "ChangedDemo123!"));
        postJson("/api/auth/login", Map.of("email", email, "password", "ChangedDemo123!"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("email", email, "password", originalPassword))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void demoPagesAssetsAndAccountsWorkWithoutExternalServices() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/demo/inbox")));
        mvc.perform(get("/demo/inbox")).andExpect(status().isOk());
        mvc.perform(get("/vendor/bootstrap-5.3.0/bootstrap.min.css")).andExpect(status().isOk());
        mvc.perform(get("/api/products")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(40));
        mvc.perform(get("/checkout")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("value=\"VNPAY\""))));
        for (var credentials : Map.of(
                "admin@beautystore.vn", "BeautyAdmin123!",
                "shipper@beautystore.vn", "BeautyShipper123!",
                "user@beautystore.vn", "BeautyUser123!").entrySet()) {
            postJson("/api/auth/login", Map.of("email", credentials.getKey(), "password", credentials.getValue()));
        }
    }

    @Test
    void localMailboxRejectsRemoteClientsAndReboundHosts() throws Exception {
        mvc.perform(get("/demo/api/mail").with(request -> {
            request.setRemoteAddr("192.0.2.20");
            return request;
        })).andExpect(status().isForbidden());
        mvc.perform(get("/demo/api/mail").with(request -> {
            request.setServerName("attacker.example");
            return request;
        })).andExpect(status().isForbidden());
        mvc.perform(get("/demo/api/mail")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    private String postJson(String path, Object value) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(value)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String otp(String email, String action) throws Exception {
        await().atMost(Duration.ofSeconds(10)).until(() -> mailbox.list().stream()
                .anyMatch(mail -> mail.recipient().contains(email)
                        && mailbox.read(mail.id()).contains(action)));
        String id = mailbox.list().stream().filter(mail -> mail.recipient().contains(email)
                && mailbox.read(mail.id()).contains(action)).findFirst().orElseThrow().id();
        String rendered = mvc.perform(get("/demo/mail/" + id)).andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("sandbox")))
                .andReturn().getResponse().getContentAsString();
        var matcher = Pattern.compile(">(\\d{6})</span>").matcher(rendered);
        assertThat(matcher.find()).as("OTP rendered by the real email template").isTrue();
        return matcher.group(1);
    }
}
