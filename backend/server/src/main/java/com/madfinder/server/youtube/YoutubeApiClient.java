package com.madfinder.server.youtube;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * 유튜브 Data API v3 호출 래퍼 (F-10 쇼츠).
 * search.list는 호출당 100유닛(일 10,000 = 하루 약 100회) — 요청마다 부르면 반나절에 소진.
 * 쿼터 통제는 호출부(GameService)의 "게임당 1회 검색 후 캐시" 정책이 담당한다.
 * 키 미설정·호출 실패 시 빈 목록 반환 — 상세 페이지는 영상 없이도 떠야 한다.
 */
@Component
public class YoutubeApiClient {

    private static final Logger log = LoggerFactory.getLogger(YoutubeApiClient.class);

    private final RestClient client = RestClient.create("https://www.googleapis.com");
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final int maxResults;   // 게임당 검색 수 — scoring.json shortsPerGame (하드코딩 금지 원칙)

    public YoutubeApiClient(@Value("${GOOGLE_API_KEY:}") String apiKey,
                            com.madfinder.server.config.Scoring scoring) {
        this.apiKey = apiKey;
        this.maxResults = scoring.shortsPerGame();
    }

    /** "roblox {게임명} shorts" 세로 영상 검색 — 상위 5개 */
    public List<VideoResult> searchShorts(String gameName) {
        if (apiKey.isBlank()) {
            log.warn("GOOGLE_API_KEY 미설정 — 유튜브 검색 생략");
            return List.of();
        }
        try {
            String raw = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/youtube/v3/search")
                            .queryParam("part", "snippet")
                            .queryParam("type", "video")
                            .queryParam("videoDuration", "short")
                            .queryParam("maxResults", maxResults)
                            .queryParam("q", "roblox " + gameName + " shorts")
                            .queryParam("key", apiKey)
                            .build())
                    .retrieve()
                    .body(String.class);

            JsonNode body = mapper.readTree(raw);
            List<VideoResult> results = new ArrayList<>();
            for (JsonNode item : body.path("items")) {
                String videoId = item.path("id").path("videoId").asText(null);
                if (videoId == null) {
                    continue;   // 채널/플레이리스트가 섞여 올 때 방어
                }
                results.add(new VideoResult(
                        videoId,
                        item.path("snippet").path("title").asText(""),
                        item.path("snippet").path("thumbnails").path("medium").path("url").asText(null)));
            }
            return results;
        } catch (Exception e) {
            log.warn("유튜브 검색 실패 (쿼터 초과 가능): {}", e.getMessage());
            return List.of();   // 실패해도 상세 페이지는 정상 응답 (F-10 예외 정책)
        }
    }

    /** 검색 결과 1건 — game_videos 한 행의 재료 */
    public record VideoResult(String videoId, String title, String thumbnailUrl) {
    }
}