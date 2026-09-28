package t4m.beauty_store.support.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import t4m.beauty_store.support.entity.SupportSession;
import t4m.beauty_store.support.repository.SupportMessageRepository;
import t4m.beauty_store.support.repository.SupportSessionRepository;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SupportConcurrencyIntegrationTests {
    @Autowired SupportService supportService;
    @Autowired SupportSessionRepository sessionRepository;
    @Autowired SupportMessageRepository messageRepository;
    @Autowired PlatformTransactionManager transactionManager;

    private String sessionId;
    private String guestTokenHash;

    @BeforeEach
    void createFixture() {
        String suffix = UUID.randomUUID().toString();
        sessionId = "support-race-" + suffix;
        guestTokenHash = "guest-hash-" + suffix;
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            SupportSession session = new SupportSession();
            session.setSessionId(sessionId);
            session.setGuestTokenHash(guestTokenHash);
            session.setUserName("Concurrency guest");
            session.setStatus("ACTIVE");
            sessionRepository.save(session);
        });
    }

    @AfterEach
    void removeFixture() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            messageRepository.deleteAll(
                messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId));
            sessionRepository.findBySessionId(sessionId).ifPresent(sessionRepository::delete);
        });
    }

    @Test
    void concurrentCustomerMessagesDoNotLoseUnreadIncrement() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> sendWhenReleased("Tin nhắn thứ nhất", ready, start));
            Future<?> second = executor.submit(() -> sendWhenReleased("Tin nhắn thứ hai", ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);

            SupportSession persisted = new TransactionTemplate(transactionManager)
                .execute(status -> sessionRepository.findBySessionId(sessionId).orElseThrow());
            assertThat(persisted.getUnreadCount()).isEqualTo(2);
            assertThat(messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).hasSize(2);
        } finally {
            executor.shutdownNow();
        }
    }

    private void sendWhenReleased(
            String text, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
            supportService.saveCustomerMessage(sessionId, null, guestTokenHash, text);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
