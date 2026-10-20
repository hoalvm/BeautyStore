package t4m.beauty_store.demo;

import jakarta.mail.internet.MimeMessage;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Email capture is test-only: the application always uses real SMTP. */
@TestConfiguration(proxyBeanMethods = false)
public class TestMailConfiguration {
    @Bean
    RecordingMailbox recordingMailbox() {
        return new RecordingMailbox();
    }

    @Bean
    @Primary
    JavaMailSender testMailSender(RecordingMailbox mailbox) {
        return new JavaMailSenderImpl() {
            @Override
            protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) {
                try {
                    for (MimeMessage message : mimeMessages) {
                        message.saveChanges();
                        mailbox.deliver(message);
                    }
                } catch (Exception exception) {
                    throw new MailSendException("Cannot render local demo mail", exception);
                }
            }
        };
    }
}
