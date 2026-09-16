ALTER TABLE support_session
    ADD COLUMN guest_token_hash VARCHAR(64) NULL;

CREATE UNIQUE INDEX uk_support_session_guest_token
    ON support_session (guest_token_hash);
