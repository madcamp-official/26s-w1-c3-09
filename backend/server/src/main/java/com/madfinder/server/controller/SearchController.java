package com.madfinder.server.controller;

import com.madfinder.server.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/search?q= — 게임 이름 검색 (티어표 직접 추가용, 2페이지).
 * 로블록스 omni-search(apis 도메인, 2.8/s) 연동 필요.
 * TODO(KJH): RobloxApiClient에 search 추가 후 구현 — 그 전까지 501 반환 (프론트는 형식 참조: SearchResponse).
 */
@RestController
public class SearchController {

    @GetMapping("/api/search")
    public Object search(@RequestParam String q) {
        throw new ApiException(HttpStatus.NOT_IMPLEMENTED, "NOT_IMPLEMENTED",
                "검색은 아직 구현 전입니다 (로블록스 search 연동 대기)");
    }
}
