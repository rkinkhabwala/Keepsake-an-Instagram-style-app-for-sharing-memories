package com.keepsake.notification.service;

import com.keepsake.memory.entity.Memory;
import com.keepsake.resurface.repository.OnThisDayRepository;
import com.keepsake.resurface.service.OnThisDay;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends each user an On This Day notification once a day, from 09:00 in their own time zone (America/New_York
 * when unknown), when they have memories from this day in earlier years. Each user's local date is recorded when
 * checked, so a run that is late or repeated sends nothing twice.
 */
@Component
public class OnThisDayNotifier {

    public static final String DEFAULT_TIME_ZONE = "America/New_York";
    private static final int BATCH = 500;

    private final JdbcTemplate jdbc;
    private final OnThisDayRepository onThisDay;
    private final NotificationService notifications;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public OnThisDayNotifier(
            JdbcTemplate jdbc,
            OnThisDayRepository onThisDay,
            NotificationService notifications,
            TransactionTemplate transactions,
            Clock clock
    ) {
        this.jdbc = jdbc;
        this.onThisDay = onThisDay;
        this.notifications = notifications;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${keepsake.notifications.on-this-day-interval:PT5M}")
    public void run() {
        sendDue(clock.instant());
    }

    /** @return how many users were checked */
    public int sendDue(Instant now) {
        int checked = 0;
        List<Map<String, Object>> due;
        do {
            due = jdbc.queryForList("""
                    SELECT id, COALESCE(time_zone, ?) AS zone FROM users
                    WHERE (CAST(? AS timestamptz) AT TIME ZONE COALESCE(time_zone, ?))::time >= TIME '09:00'
                      AND (on_this_day_checked_on IS NULL OR on_this_day_checked_on
                           < (CAST(? AS timestamptz) AT TIME ZONE COALESCE(time_zone, ?))::date)
                    LIMIT ?
                    """, DEFAULT_TIME_ZONE, Timestamp.from(now), DEFAULT_TIME_ZONE, Timestamp.from(now),
                    DEFAULT_TIME_ZONE, BATCH);
            for (Map<String, Object> user : due) {
                UUID userId = (UUID) user.get("id");
                LocalDate today = now.atZone(ZoneId.of((String) user.get("zone"))).toLocalDate();
                transactions.executeWithoutResult(status -> check(userId, today));
                checked++;
            }
        } while (due.size() == BATCH);
        return checked;
    }

    private void check(UUID userId, LocalDate today) {
        List<MonthDay> days = OnThisDay.daysShownOn(today);
        List<Memory> memories = onThisDay.findOnDays(userId, today.getYear(), days.getFirst().getMonthValue(),
                days.getFirst().getDayOfMonth(), days.getLast().getMonthValue(), days.getLast().getDayOfMonth(), 1);
        if (!memories.isEmpty()) {
            notifications.recordOnThisDay(userId, memories.getFirst().getId());
        }
        jdbc.update("UPDATE users SET on_this_day_checked_on = ? WHERE id = ?", today, userId);
    }
}
