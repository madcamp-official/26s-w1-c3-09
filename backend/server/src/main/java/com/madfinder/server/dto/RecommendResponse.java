package com.madfinder.server.dto;

import java.util.List;

/**
 * POST /api/recommend · GET /api/recommendations/{userId} 응답 — 두 섹션(인기/발견).
 * 같은 raw 점수를 visits^alpha로 나누되 alpha가 다름(scoring.json sections).
 * 중복 제거 안 함 — 같은 게임이 양쪽에 뜰 수 있음(겹침은 alpha 튜닝의 문제).
 * 저장된 결과 없으면 두 리스트 다 [].
 */
public record RecommendResponse(Sections sections) {

    public record Sections(List<Item> popular, List<Item> discovery) {
    }

    public record Item(
            int rank,                             // 섹션 내 순위 (1부터)
            Long universeId,
            String name,
            String genreL1,
            String genreL2,
            double score,
            Integer playerCount,
            String iconUrl
    ) {
    }
}
