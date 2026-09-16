package t4m.beauty_store.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import t4m.beauty_store.admin.dto.AccountCreateRequest;
import t4m.beauty_store.admin.dto.AccountDTO;
import t4m.beauty_store.admin.dto.AccountUpdateRequest;
import t4m.beauty_store.admin.dto.BulkActionRequest;
import t4m.beauty_store.admin.service.AdminAccountService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/accounts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAccountController {
    private final AdminAccountService accountService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllAccounts(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String roleFilter,
            @RequestParam(defaultValue = "") String statusFilter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "created") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Page<AccountDTO> accounts = accountService.getAccounts(
            search, roleFilter, statusFilter, page, size, sortBy, sortDir);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("accounts", accounts.getContent());
        response.put("currentPage", accounts.getNumber());
        response.put("totalItems", accounts.getTotalElements());
        response.put("totalPages", accounts.getTotalPages());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountDTO> getAccountById(@PathVariable Long id) {
        return ResponseEntity.ok(accountService.getAccountById(id));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createAccount(
            @Valid @RequestBody AccountCreateRequest request) {
        return ResponseEntity.ok(Map.of(
            "message", "Tạo tài khoản thành công",
            "account", accountService.createAccount(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateAccount(
            @PathVariable Long id,
            @Valid @RequestBody AccountUpdateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(Map.of(
            "message", "Cập nhật tài khoản thành công",
            "account", accountService.updateAccount(id, request, authentication.getName())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteAccount(
            @PathVariable Long id, Authentication authentication) {
        accountService.deleteAccount(id, authentication.getName());
        return ResponseEntity.ok(Map.of("message", "Xóa tài khoản thành công"));
    }

    @PostMapping("/{id}/ban")
    public ResponseEntity<Map<String, String>> banAccount(
            @PathVariable Long id, Authentication authentication) {
        accountService.banAccount(id, authentication.getName());
        return ResponseEntity.ok(Map.of("message", "Khóa tài khoản thành công"));
    }

    @PostMapping("/{id}/unban")
    public ResponseEntity<Map<String, String>> unbanAccount(@PathVariable Long id) {
        accountService.unbanAccount(id);
        return ResponseEntity.ok(Map.of("message", "Mở khóa tài khoản thành công"));
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@PathVariable Long id) {
        accountService.resetPassword(id);
        return ResponseEntity.ok(Map.of(
            "message", "Email đặt lại mật khẩu đã được gửi đến người dùng"));
    }

    @PostMapping("/bulk-action")
    public ResponseEntity<Map<String, String>> bulkAction(
            @RequestBody BulkActionRequest request, Authentication authentication) {
        accountService.bulkAction(request, authentication.getName());
        String action = request.getAction() == null ? "" : request.getAction().toLowerCase();
        String message = switch (action) {
            case "ban" -> "Khóa các tài khoản thành công";
            case "unban" -> "Mở khóa các tài khoản thành công";
            case "delete" -> "Xóa các tài khoản thành công";
            default -> "Thực hiện hành động thành công";
        };
        return ResponseEntity.ok(Map.of("message", message));
    }

    @GetMapping("/search")
    public ResponseEntity<List<AccountDTO>> searchAccounts(@RequestParam String keyword) {
        return ResponseEntity.ok(accountService.searchAccounts(keyword));
    }
}
