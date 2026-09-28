package t4m.beauty_store.chatbot.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import t4m.beauty_store.chatbot.dto.ConversationState;
import t4m.beauty_store.config.StoreTime;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Step 5: Interaction Logging and Learning
 * Ghi lại và học từ tương tác để cải thiện chatbot
 */
@Service
@RequiredArgsConstructor
public class InteractionLoggingService {
    private static final Logger logger = LoggerFactory.getLogger(InteractionLoggingService.class);
    
    // In-memory analytics (in production, use database or analytics service)
    private final Map<String, ConversationState> conversationStates = new ConcurrentHashMap<>();
    private final Map<String, Integer> intentFrequency = new ConcurrentHashMap<>();
    private final Map<String, Integer> productClickThroughs = new ConcurrentHashMap<>();
    private final StoreTime storeTime;
    
    /**
     * Log conversation state
     */
    public void logConversation(String conversationId, ConversationState state) {
        conversationStates.put(conversationId, state);
        logger.debug("Chatbot conversation state updated: stage={}, messageCount={}",
                    state.getCurrentStage(), state.getMessageCount());
    }
    
    /**
     * Get or create conversation state
     */
    public ConversationState getOrCreateState(String conversationId) {
        return conversationStates.computeIfAbsent(conversationId, id -> {
            ConversationState state = new ConversationState();
            state.setConversationId(id);
            state.setStartTime(storeTime.currentDateTime());
            state.setLastUpdateTime(storeTime.currentDateTime());
            state.setCurrentStage(ConversationState.ConversationStage.GREETING);
            state.setMessageCount(0);
            return state;
        });
    }
    
    /**
     * Update conversation state
     */
    public void updateState(String conversationId, ConversationState.ConversationStage stage) {
        ConversationState state = getOrCreateState(conversationId);
        state.setCurrentStage(stage);
        state.setLastUpdateTime(storeTime.currentDateTime());
        logConversation(conversationId, state);
    }
    
    /**
     * Log intent occurrence for analytics
     */
    public void logIntent(String intent) {
        intentFrequency.merge(intent, 1, Integer::sum);
        logger.debug("Chatbot intent analytics updated");
    }
    
    /**
     * Log product click-through
     */
    public void logProductClickThrough(String conversationId, String productName) {
        ConversationState state = getOrCreateState(conversationId);
        state.recordClickThrough();
        productClickThroughs.merge(productName, 1, Integer::sum);
        
        logger.debug("Chatbot product click-through recorded");
    }
    
    /**
     * Log successful recommendation
     */
    public void logSuccessfulRecommendation(String conversationId) {
        ConversationState state = getOrCreateState(conversationId);
        state.recordSuccess();
        logger.debug("Chatbot recommendation outcome recorded");
    }
    
    /**
     * Log handoff request
     */
    public void logHandoffRequest(String conversationId, String reason) {
        ConversationState state = getOrCreateState(conversationId);
        state.setHandoffRequested(true);
        state.setHandoffReason(reason);
        logger.warn("A chatbot conversation requested human support");
    }
    
    /**
     * Get conversation metrics for analytics
     */
    public Map<String, Object> getConversationMetrics(String conversationId) {
        ConversationState state = conversationStates.get(conversationId);
        if (state == null) {
            return Map.of("error", "Conversation not found");
        }
        
        long durationMinutes = Duration.between(state.getStartTime(), 
                                                state.getLastUpdateTime()).toMinutes();
        
        return Map.of(
            "conversationId", conversationId,
            "duration_minutes", durationMinutes,
            "message_count", state.getMessageCount(),
            "successful_recommendations", state.getSuccessfulRecommendations(),
            "click_throughs", state.getClickThroughs(),
            "current_stage", state.getCurrentStage().toString(),
            "handoff_requested", state.isHandoffRequested()
        );
    }
    
    /**
     * Get global analytics
     */
    public Map<String, Object> getGlobalAnalytics() {
        int totalConversations = conversationStates.size();
        int handoffRequests = (int) conversationStates.values().stream()
            .filter(ConversationState::isHandoffRequested)
            .count();
        
        int totalClickThroughs = productClickThroughs.values().stream()
            .mapToInt(Integer::intValue)
            .sum();
        
        return Map.of(
            "total_conversations", totalConversations,
            "handoff_requests", handoffRequests,
            "handoff_rate", totalConversations > 0 ? (double) handoffRequests / totalConversations : 0.0,
            "total_click_throughs", totalClickThroughs,
            "intent_frequency", intentFrequency,
            "top_products", productClickThroughs
        );
    }
    
    /**
     * Cleanup old conversations (memory management)
     */
    public void cleanupOldConversations(int maxAgeHours) {
        var cutoff = storeTime.currentDateTime().minusHours(maxAgeHours);
        
        conversationStates.entrySet().removeIf(entry -> 
            entry.getValue().getLastUpdateTime().isBefore(cutoff)
        );
        
        logger.info("Cleaned up conversations older than {} hours. Remaining: {}", 
                    maxAgeHours, conversationStates.size());
    }
}
