-- Complete the legacy history backfill before ProductVariant/Review become the
-- only public sources of truth.

-- Normalize historical default flags first. Prefer an active default whenever
-- one exists, while archived products still retain one inactive default so
-- their order history can resolve the original compatibility variant.
UPDATE product_variants variant
JOIN (
    SELECT product_id,
           COALESCE(
               MIN(CASE WHEN active = TRUE AND is_default = TRUE THEN id END),
               MIN(CASE WHEN active = TRUE THEN id END),
               MIN(CASE WHEN is_default = TRUE THEN id END),
               MIN(id)
           ) AS selected_id
    FROM product_variants
    GROUP BY product_id
) selected ON selected.product_id = variant.product_id
SET variant.is_default = CASE
    WHEN selected.selected_id IS NOT NULL AND variant.id = selected.selected_id THEN TRUE
    ELSE FALSE
END;

-- A generated nullable key lets MySQL enforce at most one active default per
-- product even if two administrator requests arrive concurrently.
ALTER TABLE product_variants
    ADD COLUMN active_default_product_id BIGINT
        GENERATED ALWAYS AS (
            CASE WHEN active = TRUE AND is_default = TRUE THEN product_id ELSE NULL END
        ) STORED,
    ADD CONSTRAINT uk_product_variants_active_default UNIQUE (active_default_product_id);

UPDATE order_items item
JOIN product_variants variant
  ON variant.product_id = item.product_id AND variant.is_default = TRUE
SET item.variant_id = COALESCE(item.variant_id, variant.id),
    item.product_sku = COALESCE(item.product_sku, variant.sku),
    item.variant_label = COALESCE(item.variant_label, variant.label),
    item.shade_name = COALESCE(item.shade_name, variant.shade_name),
    item.net_content = COALESCE(
        item.net_content,
        CASE
            WHEN variant.size_value IS NOT NULL AND variant.size_unit IS NOT NULL
            THEN CONCAT(CAST(variant.size_value AS CHAR), ' ', variant.size_unit)
            ELSE NULL
        END
    )
WHERE item.variant_id IS NULL
   OR item.product_sku IS NULL
   OR item.variant_label IS NULL
   OR item.shade_name IS NULL
   OR item.net_content IS NULL;

-- Legacy stars had no title/body/images and were already visible to shoppers,
-- so mapped rows become approved verified-purchase reviews. Choosing the first
-- matching order item preserves the old one-rating-per-order/product rule.
INSERT INTO reviews
    (product_id, user_id, order_item_id, stars, title, content, skin_type,
     verified_purchase, status, created_at, updated_at)
SELECT rating.product_id,
       rating.user_id,
       item.id,
       rating.stars,
       NULL,
       NULL,
       NULL,
       TRUE,
       'APPROVED',
       COALESCE(rating.created_at, NOW(6)),
       COALESCE(rating.created_at, NOW(6))
FROM ratings rating
JOIN order_items item
  ON item.id = (
      SELECT MIN(candidate.id)
      FROM order_items candidate
      WHERE candidate.order_id = rating.order_id
        AND candidate.product_id = rating.product_id
  )
LEFT JOIN reviews existing ON existing.order_item_id = item.id
WHERE existing.id IS NULL;

UPDATE products product
LEFT JOIN (
    SELECT product_id, AVG(stars) AS average_rating, COUNT(*) AS rating_count
    FROM reviews
    WHERE status = 'APPROVED'
    GROUP BY product_id
) summary ON summary.product_id = product.id
SET product.average_rating = COALESCE(summary.average_rating, 0),
    product.rating_count = COALESCE(summary.rating_count, 0);
