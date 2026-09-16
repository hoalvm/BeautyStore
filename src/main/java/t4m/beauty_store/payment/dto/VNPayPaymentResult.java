package t4m.beauty_store.payment.dto;

/** Result returned after processing an IPN inside the order transaction. */
public record VNPayPaymentResult(
        VNPayPaymentOutcome outcome,
        String paymentStatus,
        boolean inventoryCommitted) {

    /** Final guard before a successful gateway transaction is acknowledged. */
    public boolean paymentCommitted() {
        return "PAID".equals(paymentStatus)
                && inventoryCommitted;
    }
}
