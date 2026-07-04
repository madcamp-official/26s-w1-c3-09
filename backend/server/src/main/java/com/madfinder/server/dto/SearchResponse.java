package com.madfinder.server.dto;

import java.util.List;

/** GET /api/search?q= 응답 (상위 10개). */
public record SearchResponse(List<Result> results) {

    public record Result(
            Long universeId,
            String name,
            Integer playerCount,
            String iconUrl
    ) {
    }
}
