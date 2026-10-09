-- When a refresh token is rotated, remember its successor (and the encrypted session issued with it) so that
-- presenting the old token again within a short grace window returns the same session instead of being treated
-- as token theft. See AuthService.REUSE_GRACE.
ALTER TABLE refresh_tokens ADD COLUMN successor_id UUID;
ALTER TABLE refresh_tokens ADD COLUMN successor_session TEXT;
