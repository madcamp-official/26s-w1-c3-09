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
 * 응답은 두 섹션(popular/discovery) — alpha만 다르고 나머지 계산 동일.
 * 섹션 간 중복 제거 안 함(확정): 겹침은 alpha 값 튜닝의 문제.
 */
@Service
public class RecommendService {

    private final TierEntryRepository tierEntryRepository;
    private final UserFavoriteRepository userFavoriteRepository;
    private final GameCofavoriteRepository gameCofavoriteRepository;
    private final com.madfinder.server.repository.GameRecommendationRepository gameRecommendationRepository;
    private final GameRepository gameRepository;
    private final UserRecommendationRepository userRecommendationRepository;
    private final GameBackfillService backfill;
    private final Scoring scoring;

    public RecommendService(TierEntryRepository tierEntryRepository,
                            UserFavoriteRepository userFavoriteRepository,
                            GameCofavoriteRepository gameCofavoriteRepository,
                            com.madfinder.server.repository.GameRecommendationRepository gameRecommendationRepository,
                            GameRepository gameRepository,
                            UserRecommendationRepository userRecommendationRepository,
                            GameBackfillService backfill,
                            Scoring scoring) {
        this.tierEntryRepository = tierEntryRepository;
        this.userFavoriteRepository = userFavoriteRepository;
        this.gameCofavoriteRepository = gameCofavoriteRepository;
        this.gameRecommendationRepository = gameRecommendationRepository;
        this.gameRepository = gameRepository;
        this.userRecommendationRepository = userRecommendationRepository;
        this.backfill = backfill;
        this.scoring = scoring;
    }

    /** 일반모드 — cofavorite만 (덤 확장 없음: DB-only라 미캐시 게임 쏠림 방지). */
    @Transactional
    public RecommendResponse compute(Long userId) {
        return compute(userId, false);
    }

    /**
     * POST /api/recommend — 계산 + 저장 + 반환.
     * applyDerived=true(정밀): 티어 게임의 연쇄추천(game_recommendations)을 덤 후보로 추가.
     *   덤 = 티어 게임의 to-게임, 여러 소스면 최고 티어 하나만(dedup), 점수 = 최고티어가중치 × derivedTierFactor.
     *   유저가 이미 배치·즐겨찾기한 게임은 제외 단계에서 빠짐(유저 배치 우선). cofavorite 점수와 합산.
     * game_recommendations는 미리 캐시돼 있어야 함(정밀이 finalize 전에 ensureRecommendations 수행) → 여기선 DB만 읽음.
     */
    @Transactional
    public RecommendResponse compute(Long userId, boolean applyDerived) {
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

        // 2.5) 덤 확장(정밀 전용): 티어 게임의 연쇄추천 to-게임을 후보로. 여러 소스면 최고 티어 하나만(dedup),
        //      점수 = 최고티어가중치 × derivedTierFactor. cofavorite 점수와 합산. (일반모드는 스킵 — 쏠림 방지)
        if (applyDerived) {
            Map<Long, Double> tierGameWeight = new HashMap<>();
            for (TierEntry t : tier) {
                tierGameWeight.put(t.getUniverseId(), scoring.tierWeights().get(t.getTier()));
            }
            Map<Long, Double> derivedBest = new HashMap<>();   // 덤게임 → 최고 소스 티어 가중치(합산 아님)
            for (var r : gameRecommendationRepository.findByFromUniverseIdIn(tierGameWeight.keySet())) {
                Double srcW = tierGameWeight.get(r.getFromUniverseId());
                if (srcW != null) {
                    derivedBest.merge(r.getToUniverseId(), srcW, Math::max);
                }
            }
            double factor = scoring.derivedTierFactor();
            for (var e : derivedBest.entrySet()) {
                raw.merge(e.getKey(), e.getValue() * factor, Double::sum);
            }
        }

        // 3) 후보 제외: 유저의 모든 즐겨찾기 + 티어 게임 (F-7) — 유저 배치가 덤보다 우선(여기서 덤 후보도 제거)
        Set<Long> exclude = new HashSet<>(seedWeights.keySet());
        raw.keySet().removeAll(exclude);
        if (raw.isEmpty()) {
            return emptyResponse();                    // cofavorite 캐시 미비 (배치 수집 대기)
        }

        // 4) ★후보 즉석 채움 — 점수 상위 미보유 게임을 detail+icon으로 games에 등록
        //    (추천에 뜨는 게임은 항상 이름·장르·썸네일 완비 — 미보유라 탈락하는 일 없게)
        List<Long> topCandidates = raw.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(scoring.candidateBackfillLimit())
                .map(Map.Entry::getKey)
                .toList();
        backfill.ensureGames(topCandidates);

        // 5) 섹션별: 유명도 보정(alpha) × 나이 보정(G-5) + 동접 하한 → 섹션별 상위 count
        Map<Long, Game> games = gameRepository.findByUniverseIdIn(raw.keySet()).stream()
                .collect(Collectors.toMap(Game::getUniverseId, Function.identity()));
        userRecommendationRepository.deleteByUserId(userId);   // 유저당 1세트 덮어쓰기
        List<RecommendResponse.Item> popular = rankSection(userId, "popular", raw, games);
        List<RecommendResponse.Item> discovery = rankSection(userId, "discovery", raw, games);
        return new RecommendResponse(new RecommendResponse.Sections(popular, discovery));
    }

    /** 한 섹션 순위 계산 + user_recommendations 저장. 섹션 간 중복 제거 안 함. */
    private List<RecommendResponse.Item> rankSection(Long userId, String section,
                                                     Map<Long, Double> raw, Map<Long, Game> games) {
        Scoring.Section cfg = scoring.sections().get(section);
        record Scored(Game game, double score) {
        }
        List<Scored> ranked = raw.entrySet().stream()
                .map(e -> {
                    Game g = games.get(e.getKey());
                    if (g == null) {
                        return null;               // 백필 한도 밖/조회실패 → 제외
                    }
                    int playing = g.getPlaying() != null ? g.getPlaying() : 0;
                    if (playing < scoring.playingFloor()) {
                        return null;               // 죽은 게임 필터 (E-3 동접 하한)
                    }
                    long visits = g.getVisits() != null && g.getVisits() > 0 ? g.getVisits() : 1;
                    double age = g.getCreated() != null
                            ? Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(
                                    g.getCreated(), java.time.LocalDateTime.now()) / 365.25)
                            : 0;
                    double score = e.getValue() / Math.pow(visits, cfg.alpha())
                            * scoring.agePenalty().factor(age);
                    return new Scored(g, score);
                })
                .filter(java.util.Objects::nonNull)
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .limit(cfg.count())
                .toList();

        List<RecommendResponse.Item> items = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            Scored s = ranked.get(i);
            UserRecommendation row = new UserRecommendation();
            row.setUserId(userId);
            row.setUniverseId(s.game().getUniverseId());
            row.setSection(section);
            row.setScore(s.score());
            row.setRecRank((short) (i + 1));
            userRecommendationRepository.save(row);
            items.add(toItem(i + 1, s.game(), s.score()));
        }
        return items;
    }

    /** GET /api/recommendations/{userId} — 저장된 결과 재조회 (재계산 없음) */
    public RecommendResponse getSaved(Long userId) {
        List<UserRecommendation> saved = userRecommendationRepository.findByUserIdOrderByRecRankAsc(userId);
        if (saved.isEmpty()) {
            return emptyResponse();
        }
        Map<Long, Game> games = gameRepository.findByUniverseIdIn(
                        saved.stream().map(UserRecommendation::getUniverseId).toList()).stream()
                .collect(Collectors.toMap(Game::getUniverseId, Function.identity()));
        Map<String, List<RecommendResponse.Item>> bySection = saved.stream()
                .filter(r -> games.containsKey(r.getUniverseId()))
                .collect(Collectors.groupingBy(UserRecommendation::getSection,
                        Collectors.mapping(r -> toItem(r.getRecRank(), games.get(r.getUniverseId()), r.getScore()),
                                Collectors.toList())));
        return new RecommendResponse(new RecommendResponse.Sections(
                bySection.getOrDefault("popular", List.of()),
                bySection.getOrDefault("discovery", List.of())));
    }

    private RecommendResponse emptyResponse() {
        return new RecommendResponse(new RecommendResponse.Sections(List.of(), List.of()));
    }

    private RecommendResponse.Item toItem(int rank, Game g, double score) {
        return new RecommendResponse.Item(
                rank, g.getUniverseId(), g.getName(), g.getGenreL1(), g.getGenreL2(),
                Math.round(score * 100.0) / 100.0, g.getPlaying(), g.getIconUrl());
    }
}
