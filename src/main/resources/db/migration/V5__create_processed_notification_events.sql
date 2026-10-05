CREATE TABLE processed_notification_events (
    event_id BIGINT PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
