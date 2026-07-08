package com.madfinder.server.roblox;

import com.madfinder.server.config.RateGovernance;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 엔드포인트(버킷)별 rate 예산 관리 (backend/config/rate_governance.json 로드본 사용).
 *
 * 구조 (G-6): 버킷 = 로블록스가 막는 단위(엔드포인트별 독립, users 계열만 공유 — 실측).
 * 레인 = 우리 정책상 분배:
 *   realtime — 유저 요청 즉시 처리. floor(최소 보장)만큼의 전용 버킷. tryAcquire(비대기, 소진 시 BUSY).
 *   precise  — 정밀모드 백그라운드 수집. 몫 = 가용치 − 타 레인 floor 합 (배치 rate_limiter와 같은 계산).
 *              블로킹 대기(acquirePreciseBlocking) — 잡 스레드라 기다려도 됨.
 *
 * 정직한 한계: EC2 야간 cron 배치와 precise가 같은 시간대에 돌면 둘이 같은 몫을 계산해
 * 합계가 상한을 넘을 수 있음 → 429 → 배치 AIMD가 물러나며 자기교정. 완전한 중앙 조정(A2)은 후속.
 */
@Component
public class RateLaneManager {

    private final Map<String, TokenBucket> realtimeBuckets = new ConcurrentHashMap<>();
    private final Map<String, TokenBucket> preciseBuckets = new ConcurrentHashMap<>();
    private final Map<String, Long> cooldownUntil = new ConcurrentHashMap<>();
    private static final long ROBLOX_PENALTY_MS = 30_000;

    public RateLaneManager(RateGovernance governance) {
        double defaultMargin = governance.defaults().margin();
        double burst = governance.defaults().burstOrDefault();
        governance.buckets().forEach((name, bucket) -> {
            double margin = bucket.margin() != null ? bucket.margin() : defaultMargin;
            double usable = bucket.ratePerS() * (1.0 - margin);

            // realtime 레인: floor 있으면 그만큼, 없으면 가용 전체 (그 버킷의 유일 소비자란 뜻)
            Double floor = bucket.realtimeFloor();
            double rtRate = floor != null ? floor : usable;
            realtimeBuckets.put(name, new TokenBucket(rtRate, Math.max(1.0, rtRate * burst)));

            // precise 레인: config에 precise가 정의된 버킷만.
            //  - activeShare 있으면(B안): 그 값으로 캡. 정밀 도는 동안 배치가 system_heartbeat를 보고
            //    같은 몫(activeShare)을 차감 → 경합 0. 정밀 유휴 시 배치가 그 몫을 되찾음.
            //  - 없으면: 가용 − 타 레인 floor 합 (work-conserving 폴백).
            if (bucket.lanes() != null && bucket.lanes().containsKey("precise")) {
                Double activeShare = bucket.lanes().get("precise").activeShare();
                double pRate;
                if (activeShare != null) {
                    pRate = activeShare;
                } else {
                    double reserved = bucket.lanes().values().stream()
                            .filter(l -> l.floor() != null)
                            .mapToDouble(RateGovernance.Lane::floor).sum();
                    pRate = Math.max(0.1, usable - reserved);
                }
                preciseBuckets.put(name, new TokenBucket(pRate, Math.max(1.0, pRate * burst)));
            }
        });
    }

    /** 실시간 허가 — 나올 때까지 블로킹 (추천 후보 즉석 채움 등 요청 스레드 내 소량 호출용). */
    public void acquireRealtimeBlocking(String bucketName) throws InterruptedException {
        TokenBucket bucket = require(realtimeBuckets, bucketName);
        while (!bucket.tryAcquire()) {
            Thread.sleep(Math.max(10, bucket.nextAvailableMillis()));
        }
    }

    /** 실시간 호출 허가 시도(비대기). false면 예산 소진 → 호출부가 BUSY 처리. */
    public boolean tryAcquire(String bucketName) {
        if (cooldownRemainingMs(bucketName) > 0) {
            return false;
        }
        return require(realtimeBuckets, bucketName).tryAcquire();
    }

    /** 다음 실시간 허가까지 대기 예상(ms). BUSY 안내용. */
    public long nextAvailableMillis(String bucketName) {
        return Math.max(cooldownRemainingMs(bucketName),
                require(realtimeBuckets, bucketName).nextAvailableMillis());
    }

    /** 정밀모드(백그라운드 잡) 허가 — 나올 때까지 블로킹 대기. */
    public void acquirePreciseBlocking(String bucketName) throws InterruptedException {
        TokenBucket bucket = require(preciseBuckets, bucketName);
        while (!bucket.tryAcquire()) {
            Thread.sleep(Math.max(10, bucket.nextAvailableMillis()));
        }
    }

    private TokenBucket require(Map<String, TokenBucket> map, String bucketName) {
        TokenBucket bucket = map.get(bucketName);
        if (bucket == null) {
            throw new IllegalArgumentException("rate_governance.json에 없는 버킷/레인: " + bucketName);
        }
        return bucket;
    }

    /** 로블록스가 직접 429를 반환함 — 이 버킷 전체를 페널티 시간만큼 잠금. */
    public void reportRobloxRateLimited(String bucketName) {
        cooldownUntil.put(bucketName, System.currentTimeMillis() + ROBLOX_PENALTY_MS);
    }

    public long cooldownRemainingMs(String bucketName) {
        Long until = cooldownUntil.get(bucketName);
        return until == null ? 0 : Math.max(0, until - System.currentTimeMillis());
    }
}
