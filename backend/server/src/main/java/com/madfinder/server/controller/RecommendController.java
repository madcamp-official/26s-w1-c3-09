package com.madfinder.server.controller;

import com.madfinder.server.dto.RecommendRequest;
import com.madfinder.server.dto.RecommendResponse;
import com.madfinder.server.dto.RecommendStatusResponse;
import com.madfinder.server.service.PreciseRecommendService;
import com.madfinder.server.service.RecommendService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/recommend — mode 생략/"normal": 즉시 계산(DB만) / "precise": 정밀 잡 시작 → jobId.
 * GET /api/recommend/status/{jobId} — 정밀모드 진행률/결과 폴링 (계약: 백엔드-api-명세.md).
 * GET /api/recommendations/{userId} — 마지막 결과 재조회 (재계산 없음).
 */
@RestController
public class RecommendController {

    private final RecommendService recommendService;
    private final PreciseRecommendService preciseRecommendService;

    public RecommendController(RecommendService recommendService,
                               PreciseRecommendService preciseRecommendService) {
        this.recommendService = recommendService;
        this.preciseRecommendService = preciseRecommendService;
    }

    @PostMapping("/api/recommend")
    public Object recommend(@RequestBody RecommendRequest request) {
        if (request.isPrecise()) {
            return RecommendStatusResponse.JobAccepted.of(
                    preciseRecommendService.start(request.userId()));
        }
        return recommendService.compute(request.userId());
    }

    @GetMapping("/api/recommend/status/{jobId}")
    public RecommendStatusResponse status(@PathVariable String jobId) {
        return preciseRecommendService.status(jobId);
    }

    /** 정밀 분석 중단 — 지금까지 수집한 것만으로 추천 계산 (현재 게임은 마무리됨). */
    @PostMapping("/api/recommend/cancel/{jobId}")
    public java.util.Map<String, String> cancel(@PathVariable String jobId) {
        preciseRecommendService.cancel(jobId);
        return java.util.Map.of("status", "cancelling");
    }

    @GetMapping("/api/recommendations/{userId}")
    public RecommendResponse saved(@PathVariable Long userId) {
        return recommendService.getSaved(userId);
    }
}
