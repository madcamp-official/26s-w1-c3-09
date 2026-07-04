package com.madfinder.server.roblox;

/**
 * 단일 엔드포인트 버킷의 토큰버킷 rate 제어.
 * 실측 근거(A-1): 로블록스 rate limit은 엔드포인트별 독립 토큰버킷.
 * 여유(margin)를 뺀 가용 rate로 재충전 — 429 페널티(30초+, A-2) 예방이 목적.
 */
public class TokenBucket {

    private final double ratePerSec;   // 가용 rate = 실측 × (1 - margin)
    private final double capacity;     // 버스트 허용량 (기본: 2초치)
    private double tokens;
    private long lastRefillNanos;

    public TokenBucket(double ratePerSec) {
        this(ratePerSec, Math.max(1.0, ratePerSec * 2));
    }

    public TokenBucket(double ratePerSec, double capacity) {
        this.ratePerSec = ratePerSec;
        this.capacity = capacity;
        this.tokens = capacity;
        this.lastRefillNanos = System.nanoTime();
    }

    /** 토큰 1개 획득 시도. 성공 시 즉시 호출 가능. */
    public synchronized boolean tryAcquire() {
        refill();
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    /** 다음 토큰까지 대기 예상(ms). 대기열 표시·백오프 판단용. */
    public synchronized long nextAvailableMillis() {
        refill();
        if (tokens >= 1.0) {
            return 0;
        }
        return (long) Math.ceil((1.0 - tokens) / ratePerSec * 1000);
    }

    private void refill() {
        long now = System.nanoTime();
        double elapsedSec = (now - lastRefillNanos) / 1_000_000_000.0;
        tokens = Math.min(capacity, tokens + elapsedSec * ratePerSec);
        lastRefillNanos = now;
    }
}
