package com.madfinder.server.service;

import com.madfinder.server.config.Scoring;
import com.madfinder.server.dto.SearchResponse;
import com.madfinder.server.exception.ApiException;
import com.madfinder.server.roblox.RobloxApiClient;
import org.springframework.cache.annotation.Cacheable;
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

    // @Cacheable: "이 메서드를 캐싱해라"는 표시. 동작 원리 —
    // 메서드 호출되면 스프링이 먼저 캐시(search)에 그 키가 있는지 확인
    // 있으면 → 메서드 본문(로블록스 호출) 아예 실행 안 하고 캐시된 값 즉시 반환
    // 없으면 → 메서드 본문 실행(로블록스 호출) → 그 결과를 캐시에 저장 후 반환
    @Cacheable(
            cacheNames = "search",
            key = "#p0.trim().toLowerCase()", // : 캐시 키 정규화
            unless = "#result.results().isempty()" // 빈 결과는 캐시하지 마라.
            // #result는 메서드 반환값.
            // 로블록스가 잠깐 BUSY(429)라 빈 결과가 났을 때,
            // 그걸 10분간 캐시하면 그동안 계속 빈 화면이 나오니까
            // 결과가 있을 때만 캐시하도록 방어
    )
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
