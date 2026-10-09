package com.keepsake.resurface.service;

import com.keepsake.common.pagination.InvalidRequestException;
import com.keepsake.memory.entity.Memory;
import com.keepsake.memory.service.MemoryDtoAssembler;
import com.keepsake.resurface.dto.OnThisDayDto;
import com.keepsake.resurface.dto.OnThisDayYearDto;
import com.keepsake.resurface.repository.OnThisDayRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnThisDayService {

    static final int LIMIT = 200;

    private final OnThisDayRepository repository;
    private final MemoryDtoAssembler assembler;
    private final Clock clock;

    public OnThisDayService(OnThisDayRepository repository, MemoryDtoAssembler assembler, Clock clock) {
        this.repository = repository;
        this.assembler = assembler;
        this.clock = clock;
    }

    /**
     * @param rawDate the viewer's local date (YYYY-MM-DD); defaults to today in UTC. Any date is safe: only the
     *                viewer's own visible memories can be returned.
     */
    @Transactional(readOnly = true)
    public OnThisDayDto onThisDay(UUID viewerId, String rawDate) {
        LocalDate date = parse(rawDate);
        List<MonthDay> days = OnThisDay.daysShownOn(date);
        MonthDay first = days.getFirst();
        MonthDay second = days.getLast();
        List<Memory> memories = repository.findOnDays(viewerId, date.getYear(),
                first.getMonthValue(), first.getDayOfMonth(), second.getMonthValue(), second.getDayOfMonth(), LIMIT);

        Map<Integer, List<Memory>> byYear = new LinkedHashMap<>();
        memories.forEach(memory -> byYear.computeIfAbsent(memory.getOccurredOn().getYear(), year -> new ArrayList<>())
                .add(memory));
        List<OnThisDayYearDto> years = byYear.entrySet().stream()
                .map(entry -> new OnThisDayYearDto(entry.getKey(), date.getYear() - entry.getKey(),
                        assembler.toGridItems(entry.getValue())))
                .toList();
        return new OnThisDayDto(date, years);
    }

    private LocalDate parse(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return LocalDate.now(clock);
        }
        try {
            return LocalDate.parse(rawDate.strip());
        } catch (DateTimeParseException exception) {
            throw new InvalidRequestException("date must be YYYY-MM-DD");
        }
    }
}
