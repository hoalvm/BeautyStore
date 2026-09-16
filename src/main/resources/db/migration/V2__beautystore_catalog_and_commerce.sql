-- Additive BeautyStore schema. The old Product columns remain during the
-- compatibility window; application code treats ProductVariant/InventoryBatch
-- as the source of truth for new catalog entries.

ALTER TABLE categories ADD COLUMN slug VARCHAR(180) NULL;
ALTER TABLE categories ADD COLUMN parent_id BIGINT NULL;
ALTER TABLE categories ADD COLUMN display_order INT NOT NULL DEFAULT 0;
UPDATE categories SET slug = CONCAT('legacy-category-', id) WHERE slug IS NULL;
ALTER TABLE categories ADD CONSTRAINT uk_categories_slug UNIQUE (slug);
ALTER TABLE categories ADD CONSTRAINT fk_categories_parent
    FOREIGN KEY (parent_id) REFERENCES categories (id);

CREATE TABLE brands (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    slug VARCHAR(180) NOT NULL,
    description TEXT,
    logo_url VARCHAR(1000),
    country VARCHAR(120),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_brands_name UNIQUE (name),
    CONSTRAINT uk_brands_slug UNIQUE (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE products ADD COLUMN slug VARCHAR(220) NULL;
ALTER TABLE products ADD COLUMN brand_id BIGINT NULL;
ALTER TABLE products ADD COLUMN benefits TEXT NULL;
ALTER TABLE products ADD COLUMN inci TEXT NULL;
ALTER TABLE products ADD COLUMN directions TEXT NULL;
ALTER TABLE products ADD COLUMN warnings TEXT NULL;
ALTER TABLE products ADD COLUMN spf VARCHAR(30) NULL;
ALTER TABLE products ADD COLUMN pao_months INT NULL;
ALTER TABLE products ADD COLUMN shelf_life_months INT NULL;
UPDATE products SET slug = CONCAT('legacy-product-', id) WHERE slug IS NULL;
ALTER TABLE products ADD CONSTRAINT uk_products_slug UNIQUE (slug);
ALTER TABLE products ADD CONSTRAINT fk_products_brand FOREIGN KEY (brand_id) REFERENCES brands (id);

CREATE TABLE product_facets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    facet_type VARCHAR(40) NOT NULL,
    code VARCHAR(100) NOT NULL,
    label VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_product_facets_type_code UNIQUE (facet_type, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_facet_assignments (
    product_id BIGINT NOT NULL,
    facet_id BIGINT NOT NULL,
    PRIMARY KEY (product_id, facet_id),
    CONSTRAINT fk_product_facet_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_product_facet_facet FOREIGN KEY (facet_id) REFERENCES product_facets (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_variants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    sku VARCHAR(100) NOT NULL,
    barcode VARCHAR(100),
    label VARCHAR(160),
    shade_name VARCHAR(120),
    shade_hex VARCHAR(7),
    size_value DECIMAL(12,3),
    size_unit VARCHAR(30),
    price DECIMAL(15,2) NOT NULL,
    discount_price DECIMAL(15,2),
    low_stock_threshold INT NOT NULL DEFAULT 10,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_product_variants_sku UNIQUE (sku),
    CONSTRAINT uk_product_variants_barcode UNIQUE (barcode),
    CONSTRAINT fk_product_variants_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inventory_batches (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    variant_id BIGINT NOT NULL,
    batch_code VARCHAR(100) NOT NULL,
    manufactured_date DATE,
    expiry_date DATE NOT NULL,
    quantity_on_hand INT NOT NULL DEFAULT 0,
    quantity_reserved INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT,
    CONSTRAINT uk_inventory_batches_variant_code UNIQUE (variant_id, batch_code),
    CONSTRAINT fk_inventory_batches_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_inventory_batches_fefo
    ON inventory_batches (variant_id, active, expiry_date);

CREATE TABLE product_images (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    variant_id BIGINT,
    url VARCHAR(1000) NOT NULL,
    cloudinary_public_id VARCHAR(500),
    alt_text VARCHAR(300) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_product_images_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Every legacy product receives a compatibility variant and batch. Archived
-- education products remain queryable from historical orders, but are hidden
-- from the public BeautyStore catalog in V3.
INSERT INTO product_variants
    (product_id, sku, label, price, discount_price, low_stock_threshold,
     is_default, active, created_at, updated_at)
SELECT p.id, COALESCE(NULLIF(UPPER(TRIM(p.sku)), ''), CONCAT('LEGACY-', p.id)),
       NULLIF(TRIM(p.unit), ''), p.price, p.discount_price, 10,
       TRUE, p.active, COALESCE(p.created_at, NOW(6)), COALESCE(p.updated_at, NOW(6))
FROM products p;

INSERT INTO inventory_batches
    (variant_id, batch_code, expiry_date, quantity_on_hand, quantity_reserved, active, version)
SELECT v.id, CONCAT('LEGACY-', v.product_id), '2099-12-31', GREATEST(COALESCE(p.stock, 0), 0), 0,
       v.active, 0
FROM product_variants v JOIN products p ON p.id = v.product_id;

INSERT INTO product_images
    (product_id, variant_id, url, alt_text, sort_order, is_primary)
SELECT p.id, NULL, p.image_url, p.name, 0, TRUE
FROM products p WHERE p.image_url IS NOT NULL AND TRIM(p.image_url) <> '';

ALTER TABLE cart MODIFY COLUMN user_id BIGINT NULL;
ALTER TABLE cart ADD COLUMN guest_token_hash VARCHAR(64) NULL;
ALTER TABLE cart ADD CONSTRAINT uk_cart_guest_token_hash UNIQUE (guest_token_hash);
ALTER TABLE cart_item ADD COLUMN variant_id BIGINT NULL;
UPDATE cart_item
SET variant_id = (
    SELECT v.id FROM product_variants v
    WHERE v.product_id = cart_item.product_id AND v.is_default = TRUE
);
ALTER TABLE cart_item ADD CONSTRAINT fk_cart_item_variant
    FOREIGN KEY (variant_id) REFERENCES product_variants (id);
CREATE UNIQUE INDEX uk_cart_item_cart_variant ON cart_item (cart_id, variant_id);

ALTER TABLE orders MODIFY COLUMN user_id BIGINT NULL;
ALTER TABLE orders ADD COLUMN shipping_address_line VARCHAR(255) NULL;
ALTER TABLE orders ADD COLUMN shipping_ward VARCHAR(255) NULL;
ALTER TABLE orders ADD COLUMN shipping_district VARCHAR(255) NULL;
ALTER TABLE orders ADD COLUMN shipping_province VARCHAR(255) NULL;
ALTER TABLE orders ADD COLUMN subtotal DECIMAL(38,2) NULL;
ALTER TABLE orders ADD COLUMN product_discount DECIMAL(38,2) NULL;
ALTER TABLE orders ADD COLUMN shipping_fee DECIMAL(38,2) NULL;
ALTER TABLE orders ADD COLUMN reservation_expires_at DATETIME(6) NULL;
ALTER TABLE orders ADD COLUMN inventory_committed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE orders ADD COLUMN delivered_at DATETIME(6) NULL;
UPDATE orders SET subtotal = total_amount, product_discount = 0, shipping_fee = 0,
    inventory_committed = TRUE WHERE subtotal IS NULL;

ALTER TABLE order_items ADD COLUMN variant_id BIGINT NULL;
ALTER TABLE order_items ADD COLUMN product_sku VARCHAR(100) NULL;
ALTER TABLE order_items ADD COLUMN variant_label VARCHAR(160) NULL;
ALTER TABLE order_items ADD COLUMN shade_name VARCHAR(120) NULL;
ALTER TABLE order_items ADD COLUMN net_content VARCHAR(100) NULL;
UPDATE order_items
SET product_sku = (
    SELECT p.sku FROM products p WHERE p.id = order_items.product_id
)
WHERE product_sku IS NULL;
ALTER TABLE order_items ADD CONSTRAINT fk_order_items_variant
    FOREIGN KEY (variant_id) REFERENCES product_variants (id);

CREATE TABLE order_item_batch_allocations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_item_id BIGINT NOT NULL,
    inventory_batch_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT fk_allocations_order_item FOREIGN KEY (order_item_id) REFERENCES order_items (id),
    CONSTRAINT fk_allocations_batch FOREIGN KEY (inventory_batch_id) REFERENCES inventory_batches (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE guest_order_access (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    otp_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    resend_available_at DATETIME(6) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    verified_at DATETIME(6),
    access_token_hash VARCHAR(64),
    access_token_expires_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_guest_order_access_order FOREIGN KEY (order_id) REFERENCES orders (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_guest_order_access_order ON guest_order_access (order_id);

CREATE TABLE reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    user_id BIGINT,
    order_item_id BIGINT NOT NULL,
    stars INT NOT NULL,
    title VARCHAR(120),
    content TEXT,
    skin_type VARCHAR(50),
    verified_purchase BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_reviews_order_item UNIQUE (order_item_id),
    CONSTRAINT fk_reviews_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_reviews_order_item FOREIGN KEY (order_item_id) REFERENCES order_items (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE review_images (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    review_id BIGINT NOT NULL,
    url VARCHAR(1000) NOT NULL,
    cloudinary_public_id VARCHAR(255),
    display_order INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_review_images_review FOREIGN KEY (review_id) REFERENCES reviews (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE return_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    user_id BIGINT,
    customer_email VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    admin_note TEXT,
    refund_reference VARCHAR(255),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_return_requests_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_return_requests_user FOREIGN KEY (user_id) REFERENCES `user` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE return_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    return_request_id BIGINT NOT NULL,
    order_item_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    reason VARCHAR(40) NOT NULL,
    unopened_and_sealed BOOLEAN NOT NULL,
    details TEXT,
    CONSTRAINT uk_return_item_request_order_item UNIQUE (return_request_id, order_item_id),
    CONSTRAINT fk_return_items_request FOREIGN KEY (return_request_id) REFERENCES return_requests (id),
    CONSTRAINT fk_return_items_order_item FOREIGN KEY (order_item_id) REFERENCES order_items (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE return_item_images (
    return_item_id BIGINT NOT NULL,
    image_url VARCHAR(1000) NOT NULL,
    CONSTRAINT fk_return_item_images_item FOREIGN KEY (return_item_id) REFERENCES return_items (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE voucher_usage MODIFY COLUMN user_id BIGINT NULL;
ALTER TABLE voucher_usage ADD COLUMN guest_identifier_hash VARCHAR(64) NULL;

CREATE TABLE voucher_brands (
    voucher_id BIGINT NOT NULL,
    brand_id BIGINT NOT NULL,
    PRIMARY KEY (voucher_id, brand_id),
    CONSTRAINT fk_voucher_brands_voucher FOREIGN KEY (voucher_id) REFERENCES voucher (id),
    CONSTRAINT fk_voucher_brands_brand FOREIGN KEY (brand_id) REFERENCES brands (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE voucher_categories (
    voucher_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    PRIMARY KEY (voucher_id, category_id),
    CONSTRAINT fk_voucher_categories_voucher FOREIGN KEY (voucher_id) REFERENCES voucher (id),
    CONSTRAINT fk_voucher_categories_category FOREIGN KEY (category_id) REFERENCES categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE voucher_products (
    voucher_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    PRIMARY KEY (voucher_id, product_id),
    CONSTRAINT fk_voucher_products_voucher FOREIGN KEY (voucher_id) REFERENCES voucher (id),
    CONSTRAINT fk_voucher_products_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE voucher_variants (
    voucher_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    PRIMARY KEY (voucher_id, variant_id),
    CONSTRAINT fk_voucher_variants_voucher FOREIGN KEY (voucher_id) REFERENCES voucher (id),
    CONSTRAINT fk_voucher_variants_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
