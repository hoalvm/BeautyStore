package t4m.beauty_store.support.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import t4m.beauty_store.auth.entity.User;
import t4m.beauty_store.cart.service.GuestCartCookieService;
import t4m.beauty_store.support.dto.*;
import t4m.beauty_store.support.entity.SupportMessage;
import t4m.beauty_store.support.entity.SupportSession;
import t4m.beauty_store.support.service.SupportService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
public class SupportApiController {
    private final SupportService supportService;
    private final GuestCartCookieService guestCookieService;

    @PostMapping("/session")
    public ResponseEntity<SupportSessionDto> createSession(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody(required = false) GuestSupportRequest guest,
            HttpServletRequest request,
            HttpServletResponse response) {
        SupportSession session;
        if (user != null) {
            session = supportService.createOrGetSession(user.getId(), user.getEmail(), user.getName());
        } else {
            String tokenHash = guestCookieService.resolveTokenHash(request, response);
            session = supportService.createOrGetGuestSession(tokenHash,
                guest == null ? null : guest.getEmail(), guest == null ? null : guest.getName());
        }
        return ResponseEntity.ok(supportService.toDto(session));
    }

    @GetMapping("/session/{sessionId}/messages")
    public List<SupportMessageResponse> getMessages(
            @PathVariable String sessionId,
            @AuthenticationPrincipal User user,
            HttpServletRequest request) {
        return supportService.getAuthorizedMessages(
            sessionId, user, guestCookieService.resolveExistingTokenHash(request));
    }

    @PostMapping("/session/{sessionId}/messages")
    public SupportMessageResponse sendCustomerMessage(
            @PathVariable String sessionId,
            @AuthenticationPrincipal User user,
            HttpServletRequest request,
            @Valid @RequestBody SupportMessageRequest body) {
        SupportMessage saved = supportService.saveCustomerMessage(
            sessionId, user, guestCookieService.resolveExistingTokenHash(request), body.getMessage());
        return SupportMessageResponse.fromEntity(saved);
    }

    @PostMapping("/session/{sessionId}/read")
    public Map<String, Boolean> markAsRead(
            @PathVariable String sessionId,
            @AuthenticationPrincipal User user,
            HttpServletRequest request) {
        supportService.markCustomerViewRead(
            sessionId, user, guestCookieService.resolveExistingTokenHash(request));
        return Map.of("success", true);
    }

    @GetMapping("/session/{sessionId}/unread")
    public Map<String, Long> getUnreadCount(
            @PathVariable String sessionId,
            @AuthenticationPrincipal User user,
            HttpServletRequest request) {
        return Map.of("unreadCount", supportService.getCustomerUnreadCount(
            sessionId, user, guestCookieService.resolveExistingTokenHash(request)));
    }

    @GetMapping("/admin/sessions")
    public List<SupportSessionDto> getAllSessions() {
        return supportService.getAllActiveSessions();
    }

    @PostMapping("/admin/session/{sessionId}/close")
    public Map<String, Boolean> closeSession(@PathVariable String sessionId) {
        supportService.closeSession(sessionId);
        return Map.of("success", true);
    }

    @PostMapping("/admin/session/{sessionId}/read")
    public Map<String, Boolean> markAdminViewRead(@PathVariable String sessionId) {
        supportService.markMessagesAsRead(sessionId, "ADMIN");
        return Map.of("success", true);
    }

    @PostMapping("/admin/session/{sessionId}/messages")
    public SupportMessageResponse sendAdminMessage(
            @PathVariable String sessionId,
            @Valid @RequestBody SupportMessageRequest body) {
        return SupportMessageResponse.fromEntity(
            supportService.saveAdminMessage(sessionId, body.getMessage()));
    }
}
