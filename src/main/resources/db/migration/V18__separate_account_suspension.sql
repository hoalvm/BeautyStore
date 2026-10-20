-- Preserve unverified registrations: activated=false alone cannot prove that
-- a legacy account was administratively suspended. Review existing inactive
-- accounts at rollout and reapply administrative suspensions where required.
ALTER TABLE `user`
    ADD COLUMN account_locked BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0;
