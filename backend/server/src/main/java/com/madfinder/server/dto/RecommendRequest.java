package com.madfinder.server.dto;

/** POST /api/recommend 요청. mode: 생략/"normal"=즉시(DB만), "precise"=정밀(잡 시작→폴링). */
public record RecommendRequest(Long userId, String mode) {

    public boolean isPrecise() {
        return "precise".equalsIgnoreCase(mode);
    }
}
