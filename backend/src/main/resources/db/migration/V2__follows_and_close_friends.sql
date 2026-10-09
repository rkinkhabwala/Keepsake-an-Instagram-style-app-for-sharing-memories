CREATE TABLE follows (
    follower_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    followee_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (follower_id, followee_id),
    CONSTRAINT ck_follows_status CHECK (status IN ('PENDING', 'ACCEPTED')),
    CONSTRAINT ck_follows_not_self CHECK (follower_id <> followee_id)
);

CREATE INDEX ix_follows_followee_status ON follows (followee_id, status);
CREATE INDEX ix_follows_follower_status ON follows (follower_id, status);

CREATE TABLE close_friends (
    owner_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    friend_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (owner_id, friend_id),
    CONSTRAINT ck_close_friends_not_self CHECK (owner_id <> friend_id)
);
