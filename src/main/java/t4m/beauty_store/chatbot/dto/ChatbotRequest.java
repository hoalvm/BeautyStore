package t4m.beauty_store.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotRequest {
    @NotBlank(message = "Vui lòng nhập câu hỏi")
    @Size(max = 1000, message = "Câu hỏi không được vượt quá 1000 ký tự")
    private String message;

    @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$", message = "Mã hội thoại không hợp lệ")
    private String conversationId;
}
