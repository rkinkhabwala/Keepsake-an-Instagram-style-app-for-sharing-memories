package com.keepsake.resurface.service;

import java.time.LocalDate;
import java.time.Month;
import java.time.MonthDay;
import java.util.List;

/**
 * Which calendar days resurface on a given date. A memory resurfaces on the same month and day in a later year.
 * Feb 29 memories also resurface on Feb 28 in years without a Feb 29; in leap years they wait for Feb 29.
 */
public final class OnThisDay {

    private static final MonthDay LEAP_DAY = MonthDay.of(Month.FEBRUARY, 29);
    private static final MonthDay EVE_OF_LEAP_DAY = MonthDay.of(Month.FEBRUARY, 28);

    private OnThisDay() {
    }

    /** The memory days to show on {@code today}: its own month-day, plus Feb 29 on Feb 28 of a non-leap year. */
    public static List<MonthDay> daysShownOn(LocalDate today) {
        MonthDay day = MonthDay.from(today);
        if (day.equals(EVE_OF_LEAP_DAY) && !today.isLeapYear()) {
            return List.of(day, LEAP_DAY);
        }
        return List.of(day);
    }

    public static boolean resurfacesOn(LocalDate occurredOn, LocalDate today) {
        return occurredOn.getYear() < today.getYear() && daysShownOn(today).contains(MonthDay.from(occurredOn));
    }
}
