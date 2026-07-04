package com.madfinder.server.dto;

/** 에러 응답 공통 형식: { "error": "코드", "message": "사람이 읽을 설명" }. */
public record ErrorResponse(
        String error,
        String message
) {
}
