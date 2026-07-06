package com.madfinder.server.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * GET /api/recommend/status/{jobId} 응답 (정밀모드 폴링).
 * running: progress만 / done: recommendations만 / error: message만 채워짐 (null 필드는 생략).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecommendStatusResponse(
        String status,                              // "running" | "done" | "error"
        Progress progress,
        List<RecommendResponse.Item> recommendations,
        String message
) {

    public record Progress(int current, int total, String collectingName) {
    }

    /** POST /api/recommend (mode=precise) 즉시 응답 */
    public record JobAccepted(String jobId, String status) {
        public static JobAccepted of(String jobId) {
            return new JobAccepted(jobId, "accepted");
        }
    }
}
