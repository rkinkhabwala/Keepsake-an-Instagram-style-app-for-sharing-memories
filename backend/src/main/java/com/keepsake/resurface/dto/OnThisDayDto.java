package com.keepsake.resurface.dto;

import java.time.LocalDate;
import java.util.List;

/** @param years newest year first; empty when nothing happened on this day before */
public record OnThisDayDto(LocalDate date, List<OnThisDayYearDto> years) {
}
