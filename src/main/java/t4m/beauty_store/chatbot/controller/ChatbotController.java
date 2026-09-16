package t4m.beauty_store.chatbot.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import t4m.beauty_store.chatbot.dto.ChatbotRequest;
import t4m.beauty_store.chatbot.dto.ChatbotResponse;
import t4m.beauty_store.chatbot.service.ChatbotService;
import t4m.beauty_store.chatbot.service.ChatbotRateLimiter;

@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
public class ChatbotController {
    private final ChatbotService chatbotService;
    private final ChatbotRateLimiter chatbotRateLimiter;
    
    /**
     * Handle chatbot messages - public endpoint (no auth required for better UX)
     * Customers can ask questions without logging in
     */
    @PostMapping("/message")
    public ResponseEntity<ChatbotResponse> handleMessage(
            @Valid @RequestBody ChatbotRequest request, HttpServletRequest httpRequest) {
        // Forwarded headers are deliberately ignored here. A trusted proxy must
        // normalize remoteAddr at the container boundary.
        chatbotRateLimiter.check(httpRequest.getRemoteAddr());
        String conversationId = request.getConversationId();
        if (conversationId == null || conversationId.isEmpty()) {
            conversationId = chatbotService.generateConversationId();
        }
        String aiReply = chatbotService.generateResponse(request.getMessage(), conversationId);
        return ResponseEntity.ok(ChatbotResponse.success(aiReply, conversationId));
    }
    
    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("BeautyStore chatbot is ready");
    }
}
