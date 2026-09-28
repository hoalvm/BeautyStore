package t4m.beauty_store.support.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import t4m.beauty_store.config.ApiException;
import t4m.beauty_store.config.StoreTime;
import t4m.beauty_store.auth.entity.Role;
import t4m.beauty_store.auth.entity.User;
import t4m.beauty_store.support.entity.SupportMessage;
import t4m.beauty_store.support.entity.SupportSession;
import t4m.beauty_store.support.repository.SupportMessageRepository;
import t4m.beauty_store.support.repository.SupportSessionRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SupportServiceTests {
    private SupportSessionRepository sessionRepository;
    private SupportMessageRepository messageRepository;
    private SupportService service;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(SupportSessionRepository.class);
        messageRepository = mock(SupportMessageRepository.class);
        service = new SupportService(sessionRepository, messageRepository, fixedTime());
        when(sessionRepository.save(any(SupportSession.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.save(any(SupportMessage.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void guestSessionIsBoundToOpaqueHash() {
        when(sessionRepository.findByGuestTokenHash("hash-1")).thenReturn(Optional.empty());

        SupportSession session = service.createOrGetGuestSession(
            "hash-1", " guest@example.com ", " Khách thử ");

        assertThat(session.getSessionId()).isNotBlank();
        assertThat(session.getGuestTokenHash()).isEqualTo("hash-1");
        assertThat(session.getUserEmail()).isEqualTo("guest@example.com");
        assertThat(session.getUserName()).isEqualTo("Khách thử");
        verify(sessionRepository).save(session);
    }

    @Test
    void guestCannotReadAnotherGuestConversation() {
        SupportSession session = guestSession("session-1", "owner-hash");
        when(sessionRepository.findBySessionId("session-1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.getAuthorizedMessages("session-1", null, "wrong-hash"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("403");
        verifyNoInteractions(messageRepository);
    }

    @Test
    void memberCanOnlyUseTheirOwnConversation() {
        SupportSession session = new SupportSession();
        session.setSessionId("member-session");
        session.setUserId(7L);
        when(sessionRepository.findBySessionId("member-session")).thenReturn(Optional.of(session));
        when(messageRepository.findBySessionIdOrderByCreatedAtAsc("member-session"))
            .thenReturn(List.of());

        assertThat(service.getAuthorizedMessages("member-session", user(7L, false), null)).isEmpty();
        assertThatThrownBy(() -> service.getAuthorizedMessages(
            "member-session", user(8L, false), null))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void customerMessageUsesServerOwnedIdentityAndTrimsText() {
        SupportSession session = guestSession("session-1", "owner-hash");
        session.setUserEmail("owner@example.com");
        session.setUserName("Khách an toàn");
        when(sessionRepository.findBySessionIdForUpdate("session-1")).thenReturn(Optional.of(session));

        SupportMessage saved = service.saveCustomerMessage(
            "session-1", null, "owner-hash", "  Xin tư vấn kem chống nắng  ");

        assertThat(saved.getSenderType()).isEqualTo("USER");
        assertThat(saved.getUserEmail()).isEqualTo("owner@example.com");
        assertThat(saved.getUserName()).isEqualTo("Khách an toàn");
        assertThat(saved.getMessage()).isEqualTo("Xin tư vấn kem chống nắng");
        assertThat(session.getUnreadCount()).isEqualTo(1);
    }

    @Test
    void adminMessageCannotOverrideSenderIdentity() {
        SupportSession session = guestSession("session-1", "owner-hash");
        when(sessionRepository.findBySessionIdForUpdate("session-1")).thenReturn(Optional.of(session));

        SupportMessage saved = service.saveAdminMessage("session-1", "  BeautyStore xin chào  ");

        assertThat(saved.getSenderType()).isEqualTo("ADMIN");
        assertThat(saved.getUserName()).isEqualTo("Chuyên viên BeautyStore");
        assertThat(saved.getUserEmail()).isNull();
        assertThat(saved.getMessage()).isEqualTo("BeautyStore xin chào");
        assertThat(session.getUnreadCount()).isZero();
    }

    @Test
    void adminCanReadAnyConversation() {
        SupportSession session = guestSession("session-1", "owner-hash");
        when(sessionRepository.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(messageRepository.findBySessionIdOrderByCreatedAtAsc("session-1"))
            .thenReturn(List.of());

        assertThat(service.getAuthorizedMessages("session-1", user(99L, true), null)).isEmpty();
    }

    @Test
    void closedSessionRejectsNewMessagesUntilReopened() {
        SupportSession session = guestSession("session-1", "owner-hash");
        session.setStatus("CLOSED");
        when(sessionRepository.findBySessionIdForUpdate("session-1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.saveCustomerMessage(
            "session-1", null, "owner-hash", "Tin nhắn mới"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("đã đóng");
        assertThatThrownBy(() -> service.saveAdminMessage("session-1", "Phản hồi mới"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("đã đóng");
        verifyNoInteractions(messageRepository);
    }

    @Test
    void customerMessageLocksSessionBeforeIncrementingUnreadCount() {
        SupportSession session = guestSession("session-locked", "owner-hash");
        session.setUnreadCount(4);
        when(sessionRepository.findBySessionIdForUpdate("session-locked"))
            .thenReturn(Optional.of(session));

        service.saveCustomerMessage("session-locked", null, "owner-hash", "Tin nhắn");

        assertThat(session.getUnreadCount()).isEqualTo(5);
        verify(sessionRepository).findBySessionIdForUpdate("session-locked");
        verify(sessionRepository).save(session);
    }

    private static SupportSession guestSession(String sessionId, String hash) {
        SupportSession session = new SupportSession();
        session.setSessionId(sessionId);
        session.setGuestTokenHash(hash);
        session.setStatus("ACTIVE");
        return session;
    }

    private static User user(long id, boolean admin) {
        User user = new User();
        user.setId(id);
        if (admin) {
            Role role = new Role();
            role.setRname("ROLE_ADMIN");
            user.setRoles(Set.of(role));
        }
        return user;
    }

    private static StoreTime fixedTime() {
        return new StoreTime(Clock.fixed(Instant.parse("2026-06-15T03:00:00Z"), StoreTime.ZONE));
    }
}
