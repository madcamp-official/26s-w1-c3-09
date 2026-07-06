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
    private static final String BUCKET_MEMBERS = "groups_members";

    private final RestClient usersClient = RestClient.create("https://users.roblox.com");
    private final RestClient gamesClient = RestClient.create("https://games.roblox.com");
    private final RestClient groupsClient = RestClient.create("https://groups.roblox.com");
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

    // ---- 정밀모드(precise 레인) 전용 — 블로킹 대기 + 429 재시도 (백그라운드 잡용) ----

    /** 그룹 멤버 한 페이지(Asc 100명). ok=false → 비공개/차단(빈 페이지와 구분). */
    public MemberPage fetchGroupMembersPrecise(long groupId, String cursor) throws InterruptedException {
        lanes.acquirePreciseBlocking(BUCKET_MEMBERS);
        String url = "/v1/groups/" + groupId + "/users?limit=100&sortOrder=Asc"
                + (cursor != null ? "&cursor=" + cursor : "");
        JsonNode body = exchangePrecise(() -> groupsClient.get().uri(url).retrieve().body(String.class),
                BUCKET_MEMBERS);
        if (body == null) {
            return new MemberPage(List.of(), null, false);
        }
        List<Long> ids = new java.util.ArrayList<>();
        for (JsonNode m : body.path("data")) {
            ids.add(m.path("user").path("userId").asLong());
        }
        String next = body.path("nextPageCursor").isNull() ? null : body.path("nextPageCursor").asText(null);
        return new MemberPage(ids, next, true);
    }

    /** 유저 즐겨찾기 (정밀 수집용). null=조회실패(비공개 등), 빈 리스트=즐겨찾기 없음. */
    public List<Long> fetchFavoriteIdsPrecise(long userId) throws InterruptedException {
        lanes.acquirePreciseBlocking(BUCKET_FAV);
        JsonNode body = exchangePrecise(() -> gamesClient.get()
                        .uri("/v2/users/{userId}/favorite/games?limit=50", userId)
                        .retrieve().body(String.class),
                BUCKET_FAV);
        if (body == null) {
            return null;
        }
        List<Long> ids = new java.util.ArrayList<>();
        for (JsonNode g : body.path("data")) {
            long id = g.path("id").asLong();
            if (id > 0) {
                ids.add(id);
            }
        }
        return ids;
    }

    /** 정밀용 호출: 429는 3초 쉬고 재획득(최대 4회), 5xx는 2초 재시도. 실패 시 null (예외 아님). */
    private JsonNode exchangePrecise(ThrowingSupplier call, String bucket) throws InterruptedException {
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                return mapper.readTree(call.get());
            } catch (org.springframework.web.client.HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429) {
                    Thread.sleep(3000);
                    lanes.acquirePreciseBlocking(bucket);
                    continue;
                }
                return null;   // 403/404 등 — 비공개 그룹/유저
            } catch (org.springframework.web.client.HttpServerErrorException e) {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                Thread.sleep(1000);
            }
        }
        return null;
    }

    public record MemberPage(List<Long> userIds, String nextCursor, boolean ok) {
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
