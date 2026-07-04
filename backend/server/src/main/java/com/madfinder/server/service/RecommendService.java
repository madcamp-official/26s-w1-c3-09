package com.madfinder.server.service;

import com.madfinder.server.config.Scoring;
import com.madfinder.server.dto.RecommendResponse;
import com.madfinder.server.entity.Game;
import com.madfinder.server.entity.GameCofavorite;
import com.madfinder.server.entity.TierEntry;
import com.madfinder.server.entity.UserFavorite;
import com.madfinder.server.entity.UserRecommendation;
import com.madfinder.server.exception.ApiException;
import com.madfinder.server.repository.GameCofavoriteRepository;
import com.madfinder.server.repository.GameRepository;
import com.madfinder.server.repository.TierEntryRepository;
import com.madfinder.server.repository.UserFavoriteRepository;
import com.madfinder.server.repository.UserRecommendationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 추천 계산 (F-6/F-7) — 전 과정 DB만 사용 (로블록스 실시간 호출 0, F-1).
 *
 * 점수: raw(c) = Σ_g 티어가중치(g) × overlap(g, c)   [g = 티어표 게임 + 미배치 즐겨찾기(0.3)]
 *       final = raw / visits^alpha                    [유명도 보정, E-2]
 * 후보 제외: 유저의 모든 즐겨찾기(티어 배치+미배치) — 이미 아는 게임 추천 무의미 (F-7)
 * depth1만 탐색 (F-6: depth2는 노이즈 — 후보 부족 시에만 확장 예정)
 *
 * TODO(KJH): 두 섹션(popular/discovery) 분리 응답 — API 명세 협의 후.
 *            지금은 popular alpha 단일 점수 상위 topN.
 */
@Service
public class RecommendService {

    private final TierEntryRepository tierEntryRepository;
    private final UserFavoriteRepository userFavoriteRepository;
    private final GameCofavoriteRepository gameCofavoriteRepository;
    private final GameRepository gameRepository;
    private final UserRecommendationRepository userRecommendationRepository;
    private final Scoring scoring;

    public RecommendService(TierEntryRepository tierEntryRepository,
                            UserFavoriteRepository userFavoriteRepository,
                            GameCofavoriteRepository gameCofavoriteRepository,
                            GameRepository gameRepository,
                            UserRecommendationRepository userRecommendationRepository,
                            Scoring scoring) {
        this.tierEntryRepository = tierEntryRepository;
        this.userFavoriteRepository = userFavoriteRepository;
        this.gameCofavoriteRepository = gameCofavoriteRepository;
        this.gameRepository = gameRepository;
        this.userRecommendationRepository = userRecommendationRepository;
        this.scoring = scoring;
    }

    /** POST /api/recommend — 계산 + 저장 + 반환 */
    @Transactional
    public RecommendResponse compute(Long userId) {
        List<TierEntry> tier = tierEntryRepository.findByUserId(userId);
        if (tier.isEmpty()) {
            throw ApiException.notFound("NO_TIER", "저장된 티어표가 없습니다");
        }

        // 1) 신호 소스: 티어 게임(등급 가중치) + 미배치 즐겨찾기(0.3)
        Map<Long, Double> seedWeights = new HashMap<>();
        for (TierEntry t : tier) {
            seedWeights.put(t.getUniverseId(), scoring.tierWeights().get(t.getTier()));
        }
        List<UserFavorite> favorites = userFavoriteRepository.findByUserId(userId);
        for (UserFavorite f : favorites) {
            seedWeights.putIfAbsent(f.getFavUniverseId(), scoring.unplacedFavoriteWeight());
        }

        // 2) depth1 cofavorite 로드 → 가중 합산 (F-7)
        List<GameCofavorite> cofavs = gameCofavoriteRepository.findBySeedUniverseIdIn(seedWeights.keySet());
        Map<Long, Double> raw = new HashMap<>();
        for (GameCofavorite c : cofavs) {
            if (c.getOverlapCount() < scoring.minOverlap()) {
                continue;
            }
            double w = seedWeights.get(c.getSeedUniverseId());
            raw.merge(c.getRelatedUniverseId(), w * c.getOverlapCount(), Double::sum);
        }

        // 3) 후보 제외: 유저의 모든 즐겨찾기 + 티어 게임 (F-7)
        Set<Long> exclude = new HashSet<>(seedWeights.keySet());
        raw.keySet().removeAll(exclude);
        if (raw.isEmpty()) {
            return new RecommendResponse(List.of());   // cofavorite 캐시 미비 (배치 수집 대기)
        }

        // 4) 유명도 보정 + 동접 하한 → 상위 topN
        Map<Long, Game> games = gameRepository.findByUniverseIdIn(raw.keySet()).stream()
                .collect(Collectors.toMap(Game::getUniverseId, Function.identity()));
        double alpha = scoring.sections().get("popular").alpha();
        record Scored(Game game, double score) {
        }
        List<Scored> ranked = raw.entrySet().stream()
                .map(e -> {
                    Game g = games.get(e.getKey());
                    if (g == null) {
                        return null;               // games 미보유 → 표시 불가, 제외 (collect_queue가 채움)
                    }
                    int playing = g.getPlaying() != null ? g.getPlaying() : 0;
                    if (playing < scoring.playingFloor()) {
                        return null;               // 죽은 게임 필터 (E-3 동접 하한)
                    }
                    long visits = g.getVisits() != null && g.getVisits() > 0 ? g.getVisits() : 1;
                    return new Scored(g, e.getValue() / Math.pow(visits, alpha));
                })
                .filter(java.util.Objects::nonNull)
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .limit(scoring.topN())
                .toList();

        // 5) 결과 저장 (유저당 1세트 덮어쓰기) + 응답
        userRecommendationRepository.deleteByUserId(userId);
        List<RecommendResponse.Item> items = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            Scored s = ranked.get(i);
            UserRecommendation row = new UserRecommendation();
            row.setUserId(userId);
            row.setUniverseId(s.game().getUniverseId());
            row.setScore(s.score());
            row.setRecRank((short) (i + 1));
            userRecommendationRepository.save(row);
            items.add(toItem(i + 1, s.game(), s.score()));
        }
        return new RecommendResponse(items);
    }

    /** GET /api/recommendations/{userId} — 저장된 결과 재조회 (재계산 없음) */
    public RecommendResponse getSaved(Long userId) {
        List<UserRecommendation> saved = userRecommendationRepository.findByUserIdOrderByRecRankAsc(userId);
        if (saved.isEmpty()) {
            return new RecommendResponse(List.of());
        }
        Map<Long, Game> games = gameRepository.findByUniverseIdIn(
                        saved.stream().map(UserRecommendation::getUniverseId).toList()).stream()
                .collect(Collectors.toMap(Game::getUniverseId, Function.identity()));
        List<RecommendResponse.Item> items = saved.stream()
                .map(r -> {
                    Game g = games.get(r.getUniverseId());
                    return g == null ? null : toItem(r.getRecRank(), g, r.getScore());
                })
                .filter(java.util.Objects::nonNull)
                .toList();
        return new RecommendResponse(items);
    }

    private RecommendResponse.Item toItem(int rank, Game g, double score) {
        return new RecommendResponse.Item(
                rank, g.getUniverseId(), g.getName(), g.getGenreL1(),
                Math.round(score * 100.0) / 100.0, g.getPlaying(), g.getIconUrl());
    }
}
