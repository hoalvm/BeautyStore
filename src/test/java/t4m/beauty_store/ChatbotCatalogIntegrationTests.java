package t4m.beauty_store;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestTemplate;
import t4m.beauty_store.chatbot.service.ChatbotService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Exercises the real catalog with OSIV disabled, never contacting an AI API. */
@SpringBootTest
@ActiveProfiles("test")
class ChatbotCatalogIntegrationTests {
    @Autowired ChatbotService chatbot;
    private ClientHttpRequestFactory originalRequestFactory;

    @AfterEach
    void clearTestKey() {
        ReflectionTestUtils.setField(chatbot, "geminiApiKey", "");
        if (originalRequestFactory != null) {
            restTemplate().setRequestFactory(originalRequestFactory);
        }
    }

    @Test
    void noKeyReturnsHonestCatalogSuggestionsIncludingRealStockAndPrices() {
        String result = chatbot.generateResponse("Tìm serum đang còn hàng", "offline-catalog");

        assertThat(result).contains("không dùng AI", "Tinh chất HA cấp ẩm", "còn", "patch test")
            .doesNotContain("tạm gián đoạn");
    }

    @Test
    void offlinePoliciesDescribeActualShippingAndReturnRules() {
        String result = chatbot.generateResponse("Chính sách đổi trả và phí giao hàng", "offline-policy");

        assertThat(result).contains("không dùng AI", "30.000", "500.000", "7 ngày", "48 giờ")
            .doesNotContain("tạm gián đoạn");
    }

    @Test
    void catalogIsMaterializedBeforeExternalCallAndOutsideDatabaseTransaction() {
        ReflectionTestUtils.setField(chatbot, "geminiApiKey", "test-key-not-a-real-secret");
        RestTemplate rest = restTemplate();
        originalRequestFactory = rest.getRequestFactory();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
            .andExpect(request ->
                assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .as("external network requests must not retain a database transaction").isFalse())
            .andExpect(content().string(containsString("DỮ LIỆU CATALOG")))
            .andExpect(content().string(containsString("Tinh chất HA cấp ẩm")))
            .andRespond(withSuccess("""
                {"candidates":[{"content":{"parts":[{"text":"Catalog đã được đọc thành công."}]}}]}
                """, MediaType.APPLICATION_JSON));

        String response = chatbot.generateResponse("Tìm serum cấp ẩm", "configured-catalog");

        assertThat(response).isEqualTo("Catalog đã được đọc thành công.");
        server.verify();
    }

    private RestTemplate restTemplate() {
        return (RestTemplate) ReflectionTestUtils.getField(chatbot, "restTemplate");
    }
}
