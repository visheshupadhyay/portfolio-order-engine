ALTER TABLE outbox_events
ADD COLUMN claim_token VARCHAR(36);

CREATE INDEX idx_outbox_events_status_claimed_at
ON outbox_events (status, claimed_at);
