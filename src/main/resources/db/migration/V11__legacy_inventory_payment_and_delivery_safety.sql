-- Normalize the old UI alias so all historical VNPay safeguards apply.
UPDATE orders
SET payment_method = 'VNPAY'
WHERE UPPER(payment_method) = 'E_WALLET';

-- Historical cancelled/refunded orders had already returned their product-level
-- stock before the batch model existed and therefore must not look committed.
UPDATE orders
SET inventory_committed = FALSE
WHERE status IN ('CANCELLED', 'REFUNDED');

-- V2 copied current legacy stock into one compatibility batch, while V9 linked
-- historical order items to their compatibility variants. Record the exact
-- committed batch allocation so a later legitimate cancellation/return can
-- restore the same batch instead of failing with an empty allocation set.
INSERT INTO order_item_batch_allocations
    (order_item_id, inventory_batch_id, quantity, status)
SELECT item.id,
       (
           SELECT batch.id
           FROM inventory_batches batch
           WHERE batch.variant_id = item.variant_id
           ORDER BY
               CASE WHEN batch.batch_code = CONCAT('LEGACY-', item.product_id) THEN 0 ELSE 1 END,
               batch.expiry_date,
               batch.id
           LIMIT 1
       ),
       item.quantity,
       'COMMITTED'
FROM order_items item
JOIN orders legacy_order ON legacy_order.id = item.order_id
WHERE legacy_order.inventory_committed = TRUE
  AND item.variant_id IS NOT NULL
  AND item.quantity > 0
  AND NOT EXISTS (
      SELECT 1
      FROM order_item_batch_allocations existing
      WHERE existing.order_item_id = item.id
  )
  AND EXISTS (
      SELECT 1
      FROM inventory_batches batch
      WHERE batch.variant_id = item.variant_id
  );

-- Pair pre-Flyway voucher usages with their historical orders in sequence.
-- Current checkout rows already have order_id and are intentionally excluded.
CREATE TEMPORARY TABLE legacy_voucher_usage_ranked AS
SELECT usage_row.id AS usage_id,
       usage_row.user_id,
       usage_row.voucher_id,
       ROW_NUMBER() OVER (
           PARTITION BY usage_row.user_id, usage_row.voucher_id
           ORDER BY usage_row.used_at, usage_row.id
       ) AS sequence_number
FROM voucher_usage usage_row
WHERE usage_row.order_id IS NULL
  AND usage_row.user_id IS NOT NULL;

CREATE TEMPORARY TABLE legacy_voucher_order_ranked AS
SELECT order_row.id AS order_id,
       order_row.user_id,
       voucher_row.id AS voucher_id,
       ROW_NUMBER() OVER (
           PARTITION BY order_row.user_id, voucher_row.id
           ORDER BY order_row.created_at, order_row.id
       ) AS sequence_number
FROM orders order_row
JOIN voucher voucher_row
  ON UPPER(voucher_row.code) = UPPER(order_row.voucher_code)
WHERE order_row.user_id IS NOT NULL
  AND order_row.voucher_code IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM voucher_usage linked WHERE linked.order_id = order_row.id
  );

UPDATE voucher_usage usage_row
JOIN legacy_voucher_usage_ranked ranked_usage
  ON ranked_usage.usage_id = usage_row.id
JOIN legacy_voucher_order_ranked ranked_order
  ON ranked_order.user_id = ranked_usage.user_id
 AND ranked_order.voucher_id = ranked_usage.voucher_id
 AND ranked_order.sequence_number = ranked_usage.sequence_number
SET usage_row.order_id = ranked_order.order_id;

DROP TEMPORARY TABLE legacy_voucher_order_ranked;
DROP TEMPORARY TABLE legacy_voucher_usage_ranked;

ALTER TABLE orders
    ADD COLUMN delivery_failure_reason VARCHAR(500) NULL,
    ADD COLUMN checkout_identity_hash VARCHAR(64) NULL;

CREATE INDEX idx_orders_checkout_identity_status
    ON orders (checkout_identity_hash, payment_method, status);
