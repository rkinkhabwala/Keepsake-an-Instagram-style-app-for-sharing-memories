-- Prefix search (LIKE 'abc%') on lowercased values; text_pattern_ops lets B-tree indexes serve LIKE prefixes
-- whatever the database collation.
CREATE INDEX ix_users_username_prefix ON users (lower(username) text_pattern_ops);
CREATE INDEX ix_users_display_name_prefix ON users (lower(display_name) text_pattern_ops);
CREATE INDEX ix_hashtags_tag_prefix ON hashtags (tag text_pattern_ops);
