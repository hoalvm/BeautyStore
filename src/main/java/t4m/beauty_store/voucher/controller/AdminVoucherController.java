package t4m.beauty_store.voucher.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import t4m.beauty_store.voucher.dto.VoucherRequest;
import t4m.beauty_store.voucher.dto.VoucherResponse;
import t4m.beauty_store.voucher.dto.VoucherStatsResponse;
import t4m.beauty_store.voucher.service.AdminVoucherService;
import t4m.beauty_store.config.PageResponse;

@RestController
@RequestMapping("/api/admin/vouchers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminVoucherController {
    
    private final AdminVoucherService adminVoucherService;

    @PostMapping
    public ResponseEntity<VoucherResponse> createVoucher(@Valid @RequestBody VoucherRequest request) {
        return ResponseEntity.ok(adminVoucherService.createVoucher(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<VoucherResponse> updateVoucher(
            @PathVariable Long id,
            @Valid @RequestBody VoucherRequest request) {
        return ResponseEntity.ok(adminVoucherService.updateVoucher(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVoucher(@PathVariable Long id) {
        adminVoucherService.deleteVoucher(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<PageResponse<VoucherResponse>> getAllVouchers(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String discountType,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Page<VoucherResponse> vouchers = adminVoucherService.getAllVouchers(
            code, discountType, active, status, page, size, sortBy, sortDir
        );
        return ResponseEntity.ok(PageResponse.from(vouchers));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VoucherResponse> getVoucherById(@PathVariable Long id) {
        return ResponseEntity.ok(adminVoucherService.getVoucherById(id));
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<VoucherResponse> toggleVoucherStatus(@PathVariable Long id) {
        return ResponseEntity.ok(adminVoucherService.toggleVoucherStatus(id));
    }

    @GetMapping("/stats")
    public ResponseEntity<VoucherStatsResponse> getStatistics() {
        VoucherStatsResponse stats = adminVoucherService.getStatistics();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/generate-code")
    public ResponseEntity<String> generateCode(@RequestParam(defaultValue = "8") int length) {
        String code = adminVoucherService.generateRandomCode(length);
        return ResponseEntity.ok(code);
    }
}
