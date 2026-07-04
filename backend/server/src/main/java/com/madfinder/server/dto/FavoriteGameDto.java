package com.madfinder.server.dto;

/** 즐겨찾기 목록의 게임 1개 (GET /api/users/{username}/favorites). */
public record FavoriteGameDto(
        Long universeId,
        String name,
        String iconUrl
) {
}
