CREATE INDEX idx_products_active_id ON products (active, id);
CREATE INDEX idx_variants_product_active ON product_variants (product_id, active);
CREATE INDEX idx_batches_variant_sellable
    ON inventory_batches (variant_id, active, expiry_date);
