CREATE INDEX idx_support_message_session_created
    ON support_message (session_id, created_at, id);

CREATE INDEX idx_support_message_unread_sender
    ON support_message (session_id, is_read, sender_type);
