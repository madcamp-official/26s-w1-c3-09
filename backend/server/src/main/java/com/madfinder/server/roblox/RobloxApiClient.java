package com.madfinder.server.roblox;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.madfinder.server.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 로블록스 실시간 호출 래퍼 (F-1: 실시간은 username 해석 + fav 조회 둘뿐).
 * 모든 호출은 RateLaneManager 허가 후에만 나감. 예산 소진 시 BUSY(429) 응답.
 * 429 수신 시 재시도 없이 즉시 실패 — A-2: 밀어붙이면 페널티 30초+.
 *
 * 버킷 매핑 (rate_governance.json):
 *  - users_lookup: GET /v1/users/{id} + POST /v1/usernames/users + POST /v1/users (셋이 공유 — 실측)
 *  - games_fav:    GET /v2/users/{id}/favorite/games
 */
@Component
public class RobloxApiClient {

    private static final Logger log = LoggerFactory.getLogger(RobloxApiClient.class);
    private static final String BUCKET_USERS = "users_lookup";
    private static final String BUCKET_FAV = "games_fav";

    private final RestClient usersClient = RestClient.create("https://users.roblox.com");
    private final RestClient gamesClient = RestClient.create("https://games.roblox.com");
    private final ObjectMapper mapper = new ObjectMapper();
    private final RateLaneManager lanes;

    public RobloxApiClient(RateLaneManager lanes) {
        this.lanes = lanes;
    }

    /** 닉네임 → userId 해석. 없는 닉네임이면 empty. (POST /v1/usernames/users) */
    public Optional<ResolvedUser> resolveUsername(String username) {
        acquireOrBusy(BUCKET_USERS);
        JsonNode body = exchange(() -> usersClient.post()
                .uri("/v1/usernames/users")
                .body(Map.of("usernames", List.of(username), "excludeBannedUsers", false))
                .retrieve()
                .body(String.class));
        JsonNode data = body.path("data");
        if (data.isEmpty()) {
            return Optional.empty();
        }
        JsonNode u = data.get(0);
        return Optional.of(new ResolvedUser(
                u.path("id").asLong(),
                u.path("name").asText(),
                u.path("displayName").asText(null)));
    }

    /** 유저 즐겨찾기 조회 (1페이지 50개). 비공개/오류 구분 위해 실패는 예외. */
    public List<FavoriteGame> fetchFavorites(long userId) {
        acquireOrBusy(BUCKET_FAV);
        JsonNode body = exchange(() -> gamesClient.get()
                .uri("/v2/users/{userId}/favorite/games?limit=50", userId)
                .retrieve()
                .body(String.class));
        List<FavoriteGame> favorites = new java.util.ArrayList<>();
        for (JsonNode g : body.path("data")) {
            favorites.add(new FavoriteGame(g.path("id").asLong(), g.path("name").asText()));
        }
        return favorites;
    }

    // ---- 내부 공통 ----

    private void acquireOrBusy(String bucket) {
        if (!lanes.tryAcquire(bucket)) {
            long waitMs = lanes.nextAvailableMillis(bucket);
            throw ApiException.busy("로블록스 호출 예산 초과 — 약 " + waitMs + "ms 후 재시도해 주세요");
        }
    }

    private JsonNode exchange(ThrowingSupplier call) {
        try {
            String raw = call.get();
            return mapper.readTree(raw);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode().equals(HttpStatusCode.valueOf(429))) {
                log.warn("로블록스 429 — 즉시 실패 (A-2: 재시도 금지)");
                throw ApiException.busy("로블록스 rate limit — 잠시 후 재시도해 주세요");
            }
            throw ApiException.robloxError("로블록스 응답 오류: " + e.getStatusCode());
        } catch (Exception e) {
            log.error("로블록스 호출 실패", e);
            throw ApiException.robloxError("로블록스 호출에 실패했습니다");
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        String get() throws Exception;
    }

    public record ResolvedUser(long userId, String username, String displayName) {
    }

    public record FavoriteGame(long universeId, String name) {
    }
}
