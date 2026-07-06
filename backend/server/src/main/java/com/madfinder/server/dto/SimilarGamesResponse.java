package com.madfinder.server.dto;

import java.util.List;

/** GET /api/games/{universeId}/similar — 그 게임과 함께 즐겨찾기된 상위 게임들 (4페이지). */
public record SimilarGamesResponse(List<Item> similar) {

    public record Item(
            Long universeId,
            String name,
            String genreL1,
            Integer playerCount,
            String iconUrl
    ) {
    }
}
