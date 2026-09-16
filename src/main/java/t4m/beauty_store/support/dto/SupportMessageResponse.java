package t4m.beauty_store.support.dto;

import lombok.Builder;
import lombok.Data;
import t4m.beauty_store.support.entity.SupportMessage;

import java.time.LocalDateTime;

@Data
@Builder
public class SupportMessageResponse {
    private Long id;
    private String senderType;
    private String senderName;
    private String message;
    private LocalDateTime createdAt;

    public static SupportMessageResponse fromEntity(SupportMessage message) {
        return SupportMessageResponse.builder()
            .id(message.getId())
            .senderType(message.getSenderType())
            .senderName("ADMIN".equals(message.getSenderType()) ? "Chuyên viên BeautyStore" : "Khách hàng")
            .message(message.getMessage())
            .createdAt(message.getCreatedAt())
            .build();
    }
}
