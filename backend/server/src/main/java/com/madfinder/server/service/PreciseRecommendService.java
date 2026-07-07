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
    private final RecommendationCacheService recommendationCache;   // 연쇄추천(덤 확장·상세) 캐시
    private final CollectionPolicy.FanCollection policy;
    private final com.madfinder.server.config.Scoring scoring;   // 티어 가중치(진행률 중요도)

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final Map<Long, String> runningUsers = new ConcurrentHashMap<>();   // userId → 진행 중 jobId (재연결용)
    private final ExecutorService jobRunner;   // 동시 정밀 잡 실행 (precise.concurrency, 버킷 공유로 rate는 자동 분배)
    private final ExecutorService favPool;   // fav 병렬 폭 = collection.json preciseFavWorkers
    private final int heartbeatTtlSeconds;   // system_heartbeat('precise') TTL — 배치가 이걸 보고 activeShare 차감

    public PreciseRecommendService(JdbcTemplate jdbc, RobloxApiClient roblox,
                                   RecommendService recommendService,
                                   RecommendationCacheService recommendationCache,
                                   CollectionPolicy collectionPolicy,
                                   com.madfinder.server.config.Scoring scoring,
                                   com.madfinder.server.config.RateGovernance governance) {
        this.jdbc = jdbc;
        this.roblox = roblox;
        this.recommendService = recommendService;
        this.recommendationCache = recommendationCache;
        this.policy = collectionPolicy.fanCollection();
        this.scoring = scoring;
        this.favPool = Executors.newFixedThreadPool(policy.preciseFavWorkers());

        var pcfg = governance.precise();
        int concurrency = pcfg != null ? pcfg.concurrencyOrDefault() : 1;
        this.heartbeatTtlSeconds = pcfg != null ? pcfg.ttlSecondsOrDefault() : 30;
        int interval = pcfg != null ? pcfg.intervalSecondsOrDefault() : 10;
        this.jobRunner = Executors.newFixedThreadPool(concurrency);

        // 하트비트: 정밀 잡이 하나라도 도는 동안 system_heartbeat('precise')를 주기 갱신 →
        // 배치가 그동안만 precise.activeShare를 차감(경합 0). 잡 끝나면 갱신 멈춰 TTL로 자동 만료.
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "precise-heartbeat");
            t.setDaemon(true);
            return t;
        }).scheduleAtFixedRate(() -> {
            if (!runningUsers.isEmpty()) {
                touchHeartbeat();
            }
        }, interval, interval, java.util.concurrent.TimeUnit.SECONDS);
    }

    /** system_heartbeat('precise')를 now+TTL로 upsert (배치가 읽는 "정밀 활성" 신호). 실패는 조용히 무시. */
    private void touchHeartbeat() {
        try {
            jdbc.update(
                    "INSERT INTO system_heartbeat (name, expires_at) VALUES ('precise', DATE_ADD(NOW(), INTERVAL ? SECOND)) "
                    + "ON DUPLICATE KEY UPDATE expires_at = VALUES(expires_at)",
                    heartbeatTtlSeconds);
        } catch (Exception e) {
            log.warn("정밀 하트비트 갱신 실패 (조율 일시 무력화, 치명적 아님): {}", e.getMessage());
        }
    }

    /** 진행 중인 정밀 잡 취소 요청 — 이미 수집한 게임들로 "즉시" 결과 계산(화면 바로 넘김).
     *  현재 긁던 게임은 백그라운드에서 끝까지 마무리(중간 중단 시 데이터 깨짐 방지, 다음 번을 위해 저장).
     *  없는 잡은 404. */
    public void cancel(String jobId) {
        Job job = jobs.get(jobId);
        if (job == null) {
            throw ApiException.notFound("JOB_NOT_FOUND", "없는 jobId입니다");
        }
        job.cancelled = true;
        finalizeResult(job);   // 완료된 게임들로 지금 계산·결과 표시 (loop 스레드는 현재 게임을 계속 마무리)
    }

    /** 정밀 잡 시작 → jobId. 티어표 없으면 NO_TIER.
     *  이미 진행 중이면 그 잡의 jobId를 그대로 돌려줘 재연결한다(나갔다 와도 이어서 보이게 — 에러 안 냄). */
    public String start(Long userId) {
        Integer tierCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tier_entries WHERE user_id = ?", Integer.class, userId);
        if (tierCount == null || tierCount == 0) {
            throw ApiException.notFound("NO_TIER", "저장된 티어표가 없습니다");
        }
        synchronized (runningUsers) {
            String existing = runningUsers.get(userId);
            if (existing != null) {
                Job ej = jobs.get(existing);
                // 아직 "진짜 진행 중"인 잡만 재연결. 이미 끝난(done, 백그라운드 마무리 중) 잡이면
                // 옛 결과로 넘기지 말고 새 분석을 시작한다.
                if (ej != null && (ej.status.equals("running") || ej.status.equals("finalizing"))) {
                    return existing;
                }
            }
            String jobId = UUID.randomUUID().toString();
            Job job = new Job(jobId, userId);
            jobs.put(jobId, job);
            runningUsers.put(userId, jobId);   // 끝난 잡 매핑을 덮어씀 (그 잡의 마무리 스레드는 아래 remove에서 무시됨)
            touchHeartbeat();                  // 배치가 즉시 양보하도록 시작하자마자 신호(스케줄 갱신 전 공백 제거)
            jobRunner.submit(() -> runJob(job));
            return jobId;
        }
    }

    /** 추천 계산·완료 처리(정확히 1회) — 취소 스레드와 loop 스레드 중 먼저 도달한 쪽만 계산. */
    private void finalizeResult(Job job) {
        synchronized (job) {
            if (job.finalized) {
                return;
            }
            job.status = "finalizing";
            job.result = recommendService.compute(job.userId, true);   // 정밀 = 덤 확장(연쇄추천) 적용
            job.status = "done";
            job.finalized = true;
        }
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
            // 진행 대상 = 모든 티어 게임(연쇄추천 getRec) + fan 타겟(미수집 Group만 팬수집).
            List<Map<String, Object>> tierGames = jdbc.queryForList(
                    "SELECT t.universe_id, g.name, t.tier, t.position "
                    + "FROM tier_entries t LEFT JOIN games g ON g.universe_id = t.universe_id "
                    + "WHERE t.user_id = ?", job.userId);
            Map<Long, Long> targetGroup = new java.util.HashMap<>();   // 팬수집 타겟 → 그룹id
            for (var t : findTargets(job.userId)) {
                targetGroup.put(((Number) t.get("universe_id")).longValue(),
                        ((Number) t.get("creator_group_id")).longValue());
            }
            job.total = tierGames.size();
            Map<Long, Double> wByGame = progressWeightByGame(tierGames);   // 티어 중요도(등급×위치)
            final int sample = policy.sampleSize();
            // 진행 비용(시간 비례): getRec=1호출(모든 게임), fan=표본수(타겟만). grandTotal로 정규화.
            double grandTotal = 0;
            for (var g : tierGames) {
                long uid = ((Number) g.get("universe_id")).longValue();
                double w = wByGame.getOrDefault(uid, 1.0);
                grandTotal += w + (targetGroup.containsKey(uid) ? w * sample : 0);
            }
            final double gt = grandTotal;
            double doneWork = 0;
            log.info("정밀 잡 {}: 티어 {}게임 (fan 타겟 {})", job.userId, tierGames.size(), targetGroup.size());

            for (int i = 0; i < tierGames.size(); i++) {
                if (job.cancelled) {
                    log.info("정밀 잡 {} 취소됨 — {}/{}까지 부분 결과", job.userId, i, job.total);
                    break;
                }
                Map<String, Object> g = tierGames.get(i);
                long universeId = ((Number) g.get("universe_id")).longValue();
                final double w = wByGame.getOrDefault(universeId, 1.0);
                job.current = i + 1;
                Object nm = g.get("name");
                job.collectingName = nm != null ? nm.toString() : ("게임 " + universeId);

                // ① 연쇄추천 캐시 (모든 티어 게임, 비용 1) — 상세·덤확장 공용
                recommendationCache.ensureRecommendations(universeId);
                final double afterRec = doneWork + w;
                job.percent = gt > 0 ? (int) Math.round(afterRec / gt * 100) : 100;

                // ② fan 수집 (미수집 Group 타겟만, 비용 표본수) — fav마다 진행률 상승
                Long groupId = targetGroup.get(universeId);
                if (groupId != null) {
                    collectGame(universeId, groupId, collected -> {
                        double c = Math.min(collected, sample);
                        job.percent = gt > 0 ? (int) Math.round((afterRec + w * c) / gt * 100) : 100;
                    }, () -> job.cancelled);
                    aggregateCofavorite(universeId);
                    doneWork = afterRec + w * sample;
                } else {
                    doneWork = afterRec;
                }
            }
            finalizeResult(job);                       // 수집 완료 → 계산·완료 (취소로 이미 계산됐으면 건너뜀)
        } catch (Exception e) {
            log.error("정밀 잡 실패 (user {})", job.userId, e);
            if (!job.finalized) {                      // 취소로 이미 결과 표시됐으면 에러로 덮지 않음
                job.status = "error";
                job.message = "정밀 수집 중 오류가 발생했습니다";
            }
        } finally {
            // 내가 현재 매핑일 때만 제거 — 새 잡이 이미 이 유저를 차지했으면 그 매핑을 지우지 않음
            runningUsers.remove(job.userId, job.jobId);
        }
    }

    /**
     * 게임별 진행률 가중치 = 티어 가중치 × 위치 계수.
     * 위치 계수: 같은 티어 안에서 position 오름차순 정렬 후 왼쪽 끝 (1+range) → 오른쪽 끝 (1-range) 선형.
     * 티어에 대상이 1개뿐이면 계수 1.0 (위치 편향 없음). targets 순서 그대로 인덱스 대응.
     */
    private Map<Long, Double> progressWeightByGame(List<Map<String, Object>> games) {
        double range = scoring.progressPositionRange();
        Map<Long, Double> out = new java.util.HashMap<>();
        java.util.Map<String, java.util.List<Integer>> byTier = new java.util.HashMap<>();
        for (int i = 0; i < games.size(); i++) {
            byTier.computeIfAbsent((String) games.get(i).get("tier"), k -> new ArrayList<>()).add(i);
        }
        for (var e : byTier.entrySet()) {
            List<Integer> idx = e.getValue();
            idx.sort(java.util.Comparator.comparingInt(
                    i -> ((Number) games.get(i).get("position")).intValue()));
            double base = scoring.tierWeights().getOrDefault(e.getKey(), 1.0);
            int n = idx.size();
            for (int r = 0; r < n; r++) {
                double t = n > 1 ? (double) r / (n - 1) : 0.5;   // 단독이면 중앙 → 계수 1.0
                double factor = 1.0 + range - 2 * range * t;      // leftmost 1+range … rightmost 1-range
                long uid = ((Number) games.get(idx.get(r)).get("universe_id")).longValue();
                out.put(uid, base * factor);
            }
        }
        return out;
    }

    /**
     * 정밀 수집 대상: 사용자가 티어에 배치한 모든 게임(SSS/A/B/C) ∩ 수집가능 ∩ cofavorite 없음.
     * 배치용 나이·동접 필터는 쓰지 않는다 — 배치는 "새 게임 발굴"이 목표라 신생·인기로 거르지만,
     * 정밀은 "사용자가 직접 고른 이 게임들"을 수집하는 게 목표라 게임 나이·현재 활동과 무관하다
     * (cofavorite 수집은 제작 그룹 멤버의 즐겨찾기를 읽으므로 오래되고 동접 낮아도 가능).
     * 수집가능 요건만 유지: 그룹 제작(멤버 순회 필요) + 비공개판정 안 됨 + 아직 미수집.
     */
    private List<Map<String, Object>> findTargets(Long userId) {
        return jdbc.queryForList(
                "SELECT g.universe_id, g.name, g.creator_group_id, t.tier, t.position "
                + "FROM tier_entries t JOIN games g ON g.universe_id = t.universe_id "
                + "WHERE t.user_id = ? "
                + "  AND g.creator_type = 'Group' AND g.creator_group_id IS NOT NULL "
                + "  AND (g.fan_cacheable IS NULL OR g.fan_cacheable = TRUE) "
                + "  AND NOT EXISTS (SELECT 1 FROM game_cofavorite c WHERE c.seed_universe_id = g.universe_id)",
                userId);
    }

    /** 한 게임 팬수집 — 배치 b4와 동일 정책. onCollected: fav마다 누적 인원 통지(진행률용).
     *  cancelled: 취소되면 다음 페이지를 시작하지 않고 중단(현재 페이지 수집분은 유지 → 데이터 안 깨짐). */
    private void collectGame(long universeId, long groupId,
                             java.util.function.IntConsumer onCollected,
                             java.util.function.BooleanSupplier cancelled) throws InterruptedException {
        int sample = policy.sampleSize();
        int collected = 0;
        int probeHas = 0;
        int probeTotal = 0;
        Boolean fanCacheable = null;
        String cursor = null;

        while (collected < sample) {
            if (cancelled.getAsBoolean()) {
                return;   // 취소 → 다음 페이지 안 시작(실행기를 빨리 풀어 재시작 대기 줄임)
            }
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

            // fav 병렬 수집 — precise 레인 버킷이 rate 조절 (스레드는 대기 요청일 뿐).
            // fav 1개 끝날 때마다 진행률 통지 → 페이지(멤버 100명)당 100초여도 진행률이 ~1초마다 오른다.
            final int base = collected;
            final int freeSeen = seen.size();   // 이미 수집된 멤버는 즉시 카운트(재조회 없음)
            final java.util.concurrent.atomic.AtomicInteger favDone = new java.util.concurrent.atomic.AtomicInteger();
            Map<Long, List<Long>> favMap = fetchFavoritesParallel(unseen,
                    () -> onCollected.accept(base + freeSeen + favDone.incrementAndGet()));

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

    /** fav 병렬 조회. 조회 실패(null)는 빈 목록으로 취급 — b4와 동일한 알려진 한계(P9).
     *  onEach: fav 1개 조회가 끝날 때마다 호출(진행률 통지용, 여러 스레드에서 호출됨). */
    private Map<Long, List<Long>> fetchFavoritesParallel(List<Long> userIds, Runnable onEach)
            throws InterruptedException {
        List<Callable<Map.Entry<Long, List<Long>>>> tasks = userIds.stream()
                .<Callable<Map.Entry<Long, List<Long>>>>map(uid -> () -> {
                    List<Long> favs = roblox.fetchFavoriteIdsPrecise(uid);
                    Map.Entry<Long, List<Long>> e = Map.entry(uid, favs != null ? favs : List.<Long>of());
                    onEach.run();   // 완료 통지 (진행률 갱신)
                    return e;
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
        final String jobId;
        final Long userId;
        volatile String status = "running";       // running | finalizing | done | error
        volatile boolean cancelled = false;
        volatile boolean finalized = false;        // 계산 1회 가드 (취소/정상완료 중복 계산 방지)
        volatile int current = 0;
        volatile int total = 0;
        volatile int percent = 0;                 // 티어 가중 진행률 0~100
        volatile String collectingName;
        volatile com.madfinder.server.dto.RecommendResponse result;
        volatile String message;

        Job(String jobId, Long userId) {
            this.jobId = jobId;
            this.userId = userId;
        }
    }
}
