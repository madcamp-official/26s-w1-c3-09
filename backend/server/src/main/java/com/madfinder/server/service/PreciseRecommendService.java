package com.madfinder.server.service;

import com.madfinder.server.config.CollectionPolicy;
import com.madfinder.server.dto.RecommendStatusResponse;
import com.madfinder.server.exception.ApiException;
import com.madfinder.server.roblox.RobloxApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * 정밀모드(G-1): 티어표(SSS/A/B) 중 cofavorite 없는 '자격 게임'을 그 자리에서 팬수집 후 추천.
 *
 * 흐름: start() → jobId 즉시 반환 → 백그라운드 스레드가 게임별로
 *   그룹 Asc 멤버 → fav 병렬 수집(precise 레인, 배치 b4와 동일 정책·값) → user_favorites 저장
 *   → cofavorite 집계 → 전부 끝나면 RecommendService.compute() → 결과 보관.
 * 자격/표본/probe 값은 배치와 같은 collection.json (진실의 원천 하나).
 *
 * 잡 상태는 메모리 보관 — 서버 재시작 시 소멸(계약에 명시). 유저당 동시 1잡.
 */
@Service
public class PreciseRecommendService {

    private static final Logger log = LoggerFactory.getLogger(PreciseRecommendService.class);

    private final JdbcTemplate jdbc;
    private final RobloxApiClient roblox;
    private final RecommendService recommendService;
    private final CollectionPolicy.FanCollection policy;
    private final com.madfinder.server.config.Scoring scoring;   // 티어 가중치(진행률 중요도)

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final Set<Long> runningUsers = ConcurrentHashMap.newKeySet();
    private final ExecutorService jobRunner = Executors.newSingleThreadExecutor();   // rate 공유라 잡 동시실행 무의미
    private final ExecutorService favPool;   // fav 병렬 폭 = collection.json preciseFavWorkers

    public PreciseRecommendService(JdbcTemplate jdbc, RobloxApiClient roblox,
                                   RecommendService recommendService, CollectionPolicy collectionPolicy,
                                   com.madfinder.server.config.Scoring scoring) {
        this.jdbc = jdbc;
        this.roblox = roblox;
        this.recommendService = recommendService;
        this.policy = collectionPolicy.fanCollection();
        this.scoring = scoring;
        this.favPool = Executors.newFixedThreadPool(policy.preciseFavWorkers());
    }

    /** 진행 중인 정밀 잡 취소 요청 — 다음 게임 넘어가기 전에 멈추고 부분 결과로 계산.
     *  현재 긁는 게임 하나는 마무리됨(중간 중단 시 데이터 깨짐 방지). 없는/끝난 잡은 404. */
    public void cancel(String jobId) {
        Job job = jobs.get(jobId);
        if (job == null) {
            throw ApiException.notFound("JOB_NOT_FOUND", "없는 jobId입니다");
        }
        job.cancelled = true;
    }

    /** 정밀 잡 시작 → jobId. 티어표 없으면 NO_TIER, 이미 진행 중이면 409. */
    public String start(Long userId) {
        Integer tierCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tier_entries WHERE user_id = ?", Integer.class, userId);
        if (tierCount == null || tierCount == 0) {
            throw ApiException.notFound("NO_TIER", "저장된 티어표가 없습니다");
        }
        if (!runningUsers.add(userId)) {
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT,
                    "JOB_ALREADY_RUNNING", "이미 진행 중인 정밀 분석이 있습니다");
        }
        String jobId = UUID.randomUUID().toString();
        Job job = new Job(userId);
        jobs.put(jobId, job);
        jobRunner.submit(() -> runJob(job));
        return jobId;
    }

    public RecommendStatusResponse status(String jobId) {
        Job job = jobs.get(jobId);
        if (job == null) {
            throw ApiException.notFound("JOB_NOT_FOUND", "없는 jobId입니다 (서버 재시작으로 만료됐을 수 있음)");
        }
        return switch (job.status) {
            case "running" -> new RecommendStatusResponse("running",
                    new RecommendStatusResponse.Progress(job.current, job.total, job.collectingName, job.percent),
                    null, null);
            case "finalizing" -> new RecommendStatusResponse("finalizing", null, null,
                    job.cancelled ? "분석을 정리하는 중입니다" : "추천을 계산하는 중입니다");
            case "done" -> new RecommendStatusResponse("done", null, job.result.sections(), null);
            default -> new RecommendStatusResponse("error", null, null, job.message);
        };
    }

    // ---------- 백그라운드 잡 ----------

    private void runJob(Job job) {
        try {
            List<Map<String, Object>> targets = findTargets(job.userId);
            job.total = targets.size();
            // 진행률 중요도 가중: 티어 가중치 × 위치 계수(같은 등급에서 왼쪽일수록 큼).
            // 위치 계수 = (1+range)…(1-range) 선형 (leftmost…rightmost), range=scoring.progressPositionRange.
            double[] progressWeight = progressWeights(targets);
            double totalWeight = java.util.Arrays.stream(progressWeight).sum();
            double doneWeight = 0;
            log.info("정밀 잡 {}: 대상 {}게임", job.userId, job.total);

            for (int i = 0; i < targets.size(); i++) {
                if (job.cancelled) {
                    log.info("정밀 잡 {} 취소됨 — {}/{}까지 부분 결과로 계산", job.userId, i, job.total);
                    break;   // 다음 게임 안 시작 (현재 게임은 이미 완료된 상태)
                }
                Map<String, Object> t = targets.get(i);
                job.current = i + 1;
                job.collectingName = (String) t.get("name");
                long universeId = ((Number) t.get("universe_id")).longValue();
                long groupId = ((Number) t.get("creator_group_id")).longValue();
                // 게임 수집 중에도 진행률이 오르게: 완료분 + 현재게임 가중치×(모은 멤버/표본)
                final double base = doneWeight;
                final double gw = progressWeight[i];
                final int sample = policy.sampleSize();
                final double tw = totalWeight;
                collectGame(universeId, groupId, collected -> {
                    double frac = sample > 0 ? Math.min(1.0, (double) collected / sample) : 1.0;
                    job.percent = tw > 0 ? (int) Math.round((base + gw * frac) / tw * 100) : 100;
                });
                aggregateCofavorite(universeId);
                doneWeight += progressWeight[i];
                job.percent = totalWeight > 0 ? (int) Math.round(doneWeight / totalWeight * 100) : 100;
            }
            job.status = "finalizing";                 // 수집 완료(또는 취소) → 추천 계산 단계
            job.result = recommendService.compute(job.userId);
            job.status = "done";
        } catch (Exception e) {
            log.error("정밀 잡 실패 (user {})", job.userId, e);
            job.status = "error";
            job.message = "정밀 수집 중 오류가 발생했습니다";
        } finally {
            runningUsers.remove(job.userId);
        }
    }

    /**
     * 게임별 진행률 가중치 = 티어 가중치 × 위치 계수.
     * 위치 계수: 같은 티어 안에서 position 오름차순 정렬 후 왼쪽 끝 (1+range) → 오른쪽 끝 (1-range) 선형.
     * 티어에 대상이 1개뿐이면 계수 1.0 (위치 편향 없음). targets 순서 그대로 인덱스 대응.
     */
    private double[] progressWeights(List<Map<String, Object>> targets) {
        double range = scoring.progressPositionRange();
        double[] w = new double[targets.size()];
        java.util.Map<String, java.util.List<Integer>> byTier = new java.util.HashMap<>();
        for (int i = 0; i < targets.size(); i++) {
            byTier.computeIfAbsent((String) targets.get(i).get("tier"), k -> new ArrayList<>()).add(i);
        }
        for (var e : byTier.entrySet()) {
            List<Integer> idx = e.getValue();
            idx.sort(java.util.Comparator.comparingInt(
                    i -> ((Number) targets.get(i).get("position")).intValue()));
            double base = scoring.tierWeights().getOrDefault(e.getKey(), 1.0);
            int n = idx.size();
            for (int r = 0; r < n; r++) {
                double t = n > 1 ? (double) r / (n - 1) : 0.5;   // 단독이면 중앙 → 계수 1.0
                double factor = 1.0 + range - 2 * range * t;      // leftmost 1+range … rightmost 1-range
                w[idx.get(r)] = base * factor;
            }
        }
        return w;
    }

    /** 정밀 수집 대상: 티어 SSS/A/B ∩ 자격(그룹·신생·동접·fan_cacheable) ∩ cofavorite 없음 */
    private List<Map<String, Object>> findTargets(Long userId) {
        int maxAgeDays = (int) (policy.maxAgeYears() * 365.25);
        return jdbc.queryForList(
                "SELECT g.universe_id, g.name, g.creator_group_id, t.tier, t.position "
                + "FROM tier_entries t JOIN games g ON g.universe_id = t.universe_id "
                + "WHERE t.user_id = ? AND t.tier IN ('SSS','A','B') "
                + "  AND g.creator_type = 'Group' AND g.creator_group_id IS NOT NULL "
                + "  AND g.playing >= ? AND g.created >= (NOW() - INTERVAL ? DAY) "
                + "  AND (g.fan_cacheable IS NULL OR g.fan_cacheable = TRUE) "
                + "  AND NOT EXISTS (SELECT 1 FROM game_cofavorite c WHERE c.seed_universe_id = g.universe_id)",
                userId, policy.playingFloor(), maxAgeDays);
    }

    /** 한 게임 팬수집 — 배치 b4와 동일 정책. onCollected: 페이지마다 누적 수집 인원 통지(진행률용). */
    private void collectGame(long universeId, long groupId,
                             java.util.function.IntConsumer onCollected) throws InterruptedException {
        int sample = policy.sampleSize();
        int collected = 0;
        int probeHas = 0;
        int probeTotal = 0;
        Boolean fanCacheable = null;
        String cursor = null;

        while (collected < sample) {
            RobloxApiClient.MemberPage page = roblox.fetchGroupMembersPrecise(groupId, cursor);
            if (!page.ok()) {
                if (collected == 0) {
                    jdbc.update("UPDATE games SET fan_cacheable = FALSE WHERE universe_id = ?", universeId);
                    log.info("  {} 멤버 비공개 → fan_cacheable=false, 스킵", universeId);
                }
                return;
            }
            if (page.userIds().isEmpty()) {
                break;
            }
            List<Long> take = page.userIds().subList(0, Math.min(page.userIds().size(), sample - collected));

            Set<Long> seen = alreadyCollected(take);
            List<Long> unseen = take.stream().filter(u -> !seen.contains(u)).toList();

            // fav 병렬 수집 — precise 레인 버킷이 rate 조절 (스레드는 대기 요청일 뿐)
            Map<Long, List<Long>> favMap = fetchFavoritesParallel(unseen);

            List<Object[]> rows = new ArrayList<>();
            for (Map.Entry<Long, List<Long>> e : favMap.entrySet()) {
                if (e.getValue() != null) {
                    for (Long gameId : e.getValue()) {
                        rows.add(new Object[]{e.getKey(), gameId});
                    }
                }
            }
            if (!rows.isEmpty()) {
                jdbc.batchUpdate(
                        "INSERT IGNORE INTO user_favorites (user_id, fav_universe_id) VALUES (?, ?)", rows);
            }

            for (Long mid : take) {                      // probe 집계 (b4와 동일)
                if (probeTotal >= policy.fanCacheableProbe()) {
                    break;
                }
                probeTotal++;
                List<Long> favs = favMap.get(mid);
                if (seen.contains(mid) || (favs != null && !favs.isEmpty())) {
                    probeHas++;
                }
            }
            collected += take.size();
            onCollected.accept(collected);   // 진행률 갱신 (게임 내 멤버 수집분 반영)
            cursor = page.nextCursor();

            jdbc.update(
                    "INSERT INTO group_cursors (group_id, sort_order, progress_cursor, users_collected, collection_status) "
                    + "VALUES (?, 'Asc', ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE progress_cursor=VALUES(progress_cursor), "
                    + "users_collected=VALUES(users_collected), collection_status=VALUES(collection_status)",
                    groupId, cursor, collected,
                    (collected >= sample || cursor == null) ? "complete" : "in_progress");

            if (fanCacheable == null && probeTotal >= policy.fanCacheableProbe()) {
                double rate = (double) probeHas / probeTotal;
                fanCacheable = rate >= policy.fanCacheableThreshold();
                jdbc.update("UPDATE games SET fan_cacheable = ? WHERE universe_id = ?", fanCacheable, universeId);
                if (!fanCacheable) {
                    log.info("  {} fan_cacheable=false (보유율 {}%) → 중단", universeId, Math.round(rate * 100));
                    return;
                }
            }
            if (cursor == null) {
                break;
            }
        }
    }

    /** fav 병렬 조회. 조회 실패(null)는 빈 목록으로 취급 — b4와 동일한 알려진 한계(P9). */
    private Map<Long, List<Long>> fetchFavoritesParallel(List<Long> userIds) throws InterruptedException {
        List<Callable<Map.Entry<Long, List<Long>>>> tasks = userIds.stream()
                .<Callable<Map.Entry<Long, List<Long>>>>map(uid -> () -> {
                    List<Long> favs = roblox.fetchFavoriteIdsPrecise(uid);
                    return Map.entry(uid, favs != null ? favs : List.<Long>of());
                })
                .toList();
        Map<Long, List<Long>> out = new ConcurrentHashMap<>();
        for (Future<Map.Entry<Long, List<Long>>> f : favPool.invokeAll(tasks)) {
            try {
                Map.Entry<Long, List<Long>> e = f.get();
                out.put(e.getKey(), e.getValue());
            } catch (Exception ignored) {
                // 개별 실패는 그 유저만 없음 처리
            }
        }
        return out;
    }

    private Set<Long> alreadyCollected(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }
        String in = userIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        return new java.util.HashSet<>(jdbc.queryForList(
                "SELECT DISTINCT user_id FROM user_favorites WHERE user_id IN (" + in + ")", Long.class));
    }

    /** cofavorite 집계 — 배치 b4와 동일 SQL (user_favorites 셀프조인) */
    private void aggregateCofavorite(long seedId) {
        Integer sampleSize = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_favorites WHERE fav_universe_id = ?", Integer.class, seedId);
        if (sampleSize == null || sampleSize == 0) {
            return;
        }
        jdbc.update("DELETE FROM game_cofavorite WHERE seed_universe_id = ?", seedId);
        jdbc.update(
                "INSERT INTO game_cofavorite (seed_universe_id, related_universe_id, overlap_count, sample_size) "
                + "SELECT ?, uf2.fav_universe_id, COUNT(*), ? "
                + "FROM user_favorites uf1 JOIN user_favorites uf2 ON uf1.user_id = uf2.user_id "
                + "WHERE uf1.fav_universe_id = ? AND uf2.fav_universe_id <> ? "
                + "GROUP BY uf2.fav_universe_id HAVING COUNT(*) >= ?",
                seedId, sampleSize, seedId, seedId, policy.minOverlap());
    }

    private static class Job {
        final Long userId;
        volatile String status = "running";       // running | finalizing | done | error
        volatile boolean cancelled = false;
        volatile int current = 0;
        volatile int total = 0;
        volatile int percent = 0;                 // 티어 가중 진행률 0~100
        volatile String collectingName;
        volatile com.madfinder.server.dto.RecommendResponse result;
        volatile String message;

        Job(Long userId) {
            this.userId = userId;
        }
    }
}
