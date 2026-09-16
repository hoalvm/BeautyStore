-- Preserve every historical row while removing ToyStore/EduStore merchandise
-- from the BeautyStore storefront. Matching category names is deliberately
-- exact so categories created by an administrator are never archived by a
-- broad keyword search.

CREATE TEMPORARY TABLE legacy_categories_to_archive (
    id BIGINT PRIMARY KEY
);

INSERT INTO legacy_categories_to_archive (id)
SELECT id FROM categories WHERE name IN (
    'Xe & Phi thuyền',
    'Robot & Công nghệ',
    'Búp bê & Nhà búp bê',
    'Đồ chơi giáo dục',
    'Đồ chơi ngoài trời',
    'Mô hình & Sưu tập',
    'Đồ chơi sáng tạo',
    'Đồ chơi cho bé',
    'Bút viết & dụng cụ cơ bản',
    'Vở giấy & sổ tay',
    'Balo hộp bút & phụ kiện',
    'Mỹ thuật & sáng tạo',
    'STEM & thí nghiệm',
    'Máy tính & thiết bị học tập',
    'Sách tham khảo & flashcard',
    'Combo theo lớp'
);

UPDATE products p
LEFT JOIN legacy_categories_to_archive legacy_category ON legacy_category.id = p.category_id
SET p.active = FALSE
WHERE UPPER(COALESCE(p.sku, '')) LIKE 'EDU-%'
   OR legacy_category.id IS NOT NULL;

UPDATE categories
SET active = FALSE
WHERE id IN (SELECT id FROM legacy_categories_to_archive);

UPDATE product_variants
SET active = FALSE
WHERE product_id IN (SELECT id FROM products WHERE active = FALSE);

UPDATE inventory_batches
SET active = FALSE
WHERE variant_id IN (SELECT id FROM product_variants WHERE active = FALSE);

-- A hidden legacy item is no longer purchasable, but favourites and order
-- snapshots remain intact for account and audit history.
DELETE FROM cart_item
WHERE product_id IN (SELECT id FROM products WHERE active = FALSE);

UPDATE voucher
SET active = FALSE
WHERE UPPER(code) LIKE 'EDU-%'
   OR LOWER(COALESCE(description, '')) LIKE '%edustore%'
   OR LOWER(COALESCE(description, '')) LIKE '%toy store%';

DROP TEMPORARY TABLE legacy_categories_to_archive;
