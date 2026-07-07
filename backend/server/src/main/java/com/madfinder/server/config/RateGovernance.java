package com.madfinder.server.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * backend/config/rate_governance.json 매핑 (필드 설명: backend/config/README.md).
 * ratePerS는 실측값 — 재측정 시 json의 그 필드만 갱신하면 정책(lanes·margin)은 안 덮인다.
 *
 *  - buckets: 로블록스가 실제로 막는 단위 (엔드포인트별 독립 토큰버킷, A-1)
 *  - operations: 논리 작업 → 어느 버킷을 쓰는지 + 실측 배치 상한 (batchSize/pageSize)
 *  - defaults.http: 블로킹 호출 재시도 정책 (배치 python과 같은 값 — 진실의 원천 하나)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RateGovernance(
        Defaults defaults,
        Map<String, Bucket> buckets,
        Map<String, Operation> operations
) {

    /** operations.{name}.batchSize — 멀티겟 묶음 크기(실측 상한). 없으면 설정 오류로 즉시 실패. */
    public int batchSize(String operation) {
        Operation op = operations.get(operation);
        if (op == null || op.batchSize() == null) {
            throw new IllegalStateException("rate_governance.json operations." + operation + ".batchSize 없음");
        }
        return op.batchSize();
    }

    /** operations.{name}.pageSize — 페이지형 응답 최대 개수. */
    public int pageSize(String operation) {
        Operation op = operations.get(operation);
        if (op == null || op.pageSize() == null) {
            throw new IllegalStateException("rate_governance.json operations." + operation + ".pageSize 없음");
        }
        return op.pageSize();
    }

    /** operations.{name}.maxPages — 커서 순회 최대 페이지 수(남용 방지). */
    public int maxPages(String operation) {
        Operation op = operations.get(operation);
        if (op == null || op.maxPages() == null) {
            throw new IllegalStateException("rate_governance.json operations." + operation + ".maxPages 없음");
        }
        return op.maxPages();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Defaults(double margin, Double burstSeconds, Aimd aimd, Http http) {

        /** 토큰버킷 버스트 용량(초) — 없으면 2초 */
        public double burstOrDefault() {
            return burstSeconds != null ? burstSeconds : 2.0;
        }
    }

    /** 배치 AIMD 파라미터 — 서버는 429 백오프(backoffSeconds)만 공유해 쓴다 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Aimd(Double backoffSeconds) {
    }

    /** HTTP 재시도/타임아웃 — 배치 python(roblox_api.py)과 같은 json 필드 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Http(
            Integer maxRetries,
            Integer timeoutSeconds,
            Double serverErrorRetryDelaySeconds,
            Double networkErrorRetryDelaySeconds
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Bucket(
            Double ratePerS,          // 실측 지속 rate (호출/s)
            Double margin,            // 여유 (null이면 defaults.margin)
            Map<String, Lane> lanes,  // 레인 정책 (realtime/precise/batch — G-6)
            String note               // 측정 신뢰도 등 비고
    ) {
        /** 실시간 레인 floor. lanes.realtime.floor 없으면 null(=가용 전체 사용). */
        public Double realtimeFloor() {
            if (lanes == null) {
                return null;
            }
            Lane rt = lanes.get("realtime");
            return rt != null ? rt.floor() : null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Lane(
            Integer priority,       // 우선순위 (1=realtime > 2=precise > 3=batch)
            Double floor            // 최소 보장 rate (없으면 예약 없음)
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Operation(
            String bucket,          // 이 작업이 소비하는 버킷 이름
            String method,
            String path,
            Integer batchSize,      // 호출당 최대 ID 수 (1 = 단건형) — 실측 상한
            Integer pageSize,       // 페이지형 응답 최대 개수 (fav 50, members 100)
            Integer maxPages,       // 커서 순회 최대 페이지 (fav 전체 조회 남용 방지)
            String note
    ) {
    }
}
