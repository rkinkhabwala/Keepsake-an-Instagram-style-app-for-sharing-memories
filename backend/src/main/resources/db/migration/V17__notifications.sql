-- Spec §5 notifications (Phase 3). Likes on a memory are grouped into one unread notification whose likers are
-- listed in notification_actors; other types carry the id of the event that produced them, so a redelivered
-- event cannot notify twice.
CREATE TABLE notifications (
    id              UUID PRIMARY KEY,
    recipient_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    actor_id        UUID REFERENCES users (id) ON DELETE CASCADE,
    type            VARCHAR(20) NOT NULL
        CHECK (type IN ('LIKE', 'COMMENT', 'FOLLOW', 'FOLLOW_REQUEST', 'TAGGED', 'ON_THIS_DAY')),
    memory_id       UUID REFERENCES memories (id) ON DELETE CASCADE,
    comment_id      UUID REFERENCES comments (id) ON DELETE CASCADE,
    actor_count     INTEGER     NOT NULL DEFAULT 1,
    source_event_id UUID,
    read_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_notifications_recipient ON notifications (recipient_id, updated_at DESC, id DESC);
CREATE INDEX ix_notifications_unread ON notifications (recipient_id) WHERE read_at IS NULL;
CREATE UNIQUE INDEX ux_notifications_unread_like ON notifications (recipient_id, memory_id)
    WHERE type = 'LIKE' AND read_at IS NULL;
CREATE UNIQUE INDEX ux_notifications_source_event ON notifications (recipient_id, source_event_id);

CREATE TABLE notification_actors (
    notification_id UUID NOT NULL REFERENCES notifications (id) ON DELETE CASCADE,
    actor_id        UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (notification_id, actor_id)
);
CREATE INDEX ix_notification_actors_actor ON notification_actors (actor_id);
