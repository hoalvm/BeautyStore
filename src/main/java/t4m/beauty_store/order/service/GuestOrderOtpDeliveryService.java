package t4m.beauty_store.order.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import t4m.beauty_store.auth.service.EmailService;
import t4m.beauty_store.order.repository.GuestOrderAccessRepository;

import java.util.concurrent.CompletableFuture;

/** Delivers a persisted guest-order challenge only after its transaction commits. */
@Service
@RequiredArgsConstructor
public class GuestOrderOtpDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(GuestOrderOtpDeliveryService.class);

    private final EmailService emailService;
    private final GuestOrderAccessRepository accessRepository;

    public void schedule(Long challengeId, String email, String customerName,
                         String orderNumber, String otp) {
        Runnable delivery = () -> deliver(challengeId, email, customerName, orderNumber, otp);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delivery.run();
                }
            });
            return;
        }
        delivery.run();
    }

    private void deliver(Long challengeId, String email, String customerName,
                         String orderNumber, String otp) {
        try {
            CompletableFuture<Void> result = emailService.sendGuestOrderOtp(
                email, customerName, orderNumber, otp);
            if (result == null) {
                invalidate(challengeId);
                return;
            }
            result.whenComplete((ignored, error) -> {
                if (error != null) invalidate(challengeId);
            });
        } catch (RuntimeException exception) {
            invalidate(challengeId);
        }
    }

    private void invalidate(Long challengeId) {
        if (challengeId != null && accessRepository.deleteUnverifiedById(challengeId) > 0) {
            log.warn("Invalidated a guest-order OTP challenge after email delivery failed");
        }
    }
}
