package t4m.beauty_store.chatbot.service;

import org.junit.jupiter.api.Test;
import t4m.beauty_store.config.StoreProperties;
import t4m.beauty_store.config.StoreTime;
import t4m.beauty_store.product.repository.CategoryRepository;
import t4m.beauty_store.product.repository.ProductRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;

class ChatbotServiceSafetyTests {

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final ChatbotService service = new ChatbotService(
            productRepository,
            categoryRepository,
            new IntentRecognitionService(),
            new InteractionLoggingService(new StoreTime(
                Clock.fixed(Instant.parse("2026-06-15T03:00:00Z"), StoreTime.ZONE))),
            new StoreProperties());

    @Test
    void handlesPossibleCosmeticReactionWithoutCallingCatalogOrAi() {
        String response = service.generateResponse(
                "Da tôi bị kích ứng và bỏng rát sau khi dùng retinol", "safe-conversation");

        assertThat(response).contains("ngừng dùng").contains("bác sĩ da liễu");
        verifyNoInteractions(productRepository, categoryRepository);
    }

    @Test
    void refusesSensitiveCredentialsBeforeExternalProcessing() {
        String response = service.generateResponse(
                "OTP: 123456 và số thẻ 4111 1111 1111 1111", "safe-conversation");

        assertThat(response).contains("không nên gửi").contains("số thẻ");
        verifyNoInteractions(productRepository, categoryRepository);
    }
}
