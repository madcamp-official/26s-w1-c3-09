package com.madfinder.server.dto;

import java.util.List;

/**
 * GET /api/games/{universeId} 응답 (게임 상세 페이지).
 * videoUrl은 만료 토큰 포함 CDN URL — 매 요청 새로 발급 (없으면 null).
 */
public record GameDetailResponse(
        Long universeId,
        String name,
        String description,
        String genreL1,
        String genreL2,
        Integer playing,
        Long visits,
        Integer upVotes,
        Integer downVotes,
        Integer minimumAge,
        List<String> screenshots,
        String videoUrl,
        String robloxUrl
) {
}
