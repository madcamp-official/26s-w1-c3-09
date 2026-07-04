package com.madfinder.server.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * backend/config/rate_governance.json 매핑 (필드 설명: backend/config/README.md).
 * measured(실측)와 policy(우리 정책)를 분리 — 재측정해도 정책이 안 덮이게.
 *
 *  - buckets: 로블록스가 실제로 막는 단위 (엔드포인트별 독립 토큰버킷, A-1)
 *  - operations: 논리 작업 → 어느 버킷을 쓰는지 (users 계열처럼 여러 작업이 한 버킷 공유 가능)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RateGovernance(
        Defaults defaults,
        Map<String, Bucket> buckets,
        Map<String, Operation> operations
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Defaults(double margin) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Bucket(
            Double ratePerS,        // 실측 지속 rate (호출/s)
            Double margin,          // 여유 (null이면 defaults.margin)
            Double realtimeFloor,   // 실시간 레인 예약 바닥 (null이면 서버가 가용 전체 사용)
            String note             // 측정 신뢰도 등 비고
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Operation(
            String bucket,          // 이 작업이 소비하는 버킷 이름
            String method,
            String path,
            Integer batchSize,      // 호출당 최대 ID 수 (1 = 단건형)
            String note
    ) {
    }
}
