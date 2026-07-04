package com.madfinder.server.service;

/**
 * 로블록스 API 실시간 호출 전담. (담당: BMS)
 * 엔드포인트 상세: docs/KJH/api-명세.md (A-1, A-3, C-1, B-1~B-3, F-1~F-3)
 * 필수 규칙:
 *   - 전역 동시성 세마포어 (상한 50 — 배치 몫 50과 합쳐 서버 IP 예산 100 이내)
 *   - C-1은 간헐 500 → 2초 간격 2~3회 재시도
 *   - User-Agent 헤더 포함
 * TODO(BMS): RestTemplate/WebClient 기반 구현.
 */
public class RobloxApiClient {
}
