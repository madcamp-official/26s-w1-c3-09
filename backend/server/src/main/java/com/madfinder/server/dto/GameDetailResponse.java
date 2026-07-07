package com.madfinder.server.dto;

import java.util.List;

/**
 * GET /api/games/{universeId} 응답 (게임 상세 페이지).
 * videoUrl은 항상 null (확정: 개발자 영상 안 씀 — 영상은 /videos 유튜브 쇼츠가 담당).
 * screenshots는 media 백필된 게임이면 URL 배열, 아니면 빈 배열.
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
        Integer releasedYear,   // games.created의 연도 (이미 저장돼 있음 — 추가 비용 0)
        List<String> screenshots,
        String videoUrl,
        String robloxUrl
) {
}
