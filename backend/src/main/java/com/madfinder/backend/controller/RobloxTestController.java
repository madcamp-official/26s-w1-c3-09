package com.madfinder.backend.controller;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class RobloxTestController {

    @GetMapping("/roblox/{userId}")
    public String testRobloxApi(@PathVariable("userId") Long userId) {
        // 1. 호출할 로블록스 유저 정보 API URL 주소 설정 (유저 ID 기준)
        String url = "https://users.roblox.com/v1/users/" + userId;

        // 2. 외부 API와 통신을 주고받는 스프링 내장 객체 생성
        RestTemplate restTemplate = new RestTemplate();

        try {
            // 3. 로블록스 서버로 GET 요청을 보내고, 결과(JSON)를 문자열(String) 형태로 통째로 받기
            String response = restTemplate.getForObject(url, String.class);
            return response;
        } catch (Exception e) {
            // 에러 발생 시 예외 메시지 출력
            return "로블록스 API 호출 실패: " + e.getMessage();
        }
    }

    // 2. [추가] 닉네임(문자열)으로 유저 ID와 정보를 검색하는 메서드
    @GetMapping("/roblox/username/{username}")
    public String testRobloxUsernameApi(@PathVariable("username") String username) {
        // 로블록스 닉네임 기반 검색 API 주소
        String url = "https://users.roblox.com/v1/usernames/users";
        RestTemplate restTemplate = new RestTemplate();

        try {
            // 헤더 설정 (JSON 형태로 데이터를 보낼 것이라고 명시)
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // 로블록스가 요구하는 바디 데이터 포맷 생성
            // { "usernames": ["유저닉네임"], "returnRequestByUsername": true }
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("usernames", Collections.singletonList(username));
            requestBody.put("returnRequestByUsername", true);

            // 헤더와 바디를 하나로 묶기
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // 로블록스 서버에 POST 요청 전송 및 결과 받기
            String response = restTemplate.postForObject(url, entity, String.class);
            return response;

        } catch (Exception e) {
            return "로블록스 닉네임 API 호출 실패: " + e.getMessage();
        }
    }

    // 3. [추가] 유저 고유 ID(숫자)를 받아 즐겨찾기한 게임 목록을 조회하는 메서드
    @GetMapping("/roblox/{userId}/favorites")
    public String testRobloxFavoritesApi(
            @PathVariable("userId") Long userId,
            @RequestParam(value = "pageCursor", required = false) String pageCursor) {

        // 로블록스 즐겨찾기 목록 조회 API 주소
        // assetTypeId=9 가 로블록스 내부적으로 '게임(Place)'을 뜻하는 고유 번호입니다.
        String url = "https://favorites.roblox.com/v1/users/" + userId + "/favorites/assets/9/list?limit=10";

        // 만약 다음 페이지 데이터가 있어서 커서(cursor)가 들어온다면 URL 뒤에 붙여줍니다.
        if (pageCursor != null && !pageCursor.isEmpty()) {
            url += "&cursor=" + pageCursor;
        }

        RestTemplate restTemplate = new RestTemplate();

        try {
            // 로블록스 서버로 GET 요청을 보내고 결과 받아오기
            String response = restTemplate.getForObject(url, String.class);
            return response;
        } catch (Exception e) {
            return "로블록스 즐겨찾기 API 호출 실패: " + e.getMessage();
        }
    }
}