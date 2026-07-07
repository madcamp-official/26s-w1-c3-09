package com.madfinder.server.dto;

/**
 * 티어표 항목 1개. tier ∈ SSS/A/B/C, position은 티어 내 왼쪽부터 1.
 * name·iconUrl은 응답 전용(저장 요청 땐 null로 와도 무시) — 재방문 시 티어 카드를 즐겨찾기 풀 없이도 렌더하기 위함.
 */
public record TierEntryDto(
        Long universeId,
        String tier,
        Integer position,
        String name,
        String iconUrl
) {
}
