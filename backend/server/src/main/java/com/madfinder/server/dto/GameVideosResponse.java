package com.madfinder.server.dto;

import java.util.List;

/** GET /api/games/{universeId}/videos 응답. 재생은 프론트에서 youtube.com/embed/{id}. */
public record GameVideosResponse(List<Video> videos) {

    public record Video(
            String youtubeVideoId,
            String title,
            String thumbnailUrl
    ) {
    }
}
