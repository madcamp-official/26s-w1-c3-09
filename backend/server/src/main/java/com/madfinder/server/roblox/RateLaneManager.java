package com.madfinder.server.roblox;

import com.madfinder.server.config.RateGovernance;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 엔드포인트(버킷)별 rate 예산 관리 (backend/config/rate_governance.json 로드본 사용).
 *
 * 구조 (F-4/F-5):
 *  - 버킷 = 로블록스가 실제로 막는 단위 (엔드포인트별 독립, 단 users 계열은 공유 — 실측)
 *  - 레인 = 우리 정책상 분배 (실시간 floor 예약 + 배치 work-conserving)
 *
 * 현재 구현: 버킷 단위 토큰버킷 + 실시간 레인의 floor 예약.
 * TODO(KJH): 배치(Python)와 예산 공유 시 서버/배치 간 분할 반영 필요
 *            (지금은 서버 실시간 몫만 이 클래스가 관리, 배치는 batch/common/rate_limiter.py).
 */
@Component
public class RateLaneManager {

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final RateGovernance governance;

    public RateLaneManager(RateGovernance governance) {
        this.governance = governance;
        governance.buckets().forEach((name, bucket) -> {
            double margin = bucket.margin() != null ? bucket.margin() : governance.defaults().margin();
            double usable = bucket.ratePerS() * (1.0 - margin);
            // 서버(실시간)는 실시간 레인 floor만 사용 — floor 없으면 가용 전체
            double realtimeRate = bucket.realtimeFloor() != null ? bucket.realtimeFloor() : usable;
            buckets.put(name, new TokenBucket(realtimeRate));
        });
    }

    /** 해당 버킷에서 호출 1건 허가 시도. false면 예산 소진 (호출하지 말 것). */
    public boolean tryAcquire(String bucketName) {
        TokenBucket bucket = require(bucketName);
        return bucket.tryAcquire();
    }

    /** 다음 허가까지 대기 예상(ms). BUSY 판단·안내용. */
    public long nextAvailableMillis(String bucketName) {
        return require(bucketName).nextAvailableMillis();
    }

    private TokenBucket require(String bucketName) {
        TokenBucket bucket = buckets.get(bucketName);
        if (bucket == null) {
            throw new IllegalArgumentException(
                    "rate_governance.json에 없는 버킷: " + bucketName);
        }
        return bucket;
    }
}
