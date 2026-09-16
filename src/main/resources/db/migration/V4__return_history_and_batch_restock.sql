ALTER TABLE return_items ADD COLUMN restocked BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE return_items ADD COLUMN restocked_at DATETIME(6) NULL;

CREATE TABLE return_status_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    return_request_id BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL,
    from_status VARCHAR(20) NULL,
    to_status VARCHAR(20) NULL,
    note TEXT NULL,
    changed_by VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_return_history_request
        FOREIGN KEY (return_request_id) REFERENCES return_requests (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_return_history_request_created
    ON return_status_history (return_request_id, created_at);

INSERT INTO return_status_history (
    return_request_id, action, from_status, to_status, note, changed_by, created_at
)
SELECT id, 'MIGRATED', NULL, status, 'Backfilled by Flyway V4', 'SYSTEM', created_at
FROM return_requests;

CREATE TABLE return_item_batch_restocks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    return_item_id BIGINT NOT NULL,
    inventory_batch_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_return_item_batch_restock UNIQUE (return_item_id, inventory_batch_id),
    CONSTRAINT fk_return_batch_restock_item
        FOREIGN KEY (return_item_id) REFERENCES return_items (id),
    CONSTRAINT fk_return_batch_restock_batch
        FOREIGN KEY (inventory_batch_id) REFERENCES inventory_batches (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_return_restock_batch
    ON return_item_batch_restocks (inventory_batch_id);
