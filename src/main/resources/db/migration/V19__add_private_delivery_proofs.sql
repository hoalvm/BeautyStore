CREATE TABLE delivery_proofs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    uploaded_by BIGINT NOT NULL,
    uploaded_at DATETIME(6) NOT NULL,
    image_bytes LONGBLOB NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_delivery_proof_shipper FOREIGN KEY (uploaded_by) REFERENCES `user` (id)
);

ALTER TABLE orders
    ADD COLUMN delivery_proof_id BIGINT NULL,
    ADD CONSTRAINT uk_order_delivery_proof UNIQUE (delivery_proof_id),
    ADD CONSTRAINT fk_order_delivery_proof FOREIGN KEY (delivery_proof_id) REFERENCES delivery_proofs (id);
