package t4m.beauty_store.payment.dto;

/** Durable outcome of applying a signed VNPay IPN under the order write lock. */
public enum VNPayPaymentOutcome {
    CONFIRMED,
    ALREADY_CONFIRMED,
    FAILURE_RECORDED,
    ALREADY_FINAL,
    ORDER_NOT_FOUND,
    INVALID_AMOUNT,
    RECONCILIATION_REQUIRED
}
