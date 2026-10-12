package com.keepsake.user.service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Accepts a browser's IANA time zone name only if Postgres knows it, since On This Day computes local times in
 * SQL; anything else is ignored and the user keeps their current zone (America/New_York when none).
 */
@Component
public class TimeZones {

    private static final int MAX_LENGTH = 64;

    private final JdbcTemplate jdbc;
    private final Map<String, Boolean> known = new ConcurrentHashMap<>();

    public TimeZones(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> valid(String name) {
        if (name == null || name.isBlank() || name.length() > MAX_LENGTH || !name.contains("/")) {
            return Optional.empty();
        }
        String trimmed = name.strip();
        boolean exists = known.computeIfAbsent(trimmed, candidate -> Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_timezone_names WHERE name = ?)", Boolean.class, candidate)));
        return exists ? Optional.of(trimmed) : Optional.empty();
    }
}
