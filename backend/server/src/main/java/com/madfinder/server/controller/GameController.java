package com.madfinder.server.controller;

import com.madfinder.server.dto.GameDetailResponse;
import com.madfinder.server.dto.GameVideosResponse;
import com.madfinder.server.service.GameService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/games/{universeId} — 게임 상세 (4페이지).
 * GET /api/games/{universeId}/videos — 유튜브 영상 캐시 (개발자 영상 폴백).
 */
@RestController
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping("/api/games/{universeId}")
    public GameDetailResponse detail(@PathVariable Long universeId) {
        return gameService.getDetail(universeId);
    }

    @GetMapping("/api/games/{universeId}/videos")
    public GameVideosResponse videos(@PathVariable Long universeId) {
        return gameService.getVideos(universeId);
    }

    @GetMapping("/api/games/{universeId}/similar")
    public com.madfinder.server.dto.SimilarGamesResponse similar(@PathVariable Long universeId) {
        return gameService.getSimilar(universeId);
    }
}
