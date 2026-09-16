package t4m.beauty_store.voucher.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoucherStatsResponse {
    private Long totalVouchers;
    private Long activeVouchers;
    private Long expiredVouchers;
    private Long upcomingVouchers;
    private Long totalUsage;
}
