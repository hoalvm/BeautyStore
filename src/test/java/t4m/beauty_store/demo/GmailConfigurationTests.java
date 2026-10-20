package t4m.beauty_store.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class GmailConfigurationTests {
    @Test
    void freshCloneTemplatePreservesLocalDefaultsWithoutPrivateCredentials() throws Exception {
        Properties settings = new Properties();
        for (String name : new String[]{"src/main/resources/application.properties",
                "src/main/resources/application-demo.properties", ".env.example"}) {
            try (var input = Files.newInputStream(Path.of(name))) {
                settings.load(input);
            }
        }
        String[] properties = settings.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue()).toArray(String[]::new);
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
                .withPropertyValues(properties)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var environment = context.getEnvironment();
                    assertThat(environment.getProperty("spring.datasource.url")).startsWith("jdbc:h2:file:");
                    assertThat(environment.getProperty("spring.profiles.default")).isEqualTo("demo");
                    assertThat(environment.getProperty("security.jwt.secret")).hasSizeGreaterThanOrEqualTo(32);
                    assertThat(environment.getProperty("store.support-email")).isNotBlank();
                    assertThat(environment.getProperty("vnpay.tmn-code")).isEmpty();
                    assertThat(environment.getProperty("cloudinary.api-secret")).isEmpty();
                    assertThat(environment.getProperty("spring.mail.password")).isEmpty();
                });
    }

    @Test
    void defaultLocalProfileUsesAuthenticatedGmailInsteadOfCapturingOtp() throws Exception {
        // Read tracked configuration only. Never load real .env credentials in tests.
        Properties settings = new Properties();
        for (String name : new String[]{"application.properties", "application-demo.properties"}) {
            try (var input = Files.newInputStream(Path.of("src/main/resources", name))) {
                settings.load(input);
            }
        }
        settings.setProperty("MAIL_USERNAME", "sender@example.test");
        settings.setProperty("MAIL_PASSWORD", "test-only-not-a-real-password");
        String[] properties = settings.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue()).toArray(String[]::new);

        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withPropertyValues(properties)
            .run(context -> {
                assertThat(context).hasSingleBean(JavaMailSender.class).doesNotHaveBean("demoMailSender");
                var sender = (JavaMailSenderImpl) context.getBean(JavaMailSender.class);
                assertThat(sender.getHost()).isEqualTo("smtp.gmail.com");
                assertThat(sender.getPort()).isEqualTo(587);
                assertThat(sender.getUsername()).isEqualTo("sender@example.test");
                assertThat(sender.getPassword()).isEqualTo("test-only-not-a-real-password");
                assertThat(sender.getJavaMailProperties())
                    .containsEntry("mail.smtp.auth", "true")
                    .containsEntry("mail.smtp.starttls.required", "true")
                    .containsEntry("mail.smtp.ssl.checkserveridentity", "true");
                assertThat(context.getEnvironment().getProperty("spring.mail.from")).isEqualTo("sender@example.test");
                assertThat(context.getEnvironment().getProperty("spring.config.import")).contains(".env");
            });
    }
}
