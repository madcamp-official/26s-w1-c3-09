package com.madfinder.server.controller;

import com.madfinder.server.dto.RecommendRequest;
import com.madfinder.server.dto.RecommendResponse;
import com.madfinder.server.service.RecommendService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/recommend — 추천 계산 실행 (2→3페이지, 결과 저장 후 반환).
 * GET /api/recommendations/{userId} — 마지막 결과 재조회 (재계산 없음).
 */
@RestController
public class RecommendController {

    private final RecommendService recommendService;

    public RecommendController(RecommendService recommendService) {
        this.recommendService = recommendService;
    }

    @PostMapping("/api/recommend")
    public RecommendResponse recommend(@RequestBody RecommendRequest request) {
        return recommendService.compute(request.userId());
    }

    @GetMapping("/api/recommendations/{userId}")
    public RecommendResponse saved(@PathVariable Long userId) {
        return recommendService.getSaved(userId);
    }
}
