-- ProductVariant, ProductImage and InventoryBatch have been fully backfilled
-- since V2. Remove the duplicated Product-level commerce projection so there
-- is only one source of truth for SKU, price, media and available inventory.

-- Preserve the brand text of archived legacy catalog rows. Newly discovered
-- legacy brands stay hidden, while an already normalized brand keeps its
-- existing status and metadata.
INSERT IGNORE INTO brands (name, slug, description, country, active)
SELECT DISTINCT TRIM(p.brand),
       CONCAT('legacy-brand-', LOWER(SHA2(TRIM(p.brand), 256))),
       'Thương hiệu được bảo toàn từ catalog cũ', NULL, FALSE
FROM products p
WHERE p.brand IS NOT NULL AND TRIM(p.brand) <> '';

UPDATE products p
JOIN brands b
  ON CAST(LOWER(b.name) AS BINARY) = CAST(LOWER(TRIM(p.brand)) AS BINARY)
SET p.brand_id = b.id
WHERE p.brand_id IS NULL
  AND p.brand IS NOT NULL
  AND TRIM(p.brand) <> '';

UPDATE product_images pi
JOIN products p ON p.id = pi.product_id
SET pi.cloudinary_public_id = p.cloudinary_public_id
WHERE pi.cloudinary_public_id IS NULL
  AND p.cloudinary_public_id IS NOT NULL
  AND CAST(pi.url AS BINARY) = CAST(p.image_url AS BINARY);

ALTER TABLE products
    DROP INDEX uk_products_sku,
    DROP COLUMN sku,
    DROP COLUMN brand,
    DROP COLUMN unit,
    DROP COLUMN price,
    DROP COLUMN discount_price,
    DROP COLUMN image_url,
    DROP COLUMN cloudinary_public_id,
    DROP COLUMN stock;
