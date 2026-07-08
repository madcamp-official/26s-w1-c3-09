package com.madfinder.server.service;

import com.madfinder.server.config.Scoring;
import com.madfinder.server.dto.SearchResponse;
import com.madfinder.server.entity.CollectQueue;
import com.madfinder.server.entity.Game;
import com.madfinder.server.exception.ApiException;
import com.madfinder.server.repository.CollectQueueRepository;
import com.madfinder.server.repository.GameRepository;
import com.madfinder.server.roblox.RobloxApiClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 게임 이름 검색 (티어표 직접 추가용, 2페이지) — 로블록스 omni-search 실시간.
 * 응답에 아이콘이 없어(실측 A-3) 상위 N개만 thumb_icon으로 보충.
 * S7: 검색으로 본 미보유 게임을 collect_queue에 등록 → 배치가 나중에 팬수집(유저 활동이 곧 수집 확장).
 */
@Service
public class SearchService {

    private final RobloxApiClient roblox;
    private final Scoring scoring;
    private final GameRepository gameRepository;
    private final CollectQueueRepository collectQueueRepository;

    public SearchService(RobloxApiClient roblox, Scoring scoring,
                         GameRepository gameRepository, CollectQueueRepository collectQueueRepository) {
        this.roblox = roblox;
        this.scoring = scoring;
        this.gameRepository = gameRepository;
        this.collectQueueRepository = collectQueueRepository;
    }

    // @Cacheable: "이 메서드를 캐싱해라"는 표시. 동작 원리 —
    // 메서드 호출되면 스프링이 먼저 캐시(search)에 그 키가 있는지 확인
    // 있으면 → 메서드 본문(로블록스 호출) 아예 실행 안 하고 캐시된 값 즉시 반환
    // 없으면 → 메서드 본문 실행(로블록스 호출) → 그 결과를 캐시에 저장 후 반환
    @Cacheable(
            cacheNames = "search",
            // key = "#p0.trim().toLowerCase()", // : 캐시 키 정규화
            // -> controller에서 처리
            unless = "#result.results().isEmpty()" // 빈 결과는 캐시하지 마라.
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
        List<Long> ids = found.stream().map(RobloxApiClient.SearchResult::universeId).toList();
        queueUnknown(ids);   // S7: 미보유 게임 수집 대기열 등록 (비동기 — 검색 응답은 안 기다림)

        Map<Long, String> icons;
        try {
            icons = roblox.fetchGameIconsRealtime(ids);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiException.robloxError("아이콘 조회가 중단됐습니다");
        }
        return new SearchResponse(found.stream()
                .map(r -> new SearchResponse.Result(
                        r.universeId(), r.name(), r.playerCount(), icons.get(r.universeId())))
                .toList());
    }

    /** games에도 collect_queue에도 없는 게임을 reason='search'로 등록 (b2가 채움). */
    private void queueUnknown(List<Long> ids) {
        Set<Long> known = gameRepository.findByUniverseIdIn(ids).stream()
                .map(Game::getUniverseId).collect(Collectors.toSet());
        for (Long id : ids) {
            if (!known.contains(id) && !collectQueueRepository.existsById(id)) {
                CollectQueue q = new CollectQueue();
                q.setUniverseId(id);
                q.setReason("search");
                collectQueueRepository.save(q);
            }
        }
    }
}
