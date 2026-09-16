-- Member and guest voucher limits share the same normalized email identity.
-- Preserve existing guest hashes and backfill historical member redemptions.
UPDATE voucher_usage usage_row
JOIN `user` account ON account.id = usage_row.user_id
SET usage_row.guest_identifier_hash = LOWER(SHA2(LOWER(TRIM(account.email)), 256))
WHERE usage_row.user_id IS NOT NULL
  AND (usage_row.guest_identifier_hash IS NULL
       OR usage_row.guest_identifier_hash NOT REGEXP '^[0-9A-Fa-f]{64}$');

CREATE INDEX idx_voucher_usage_voucher_identity
    ON voucher_usage (voucher_id, guest_identifier_hash);
