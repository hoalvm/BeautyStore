package t4m.beauty_store.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Conversation State - Step 1 & 5: Track conversation context and learning
 * Lưu trạng thái hội thoại và học từ tương tác
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationState {
    
    private String conversationId;
    private LocalDateTime startTime;
    private LocalDateTime lastUpdateTime;
    
    // Step 1: Input collection state
    private ConversationStage currentStage;
    private Map<String, Object> collectedInfo = new HashMap<>();
    
    // Step 5: Learning metrics
    private int messageCount;
    private int successfulRecommendations;
    private int clickThroughs;
    private boolean handoffRequested;
    private String handoffReason;
    
    // User context for personalization (Step 7)
    private String userId; // null for anonymous
    private Map<String, Object> preferences = new HashMap<>();
    
    public enum ConversationStage {
        GREETING,               // Chào hỏi ban đầu
        COLLECTING_BEAUTY_PROFILE, // Thu thập loại da/tóc và nhu cầu
        COLLECTING_PREFERENCES, // Thu thập ngân sách, thành phần, màu sắc
        SHOWING_PRODUCTS,       // Đang hiển thị sản phẩm
        PRODUCT_SELECTED,       // Khách đã chọn sản phẩm
        HANDLING_INQUIRY,       // Xử lý câu hỏi cụ thể
        AWAITING_HANDOFF,       // Đang chờ chuyển nhân viên
        COMPLETED              // Hoàn thành
    }
    
    // Helper methods
    public void setInfo(String key, Object value) {
        collectedInfo.put(key, value);
        lastUpdateTime = LocalDateTime.now();
    }
    
    public Object getInfo(String key) {
        return collectedInfo.get(key);
    }
    
    public void setPreference(String key, Object value) {
        preferences.put(key, value);
    }
    
    public void incrementMessageCount() {
        this.messageCount++;
        this.lastUpdateTime = LocalDateTime.now();
    }
    
    public void recordSuccess() {
        this.successfulRecommendations++;
    }
    
    public void recordClickThrough() {
        this.clickThroughs++;
    }
    
    public boolean isAnonymous() {
        return userId == null || userId.isEmpty();
    }
}
