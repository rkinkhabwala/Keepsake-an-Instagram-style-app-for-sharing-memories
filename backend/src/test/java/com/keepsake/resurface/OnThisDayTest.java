package com.keepsake.resurface;

import static org.assertj.core.api.Assertions.assertThat;

import com.keepsake.resurface.service.OnThisDay;
import java.time.LocalDate;
import java.time.MonthDay;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OnThisDayTest {

    private static final LocalDate LEAP_DAY_2020 = LocalDate.of(2020, 2, 29);

    @ParameterizedTest(name = "Feb 29, 2020 memory on {0} -> {1}")
    @CsvSource({
            "2021-02-28, true",
            "2022-02-28, true",
            "2023-02-28, true",
            "2024-02-28, false",
            "2024-02-29, true",
            "2025-02-28, true",
            "2021-03-01, false",
            "2100-02-28, true",
            "2020-02-29, false"
    })
    void leapDayMemoriesResurfaceOnFeb28OnlyInYearsWithoutAFeb29(LocalDate today, boolean expected) {
        assertThat(OnThisDay.resurfacesOn(LEAP_DAY_2020, today)).isEqualTo(expected);
    }

    @Test
    void ordinaryDatesNeedTheSameMonthAndDayInAnEarlierYear() {
        LocalDate memory = LocalDate.of(2019, 7, 4);

        assertThat(OnThisDay.resurfacesOn(memory, LocalDate.of(2026, 7, 4))).isTrue();
        assertThat(OnThisDay.resurfacesOn(memory, LocalDate.of(2019, 7, 4))).isFalse();
        assertThat(OnThisDay.resurfacesOn(memory, LocalDate.of(2018, 7, 4))).isFalse();
        assertThat(OnThisDay.resurfacesOn(memory, LocalDate.of(2026, 7, 5))).isFalse();
        assertThat(OnThisDay.resurfacesOn(LocalDate.of(2019, 2, 28), LocalDate.of(2024, 2, 28))).isTrue();
    }

    @Test
    void feb28InANonLeapYearAlsoShowsFeb29() {
        assertThat(OnThisDay.daysShownOn(LocalDate.of(2023, 2, 28)))
                .containsExactly(MonthDay.of(2, 28), MonthDay.of(2, 29));
        assertThat(OnThisDay.daysShownOn(LocalDate.of(2024, 2, 28))).containsExactly(MonthDay.of(2, 28));
        assertThat(OnThisDay.daysShownOn(LocalDate.of(2024, 2, 29))).containsExactly(MonthDay.of(2, 29));
    }
}
