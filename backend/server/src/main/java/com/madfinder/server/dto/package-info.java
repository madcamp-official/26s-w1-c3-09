/**
 * 요청/응답 JSON 계약 (record). docs/specs/백엔드-api-명세.md 와 1:1.
 * 엔티티를 직접 노출하지 않기 위한 외부용 형태 — 화면 변경은 여기만 수정.
 * 구성: UserFavoritesResponse(+FavoriteGameDto), SearchResponse, TierSaveRequest/Response(+TierEntryDto),
 *       RecommendRequest/Response(두 섹션), RecommendStatusResponse(정밀모드 폴링),
 *       GameDetailResponse, GameVideosResponse, SimilarGamesResponse, ErrorResponse
 */
package com.madfinder.server.dto;
