-- The browser's IANA time zone, captured at signup and login; America/New_York when missing. On This Day
-- notifications go out at 09:00 local time, and on_this_day_checked_on records the local date last checked.
ALTER TABLE users
    ADD COLUMN time_zone VARCHAR(64),
    ADD COLUMN on_this_day_checked_on DATE;
