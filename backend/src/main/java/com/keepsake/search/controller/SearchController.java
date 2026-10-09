package com.keepsake.search.controller;

import com.keepsake.search.dto.SearchResultsDto;
import com.keepsake.search.service.SearchService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/api/search")
    public SearchResultsDto search(
            @AuthenticationPrincipal UUID viewerId,
            @RequestParam(name = "q", required = false) String query
    ) {
        return searchService.search(viewerId, query);
    }
}
