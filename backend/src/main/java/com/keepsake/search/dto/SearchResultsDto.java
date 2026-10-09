package com.keepsake.search.dto;

import com.keepsake.user.dto.UserSummaryDto;
import java.util.List;

public record SearchResultsDto(List<UserSummaryDto> users, List<String> hashtags) {

    public static final SearchResultsDto EMPTY = new SearchResultsDto(List.of(), List.of());
}
