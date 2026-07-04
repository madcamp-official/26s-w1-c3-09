package com.madfinder.server.controller;

import com.madfinder.server.dto.UserFavoritesResponse;
import com.madfinder.server.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/users/{username}/favorites — 닉네임으로 유저 확인 + 즐겨찾기 + 저장된 티어표 (1페이지).
 * refresh=true: 캐시 무시하고 로블록스 재조회 (F-3: 접속당 1회 새로고침 버튼 — 횟수 제한은 프론트).
 */
@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/api/users/{username}/favorites")
    public UserFavoritesResponse getFavorites(@PathVariable String username,
                                              @RequestParam(defaultValue = "false") boolean refresh) {
        return userService.getFavoritesByUsername(username, refresh);
    }
}
