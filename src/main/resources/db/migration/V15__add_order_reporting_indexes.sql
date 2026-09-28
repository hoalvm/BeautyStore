CREATE INDEX idx_orders_status_created
    ON orders (status, created_at);

CREATE INDEX idx_orders_payment_reservation
    ON orders (status, payment_status, inventory_committed, reservation_expires_at);
