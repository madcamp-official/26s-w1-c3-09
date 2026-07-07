package com.madfinder.server.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * GET /api/recommend/status/{jobId} 응답 (정밀모드 폴링).
 * running: progress만 / finalizing: (마무리·계산 중, message에 안내) / done: sections만 / error: message만.
 * finalizing = 취소 후 현재 게임 마무리 중 또는 수집 완료 후 추천 계산 중 (null 필드는 생략).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecommendStatusResponse(
        String status,                              // "running" | "finalizing" | "done" | "error"
        Progress progress,
        RecommendResponse.Sections sections,        // POST /api/recommend와 동일 형태
        String message
) {

    /** percent = 티어 중요도 가중 진행률(0~100). current/total은 게임 개수 표시용. */
    public record Progress(int current, int total, String collectingName, int percent) {
    }

    /** POST /api/recommend (mode=precise) 즉시 응답 */
    public record JobAccepted(String jobId, String status) {
        public static JobAccepted of(String jobId) {
            return new JobAccepted(jobId, "accepted");
        }
    }
}
