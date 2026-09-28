package t4m.beauty_store.support.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import t4m.beauty_store.config.ApiException;
import t4m.beauty_store.config.StoreTime;
import t4m.beauty_store.support.dto.SupportSessionDto;
import t4m.beauty_store.support.dto.SupportMessageResponse;
import t4m.beauty_store.support.entity.SupportMessage;
import t4m.beauty_store.support.entity.SupportSession;
import t4m.beauty_store.auth.entity.User;
import t4m.beauty_store.support.repository.SupportMessageRepository;
import t4m.beauty_store.support.repository.SupportSessionRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SupportService {

    private final SupportSessionRepository sessionRepository;
    private final SupportMessageRepository messageRepository;
    private final StoreTime storeTime;

    @Transactional
    public SupportSession createOrGetSession(Long userId, String userEmail, String userName) {
        SupportSession session = sessionRepository.findByUserId(userId)
                .orElseGet(() -> {
                    SupportSession created = new SupportSession();
                    created.setSessionId(UUID.randomUUID().toString());
                    created.setUserId(userId);
                    created.setUserEmail(userEmail);
                    created.setUserName(userName);
                    created.setStatus("ACTIVE");
                    return sessionRepository.save(created);
                });
        if (!"ACTIVE".equals(session.getStatus())) {
            session.setStatus("ACTIVE");
            session.setUpdatedAt(storeTime.currentDateTime());
            session = sessionRepository.save(session);
        }
        return session;
    }

    @Transactional
    public SupportSession createOrGetGuestSession(String guestTokenHash, String guestEmail, String guestName) {
        if (guestTokenHash == null || guestTokenHash.isBlank()) {
            throw new IllegalArgumentException("Không thể tạo phiên hỗ trợ khách");
        }
        SupportSession session = sessionRepository.findByGuestTokenHash(guestTokenHash)
            .orElseGet(() -> {
                SupportSession created = new SupportSession();
                created.setSessionId(UUID.randomUUID().toString());
                created.setGuestTokenHash(guestTokenHash);
                created.setUserEmail(clean(guestEmail, 255));
                created.setUserName(defaultGuestName(guestName));
                created.setStatus("ACTIVE");
                return sessionRepository.save(created);
            });
        if (!"ACTIVE".equals(session.getStatus())) {
            session.setStatus("ACTIVE");
            session.setUpdatedAt(storeTime.currentDateTime());
            session = sessionRepository.save(session);
        }
        return session;
    }

    public SupportSessionDto toDto(SupportSession session) {
        return convertToDto(session);
    }

    @Transactional(readOnly = true)
    public List<SupportMessageResponse> getAuthorizedMessages(
            String sessionId, User viewer, String guestTokenHash) {
        SupportSession session = requireAuthorizedSession(sessionId, viewer, guestTokenHash);
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getSessionId()).stream()
            .map(SupportMessageResponse::fromEntity)
            .toList();
    }

    @Transactional
    public SupportMessage saveCustomerMessage(
            String sessionId, User viewer, String guestTokenHash, String text) {
        SupportSession session = requireAuthorizedSessionForUpdate(sessionId, viewer, guestTokenHash);
        requireActive(session);
        String messageText = clean(text, 2000);
        if (messageText == null) throw new IllegalArgumentException("Tin nhắn không được để trống");
        SupportMessage message = new SupportMessage();
        message.setSessionId(session.getSessionId());
        message.setUserId(session.getUserId());
        message.setUserEmail(session.getUserEmail());
        message.setUserName(session.getUserName());
        message.setSenderType("USER");
        message.setMessage(messageText);
        message.setCreatedAt(storeTime.currentDateTime());
        SupportMessage saved = messageRepository.save(message);
        session.setUpdatedAt(storeTime.currentDateTime());
        session.setUnreadCount(session.getUnreadCount() + 1);
        sessionRepository.save(session);
        return saved;
    }

    @Transactional
    public SupportMessage saveAdminMessage(String sessionId, String text) {
        SupportSession session = sessionRepository.findBySessionIdForUpdate(sessionId)
            .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiên hỗ trợ"));
        requireActive(session);
        String messageText = clean(text, 2000);
        if (messageText == null) {
            throw new IllegalArgumentException("Tin nhắn không được để trống");
        }
        SupportMessage message = new SupportMessage();
        message.setSessionId(session.getSessionId());
        message.setUserId(null);
        message.setUserEmail(null);
        message.setUserName("Chuyên viên BeautyStore");
        message.setSenderType("ADMIN");
        message.setMessage(messageText);
        message.setCreatedAt(storeTime.currentDateTime());
        SupportMessage saved = messageRepository.save(message);
        session.setUpdatedAt(storeTime.currentDateTime());
        sessionRepository.save(session);
        return saved;
    }

    public List<SupportMessage> getSessionMessages(String sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    public List<SupportSessionDto> getAllActiveSessions() {
        List<SupportSession> sessions = sessionRepository.findByStatusOrderByUpdatedAtDesc("ACTIVE");
        return sessions.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    @Transactional
    public void markMessagesAsRead(String sessionId, String senderType) {
        SupportSession session = sessionRepository.findBySessionIdForUpdate(sessionId)
            .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiên hỗ trợ"));
        messageRepository.markUnreadFromOtherSenderAsRead(sessionId, senderType);
        if ("ADMIN".equals(senderType)) {
            session.setUnreadCount(0);
            sessionRepository.save(session);
        }
    }

    @Transactional
    public void markCustomerViewRead(String sessionId, User viewer, String guestTokenHash) {
        SupportSession session = requireAuthorizedSessionForUpdate(sessionId, viewer, guestTokenHash);
        messageRepository.markUnreadFromOtherSenderAsRead(session.getSessionId(), "USER");
    }

    @Transactional(readOnly = true)
    public long getCustomerUnreadCount(String sessionId, User viewer, String guestTokenHash) {
        SupportSession session = requireAuthorizedSession(sessionId, viewer, guestTokenHash);
        return messageRepository.countBySessionIdAndSenderTypeAndIsReadFalse(session.getSessionId(), "ADMIN");
    }

    @Transactional
    public void closeSession(String sessionId) {
        sessionRepository.findBySessionIdForUpdate(sessionId)
                .ifPresent(session -> {
                    session.setStatus("CLOSED");
                    session.setUpdatedAt(storeTime.currentDateTime());
                    sessionRepository.save(session);
                });
    }

    public long getUnreadCount(String sessionId, String senderType) {
        return messageRepository.countBySessionIdAndSenderTypeAndIsReadFalse(sessionId, senderType);
    }

    private SupportSessionDto convertToDto(SupportSession session) {
        SupportSessionDto dto = new SupportSessionDto();
        dto.setId(session.getId());
        dto.setSessionId(session.getSessionId());
        dto.setUserId(session.getUserId());
        dto.setUserEmail(session.getUserEmail());
        dto.setUserName(session.getUserName());
        dto.setStatus(session.getStatus());
        dto.setCreatedAt(session.getCreatedAt());
        dto.setUpdatedAt(session.getUpdatedAt());
        dto.setUnreadCount(session.getUnreadCount());

        messageRepository.findTopBySessionIdOrderByCreatedAtDescIdDesc(session.getSessionId())
            .ifPresent(message -> dto.setLastMessage(message.getMessage()));

        return dto;
    }

    private SupportSession requireAuthorizedSession(String sessionId, User viewer, String guestTokenHash) {
        SupportSession session = sessionRepository.findBySessionId(sessionId)
            .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiên hỗ trợ"));
        authorize(session, viewer, guestTokenHash);
        return session;
    }

    private SupportSession requireAuthorizedSessionForUpdate(
            String sessionId, User viewer, String guestTokenHash) {
        SupportSession session = sessionRepository.findBySessionIdForUpdate(sessionId)
            .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiên hỗ trợ"));
        authorize(session, viewer, guestTokenHash);
        return session;
    }

    private static void authorize(SupportSession session, User viewer, String guestTokenHash) {
        if (viewer != null && isAdmin(viewer)) return;
        boolean memberOwner = viewer != null && session.getUserId() != null
            && session.getUserId().equals(viewer.getId());
        boolean guestOwner = viewer == null && guestTokenHash != null
            && guestTokenHash.equals(session.getGuestTokenHash());
        if (!memberOwner && !guestOwner) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập phiên hỗ trợ này");
        }
    }

    private static boolean isAdmin(User user) {
        return user.getAuthorities().stream()
            .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private static void requireActive(SupportSession session) {
        if (!"ACTIVE".equals(session.getStatus())) {
            throw ApiException.conflict("Phiên hỗ trợ đã đóng; vui lòng mở một phiên mới");
        }
    }

    private static String defaultGuestName(String value) {
        String cleaned = clean(value, 120);
        return cleaned == null ? "Khách BeautyStore" : cleaned;
    }

    private static String clean(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String cleaned = value.trim();
        if (cleaned.length() > maxLength) {
            throw new IllegalArgumentException("Dữ liệu hỗ trợ vượt quá độ dài cho phép");
        }
        return cleaned;
    }
}
