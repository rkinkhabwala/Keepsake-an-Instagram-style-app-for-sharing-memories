-- Transactional outbox: events are written in the same transaction as the change they describe, then a relay
-- publishes them to Kafka. A crash between commit and publish can delay an event but never lose it.
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    topic VARCHAR(100) NOT NULL,
    event_key VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(500)
);

CREATE INDEX ix_outbox_events_pending ON outbox_events (created_at) WHERE published_at IS NULL;
