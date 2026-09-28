package t4m.beauty_store.returns.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import t4m.beauty_store.returns.dto.ReturnResponse;
import t4m.beauty_store.returns.entity.ReturnStatus;
import t4m.beauty_store.returns.service.ReturnService;
import t4m.beauty_store.config.PageResponse;

import java.util.Set;

@RestController
@RequestMapping("/api/admin/returns")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminReturnController {
    private final ReturnService returnService;

    @GetMapping
    public ResponseEntity<PageResponse<ReturnResponse>> list(
            @RequestParam(required = false) ReturnStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.from(returnService.adminList(status,
            PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100))))));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ReturnResponse> transition(
            @PathVariable Long id,
            @RequestBody Transition body,
            Authentication authentication) {
        return ResponseEntity.ok(returnService.transition(
            id, body.getStatus(), body.getAdminNote(), body.getRefundReference(), authentication.getName()));
    }

    @PostMapping("/{id}/restock")
    public ResponseEntity<ReturnResponse> restock(
            @PathVariable Long id,
            @RequestBody Restock body,
            Authentication authentication) {
        return ResponseEntity.ok(returnService.restock(id, body.getReturnItemIds(), authentication.getName()));
    }

    @Data
    public static class Transition {
        private ReturnStatus status;
        private String adminNote;
        private String refundReference;
    }

    @Data
    public static class Restock {
        private Set<Long> returnItemIds;
    }
}
