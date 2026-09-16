-- Normalize the legacy comma/JSON-like ID strings before removing them. A
-- malformed or fully archived legacy scope is disabled rather than silently
-- becoming a global voucher.

INSERT IGNORE INTO voucher_categories (voucher_id, category_id)
SELECT v.id, c.id
FROM voucher v
JOIN categories c ON FIND_IN_SET(
    CAST(CAST(c.id AS CHAR) AS BINARY),
    CAST(REPLACE(REPLACE(REPLACE(REPLACE(COALESCE(v.applicable_categories, ''), '[', ''), ']', ''), '"', ''), ' ', '') AS BINARY)
) > 0
WHERE v.applicable_categories IS NOT NULL
  AND TRIM(v.applicable_categories) <> '';

INSERT IGNORE INTO voucher_products (voucher_id, product_id)
SELECT v.id, p.id
FROM voucher v
JOIN products p ON FIND_IN_SET(
    CAST(CAST(p.id AS CHAR) AS BINARY),
    CAST(REPLACE(REPLACE(REPLACE(REPLACE(COALESCE(v.applicable_products, ''), '[', ''), ']', ''), '"', ''), ' ', '') AS BINARY)
) > 0
WHERE v.applicable_products IS NOT NULL
  AND TRIM(v.applicable_products) <> '';

UPDATE voucher v
SET v.active = FALSE
WHERE (
        (v.applicable_categories IS NOT NULL AND TRIM(v.applicable_categories) <> '')
        OR (v.applicable_products IS NOT NULL AND TRIM(v.applicable_products) <> '')
      )
  AND NOT EXISTS (
      SELECT 1 FROM voucher_categories vc
      JOIN categories c ON c.id = vc.category_id
      WHERE vc.voucher_id = v.id AND c.active = TRUE
  )
  AND NOT EXISTS (
      SELECT 1 FROM voucher_products vp
      JOIN products p ON p.id = vp.product_id
      WHERE vp.voucher_id = v.id AND p.active = TRUE
  );

ALTER TABLE voucher
    DROP COLUMN applicable_categories,
    DROP COLUMN applicable_products;
