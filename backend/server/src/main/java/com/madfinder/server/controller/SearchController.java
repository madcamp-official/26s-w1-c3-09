package com.madfinder.server.controller;

import com.madfinder.server.config.Scoring;
import com.madfinder.server.dto.SearchResponse;
import com.madfinder.server.exception.ApiException;
import com.madfinder.server.roblox.RobloxApiClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * GET /api/search?q= — 게임 이름 검색 (티어표 직접 추가용, 2페이지).
 * 로블록스 omni-search 실시간 호출 (apis_search 버킷 — 검색 전용이라 floor 없이 전체 사용).
 * omni-search 응답엔 아이콘이 없어(실측 A-3) thumb_icon으로 상위 N개만 보충. DB 미사용.
 */
@RestController
public class SearchController {

    private final RobloxApiClient roblox;
    private final Scoring scoring;

    public SearchController(RobloxApiClient roblox, Scoring scoring) {
        this.roblox = roblox;
        this.scoring = scoring;
    }

    @GetMapping("/api/search")
    public SearchResponse search(@RequestParam String q) {
        if (q.isBlank()) {
            return new SearchResponse(List.of());
        }
        List<RobloxApiClient.SearchResult> found = roblox.searchGames(q.trim()).stream()
                .limit(scoring.searchResultLimit())
                .toList();
        if (found.isEmpty()) {
            return new SearchResponse(List.of());
        }
        Map<Long, String> icons;
        try {
            icons = roblox.fetchGameIconsRealtime(
                    found.stream().map(RobloxApiClient.SearchResult::universeId).toList());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiException.robloxError("아이콘 조회가 중단됐습니다");
        }
        return new SearchResponse(found.stream()
                .map(r -> new SearchResponse.Result(
                        r.universeId(), r.name(), r.playerCount(), icons.get(r.universeId())))
                .toList());
    }
}
