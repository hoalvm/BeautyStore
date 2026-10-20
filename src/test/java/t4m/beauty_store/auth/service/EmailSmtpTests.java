package t4m.beauty_store.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import t4m.beauty_store.config.StoreProperties;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

/** Exercises actual SMTP on loopback, never Gmail or real recipients. */
class EmailSmtpTests {
    @Test
    void sendsThroughSmtpAndUsesLoginAddressWhenFromIsBlank() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            server.setSoTimeout(5000);
            Future<String> received = executor.submit(() -> receive(server));
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost("127.0.0.1");
            sender.setPort(server.getLocalPort());
            sender.setUsername("sender@example.test");
            // Avoid reverse DNS of the Windows hostname during EHLO in an offline test.
            sender.getJavaMailProperties().setProperty("mail.smtp.localhost", "localhost");
            sender.getJavaMailProperties().setProperty("mail.host", "localhost");
            sender.getJavaMailProperties().setProperty("mail.user", "tester");
            sender.getJavaMailProperties().setProperty("mail.smtp.auth", "false");
            sender.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout", "3000");
            sender.getJavaMailProperties().setProperty("mail.smtp.timeout", "3000");
            sender.getJavaMailProperties().setProperty("mail.smtp.writetimeout", "3000");
            TemplateEngine templates = new org.thymeleaf.spring6.SpringTemplateEngine();
            templates.setTemplateResolver(new StringTemplateResolver());

            new EmailService(sender, templates, new StoreProperties(), "")
                .sendOtpEmail("recipient@example.test", "123456", "test").join();

            assertThat(received.get(5, TimeUnit.SECONDS))
                .contains("From: sender@example.test")
                .contains("To: recipient@example.test")
                .contains("email/otp-email");
        }
    }

    @Test
    void missingSenderFailsBeforeAttemptingNetworkConnection() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        TemplateEngine templates = new org.thymeleaf.spring6.SpringTemplateEngine();
        templates.setTemplateResolver(new StringTemplateResolver());
        assertThatThrownBy(() -> new EmailService(sender, templates, new StoreProperties(), "")
            .sendOtpEmail("recipient@example.test", "123456", "test"))
            .isInstanceOf(IllegalStateException.class).hasMessage("Không thể gửi email");
    }

    private static String receive(ServerSocket server) throws IOException {
        try (var socket = server.accept();
             var input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             var output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
            socket.setSoTimeout(15000);
            output.print("220 localhost test SMTP\r\n");
            output.flush();
            StringBuilder message = new StringBuilder();
            boolean data = false;
            String line;
            while ((line = input.readLine()) != null) {
                if (data && !line.equals(".")) {
                    message.append(line).append('\n');
                    continue;
                }
                String reply;
                if (data) { data = false; reply = "250 accepted"; }
                else if (line.startsWith("DATA")) { data = true; reply = "354 continue"; }
                else if (line.startsWith("QUIT")) {
                    output.print("221 bye\r\n");
                    output.flush();
                    break;
                } else { reply = "250 OK"; }
                output.print(reply + "\r\n");
                output.flush();
            }
            return message.toString();
        }
    }
}
