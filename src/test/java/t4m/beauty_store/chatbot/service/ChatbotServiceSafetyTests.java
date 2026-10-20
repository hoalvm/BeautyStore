package t4m.beauty_store.chatbot.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
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
            new StoreProperties(), mock(PlatformTransactionManager.class), mock(Environment.class));

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

    @Test
    void humanHandoffExplainsTheExplicitButtonInsteadOfPretendingToOpenASession() {
        assertThat(service.generateResponse("Cho tôi gặp nhân viên hỗ trợ", "human-handoff"))
            .contains("chọn nút Hỗ trợ trực tiếp", "chưa tự tạo phiên");
        verifyNoInteractions(productRepository, categoryRepository);
    }

    @Test
    void demoProfileNeverCallsExternalAiEvenWithAnAccidentalKey() {
        MockEnvironment demo = new MockEnvironment();
        demo.setActiveProfiles("demo");
        ChatbotService local = new ChatbotService(productRepository, categoryRepository,
            new IntentRecognitionService(),
            new InteractionLoggingService(new StoreTime(
                Clock.fixed(Instant.parse("2026-06-15T03:00:00Z"), StoreTime.ZONE))),
            new StoreProperties(), mock(PlatformTransactionManager.class), demo);
        ReflectionTestUtils.setField(local, "geminiApiKey", "must-not-leave-this-machine");
        MockRestServiceServer server = MockRestServiceServer.bindTo(
            (RestTemplate) ReflectionTestUtils.getField(local, "restTemplate")).build();

        assertThat(local.generateResponse("Chính sách đổi trả", "demo-profile"))
            .contains("không dùng AI", "7 ngày");
        server.verify(); // Zero expected requests: any external call fails this test.
    }
}
