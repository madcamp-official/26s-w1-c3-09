package com.madfinder.server.controller;

import com.madfinder.server.dto.SearchResponse;
import com.madfinder.server.service.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/search?q= — 게임 이름 검색 (티어표 직접 추가용, 2페이지). */
@RestController
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/api/search")
    public SearchResponse search(@RequestParam String q) {
        return searchService.search(q.trim().toLowerCase());
    }
}
