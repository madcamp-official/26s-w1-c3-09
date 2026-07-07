package com.madfinder.server.service;

import com.madfinder.server.entity.GameRecommendation;
import com.madfinder.server.repository.GameRecommendationRepository;
import com.madfinder.server.roblox.RobloxApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 연쇄추천(People-Also-Join, C-1) 캐시 — game_recommendations.
 * 캐시에 있으면 그대로, 없으면 로블록스 realtime 호출 → 저장 → to-게임 상세 backfill(장르·아이콘은 빈 값으로 오므로).
 * 상세 "함께 즐기는 게임"과 정밀모드 덤 확장이 공유한다. compute는 이 캐시(DB)를 읽기만 하므로 트랜잭션 안 HTTP 없음.
 */
@Service
public class RecommendationCacheService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationCacheService.class);

    private final RobloxApiClient roblox;
    private final GameRecommendationRepository repo;

    public RecommendationCacheService(RobloxApiClient roblox, GameRecommendationRepository repo) {
        this.roblox = roblox;
        this.repo = repo;
    }

    /**
     * 게임의 연쇄추천 to-universeId 목록(rank 순, 최대 6). 캐시 없으면 즉석 수집.
     * 트랜잭션으로 감싸지 않음 — HTTP 호출을 트랜잭션 밖에서 하고 저장만 짧게.
     */
    public List<Long> ensureRecommendations(Long universeId) {
        List<GameRecommendation> cached = repo.findByFromUniverseIdOrderByRecRankAsc(universeId);
        if (!cached.isEmpty()) {
            return cached.stream().map(GameRecommendation::getToUniverseId).toList();
        }
        List<Long> recs;
        try {
            recs = roblox.fetchRecommendationsRealtime(universeId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }
        if (recs.isEmpty()) {
            return List.of();
        }
        List<GameRecommendation> rows = new ArrayList<>();
        for (int i = 0; i < recs.size(); i++) {
            GameRecommendation r = new GameRecommendation();
            r.setFromUniverseId(universeId);
            r.setToUniverseId(recs.get(i));
            r.setRecRank((short) (i + 1));
            rows.add(r);
        }
        try {
            repo.saveAll(rows);
        } catch (Exception e) {
            // 동시 요청이 같은 게임을 먼저 저장했을 때의 중복키 등 — 데이터는 이미 있으니 무시
            log.debug("연쇄추천 저장 스킵({}): {}", universeId, e.getMessage());
        }
        // 표시용 상세(장르·아이콘) backfill은 호출측이 담당 (getSimilar / compute 후보 채움) — 캐시 히트 때도 커버되게.
        return recs;
    }
}
