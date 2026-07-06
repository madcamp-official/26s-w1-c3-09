package com.madfinder.server.service;

import com.madfinder.server.config.Scoring;
import com.madfinder.server.dto.SearchResponse;
import com.madfinder.server.exception.ApiException;
import com.madfinder.server.roblox.RobloxApiClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 게임 이름 검색 (티어표 직접 추가용, 2페이지) — 로블록스 omni-search 실시간.
 * 응답에 아이콘이 없어(실측 A-3) 상위 N개만 thumb_icon으로 보충. DB 미사용.
 */
@Service
public class SearchService {

    private final RobloxApiClient roblox;
    private final Scoring scoring;

    public SearchService(RobloxApiClient roblox, Scoring scoring) {
        this.roblox = roblox;
        this.scoring = scoring;
    }

    public SearchResponse search(String q) {
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
