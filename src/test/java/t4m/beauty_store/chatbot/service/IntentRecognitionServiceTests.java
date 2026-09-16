package t4m.beauty_store.chatbot.service;

import org.junit.jupiter.api.Test;
import t4m.beauty_store.chatbot.dto.IntentClassification;

import static org.assertj.core.api.Assertions.assertThat;

class IntentRecognitionServiceTests {

    private final IntentRecognitionService service = new IntentRecognitionService();

    @Test
    void extractsBeautyProfileBudgetAndRoutine() {
        IntentClassification result = service.classifyIntent(
                "Gợi ý routine skincare sáng cho da dầu mụn dưới 600k", "");

        assertThat(result.getIntent()).isEqualTo(IntentClassification.Intent.ROUTINE_RECOMMENDATION);
        assertThat(result.getSlotAsString(IntentClassification.SLOT_SKIN_TYPE)).isEqualTo("oily");
        assertThat(result.getSlotAsString(IntentClassification.SLOT_CONCERN)).isEqualTo("acne");
        assertThat(result.getSlotAsInteger(IntentClassification.SLOT_MAX_BUDGET)).isEqualTo(600_000);
    }

    @Test
    void recognizesShadeAndMakeupCategory() {
        IntentClassification result = service.classifyIntent(
                "Tư vấn chọn màu son tone ấm, finish lì", "");

        assertThat(result.getIntent()).isEqualTo(IntentClassification.Intent.SHADE_MATCHING);
        assertThat(result.getSlotAsString(IntentClassification.SLOT_CATEGORY)).isEqualTo("Trang điểm");
        assertThat(result.getSlotAsString(IntentClassification.SLOT_SHADE)).isEqualTo("warm");
        assertThat(result.getSlotAsString(IntentClassification.SLOT_FINISH)).isEqualTo("matte");
    }

    @Test
    void safetyIntentOverridesIngredientRecommendation() {
        IntentClassification result = service.classifyIntent(
                "Da tôi bị kích ứng và bỏng rát sau khi dùng retinol", "");

        assertThat(result.getIntent()).isEqualTo(IntentClassification.Intent.SAFETY_INQUIRY);
        assertThat(result.getSlotAsString(IntentClassification.SLOT_INGREDIENT)).isEqualTo("retinol");
    }

    @Test
    void recognizesSunscreenSpfRequest() {
        IntentClassification result = service.classifyIntent(
                "Tìm kem chống nắng SPF 50 cho da nhạy cảm", "");

        assertThat(result.getIntent()).isEqualTo(IntentClassification.Intent.PRODUCT_SEARCH);
        assertThat(result.getSlotAsString(IntentClassification.SLOT_CATEGORY)).isEqualTo("Chống nắng");
        assertThat(result.getSlotAsString(IntentClassification.SLOT_SKIN_TYPE)).isEqualTo("sensitive");
        assertThat(result.getSlotAsString(IntentClassification.SLOT_SPF)).isEqualTo("SPF 50");
    }
}
