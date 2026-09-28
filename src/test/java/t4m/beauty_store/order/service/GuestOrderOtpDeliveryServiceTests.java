package t4m.beauty_store.order.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import t4m.beauty_store.auth.service.EmailService;
import t4m.beauty_store.order.repository.GuestOrderAccessRepository;

import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;

class GuestOrderOtpDeliveryServiceTests {
    private EmailService emailService;
    private GuestOrderAccessRepository accessRepository;
    private GuestOrderOtpDeliveryService service;

    @BeforeEach
    void setUp() {
        emailService = mock(EmailService.class);
        accessRepository = mock(GuestOrderAccessRepository.class);
        service = new GuestOrderOtpDeliveryService(emailService, accessRepository);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void emailIsNotDispatchedUntilTheChallengeTransactionCommits() {
        when(emailService.sendGuestOrderOtp(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(null));
        TransactionSynchronizationManager.initSynchronization();

        service.schedule(7L, "guest@example.com", "Guest", "BEA-7", "123456");

        verifyNoInteractions(emailService);
        TransactionSynchronizationManager.getSynchronizations()
            .forEach(TransactionSynchronization::afterCommit);
        verify(emailService).sendGuestOrderOtp(
            "guest@example.com", "Guest", "BEA-7", "123456");
        verify(accessRepository, never()).deleteUnverifiedById(anyLong());
    }

    @Test
    void failedDeliveryDeletesOnlyTheUnverifiedChallenge() {
        when(emailService.sendGuestOrderOtp(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("smtp unavailable")));
        when(accessRepository.deleteUnverifiedById(7L)).thenReturn(1);

        service.schedule(7L, "guest@example.com", "Guest", "BEA-7", "123456");

        verify(accessRepository).deleteUnverifiedById(7L);
    }
}
