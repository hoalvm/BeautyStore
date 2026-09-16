-- Replace the single hero placeholder only for BeautyStore's demo catalog.
-- Admin-uploaded images and every legacy/historical product remain untouched.
UPDATE product_images image
JOIN products product ON product.id = image.product_id
JOIN categories category ON category.id = product.category_id
SET image.url = CASE category.slug
    WHEN 'cham-soc-da-mat' THEN '/images/beauty/catalog-skincare.webp'
    WHEN 'chong-nang' THEN '/images/beauty/catalog-skincare.webp'
    WHEN 'trang-diem' THEN '/images/beauty/catalog-makeup.webp'
    WHEN 'cham-soc-co-the' THEN '/images/beauty/catalog-body-hair.webp'
    WHEN 'cham-soc-toc' THEN '/images/beauty/catalog-body-hair.webp'
    WHEN 'nuoc-hoa' THEN '/images/beauty/catalog-gift-tools.webp'
    WHEN 'dung-cu-phu-kien' THEN '/images/beauty/catalog-gift-tools.webp'
    WHEN 'bo-qua-tang-minisize' THEN '/images/beauty/catalog-gift-tools.webp'
    ELSE image.url
END
WHERE image.url = '/images/beauty/hero-beautystore.png'
  AND EXISTS (
      SELECT 1
      FROM product_variants variant
      WHERE variant.product_id = product.id
        AND UPPER(variant.sku) LIKE 'BEA-DEMO-%'
  );
