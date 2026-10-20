package t4m.beauty_store.demo;

import jakarta.mail.internet.MimeMessage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
@Profile("demo")
public class DemoMailConfiguration {
    @Bean
    @Primary
    JavaMailSender demoMailSender(DemoMailbox mailbox) {
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
