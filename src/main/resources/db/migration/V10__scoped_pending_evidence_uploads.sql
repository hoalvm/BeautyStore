CREATE TABLE pending_evidence_uploads (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    evidence_kind VARCHAR(20) NOT NULL,
    order_id BIGINT NOT NULL,
    order_item_id BIGINT NOT NULL,
    url VARCHAR(1000) NOT NULL,
    cloudinary_public_id VARCHAR(255) NOT NULL,
    claimed_at DATETIME(6),
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_pending_evidence_public_id UNIQUE (cloudinary_public_id),
    CONSTRAINT fk_pending_evidence_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_pending_evidence_order_item FOREIGN KEY (order_item_id) REFERENCES order_items (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_pending_evidence_scope
    ON pending_evidence_uploads (evidence_kind, order_item_id, expires_at);
CREATE INDEX idx_pending_evidence_cleanup
    ON pending_evidence_uploads (claimed_at, expires_at);
