package com.madfinder.server.dto;

import java.util.List;

/**
 * GET /api/users/{username}/favorites 응답.
 * favoritesEmpty=true → 즐겨찾기 0개/비공개 (에러 아님 — 직접 추가 유도).
 * savedTier=null → 저장된 티어표 없음 (신규 유저).
 */
public record UserFavoritesResponse(
        Long userId,
        String username,
        List<FavoriteGameDto> favorites,
        boolean favoritesEmpty,
        List<TierEntryDto> savedTier
) {
}
