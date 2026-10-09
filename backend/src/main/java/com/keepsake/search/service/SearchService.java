package com.keepsake.search.service;

import com.keepsake.search.dto.SearchResultsDto;
import com.keepsake.search.repository.SearchRepository;
import com.keepsake.user.service.UserSummaries;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Search (spec §8): users by prefix of username or display name, and hashtags by prefix. A leading @ searches only
 * users and a leading # only hashtags.
 */
@Service
public class SearchService {

    static final int LIMIT = 20;
    static final int MAX_QUERY_LENGTH = 100;

    private final SearchRepository searchRepository;
    private final UserSummaries userSummaries;

    public SearchService(SearchRepository searchRepository, UserSummaries userSummaries) {
        this.searchRepository = searchRepository;
        this.userSummaries = userSummaries;
    }

    @Transactional(readOnly = true)
    public SearchResultsDto search(UUID viewerId, String rawQuery) {
        String query = rawQuery == null ? "" : Normalizer.normalize(rawQuery.strip(), Normalizer.Form.NFC);
        boolean usersOnly = query.startsWith("@");
        boolean tagsOnly = query.startsWith("#");
        if (usersOnly || tagsOnly) {
            query = query.substring(1);
        }
        query = query.toLowerCase(Locale.ROOT);
        if (query.isEmpty() || query.length() > MAX_QUERY_LENGTH) {
            return SearchResultsDto.EMPTY;
        }
        String prefix = escapeLike(query) + "%";
        return new SearchResultsDto(
                tagsOnly ? List.of() : searchRepository.findUsers(prefix, query, viewerId, LIMIT).stream()
                        .map(userSummaries::of).toList(),
                usersOnly ? List.of() : searchRepository.findHashtags(prefix, viewerId, LIMIT));
    }

    /** Makes the query match literally: % and _ are LIKE wildcards, \ is the escape character. */
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
