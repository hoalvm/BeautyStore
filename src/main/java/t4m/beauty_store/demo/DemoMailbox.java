package t4m.beauty_store.demo;

import jakarta.mail.MessagingException;
import jakarta.mail.Part;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** In-memory replacement for a mail server, exclusively in the loopback-only demo. */
@Component
@Profile("demo")
public class DemoMailbox {
    private final ArrayDeque<Mail> messages = new ArrayDeque<>();

    public synchronized void deliver(MimeMessage message) throws MessagingException, IOException {
        prune();
        String recipient = Arrays.stream(message.getAllRecipients())
                .map(Object::toString).collect(java.util.stream.Collectors.joining(", "));
        messages.addFirst(new Mail(UUID.randomUUID().toString(), recipient, message.getSubject(),
                Instant.now(), html(message)));
        while (messages.size() > 100) messages.removeLast();
    }

    public synchronized List<Summary> list() {
        prune();
        return messages.stream().map(mail ->
                new Summary(mail.id(), mail.recipient(), mail.subject(), mail.createdAt())).toList();
    }

    public synchronized String read(String id) {
        prune();
        return messages.stream().filter(mail -> mail.id().equals(id)).findFirst()
                .map(Mail::html).orElse(null);
    }

    private void prune() {
        Instant cutoff = Instant.now().minusSeconds(600);
        messages.removeIf(mail -> mail.createdAt().isBefore(cutoff));
    }

    private String html(Part part) throws MessagingException, IOException {
        if (part.isMimeType("text/html")) return (String) part.getContent();
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                String content = html(multipart.getBodyPart(i));
                if (!content.isEmpty()) return content;
            }
        }
        return "";
    }

    public record Summary(String id, String recipient, String subject, Instant createdAt) {}
    private record Mail(String id, String recipient, String subject, Instant createdAt, String html) {}
}
