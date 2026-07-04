package com.madfinder.server.dto;

import java.util.List;

/**
 * POST /api/recommend · GET /api/recommendations/{userId} 응답.
 * genreL1은 프론트 그룹핑용. 저장된 결과 없으면 recommendations=[].
 */
public record RecommendResponse(List<Item> recommendations) {

    public record Item(
            int rank,
            Long universeId,
            String name,
            String genreL1,
            double score,
            Integer playerCount,
            String iconUrl
    ) {
    }
}
