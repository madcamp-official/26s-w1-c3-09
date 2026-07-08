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

    // 전부 rate_governance.json에서 (하드코딩 금지 원칙 — 배치 python과 같은 필드)
    private final int detailBatch;      // operations.getGameDetails.batchSize (실측 50)
    private final int iconBatch;        // operations.getGameIcons.batchSize (실측 100)
    private final int assetBatch;       // operations.getAssetThumbnails.batchSize (100)
    private final int memberPage;       // operations.getGroupMembers.batchSize (페이지당 100)
    private final int favPage;          // operations.getFavorites.pageSize (응답 최대 50)
    private final int favMaxPages;      // operations.getFavorites.maxPages (커서 순회 상한)
    private final int maxRetries;       // defaults.http.maxRetries
    private final long backoff429Ms;    // defaults.aimd.backoffSeconds (429 대기 — 배치와 동일)
    private final long serverErrorMs;   // defaults.http.serverErrorRetryDelaySeconds
    private final long networkErrorMs;  // defaults.http.networkErrorRetryDelaySeconds

    public RobloxApiClient(RateLaneManager lanes,
                           com.madfinder.server.config.RateGovernance governance) {
        this.lanes = lanes;
        this.detailBatch = governance.batchSize("getGameDetails");
        this.iconBatch = governance.batchSize("getGameIcons");
        this.assetBatch = governance.batchSize("getAssetThumbnails");
        this.memberPage = governance.batchSize("getGroupMembers");
        this.favPage = governance.pageSize("getFavorites");
        this.favMaxPages = governance.maxPages("getFavorites");
        this.maxRetries = governance.defaults().http().maxRetries();
        this.backoff429Ms = (long) (governance.defaults().aimd().backoffSeconds() * 1000);
        this.serverErrorMs = (long) (governance.defaults().http().serverErrorRetryDelaySeconds() * 1000);
        this.networkErrorMs = (long) (governance.defaults().http().networkErrorRetryDelaySeconds() * 1000);
    }

    /** 닉네임 → userId 해석. 없는 닉네임이면 empty. (POST /v1/usernames/users) */
    public Optional<ResolvedUser> resolveUsername(String username) {
        acquireOrBusy(BUCKET_USERS);
        JsonNode body = exchange(() -> usersClient.post()
                .uri("/v1/usernames/users")
                .body(Map.of("usernames", List.of(username), "excludeBannedUsers", false))
                .retrieve()
                .body(String.class), BUCKET_USERS);
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

    /**
     * 유저 즐겨찾기 전체 조회 — nextPageCursor를 따라 마지막 페이지까지 순회 (S8).
     * 50개 넘는 유저도 전부 가져옴. 남용 방지로 최대 favMaxPages(config)까지만.
     * 비공개/오류 구분 위해 실패는 예외(첫 페이지 기준).
     */
    public List<FavoriteGame> fetchFavorites(long userId) {
        List<FavoriteGame> favorites = new java.util.ArrayList<>();
        String cursor = null;
        for (int page = 0; page < favMaxPages; page++) {
            acquireOrBusy(BUCKET_FAV);
            final String c = cursor;
            JsonNode body = exchange(() -> gamesClient.get()
                    .uri(b -> b.path("/v2/users/{userId}/favorite/games")
                            .queryParam("limit", favPage)
                            .queryParamIfPresent("cursor", java.util.Optional.ofNullable(c))
                            .build(userId))
                    .retrieve()
                    .body(String.class), BUCKET_FAV);
            for (JsonNode g : body.path("data")) {
                favorites.add(new FavoriteGame(g.path("id").asLong(), g.path("name").asText()));
            }
            JsonNode next = body.path("nextPageCursor");
            if (next.isNull() || next.asText("").isEmpty()) {
                break;   // 마지막 페이지 (커서 없음)
            }
            cursor = next.asText();
        }
        return favorites;
    }

    /** 게임명 검색 (omni-search, 실측 A-3). 예산 소진 시 BUSY — 유저 대면 실시간. */
    public List<SearchResult> searchGames(String query) {
        acquireOrBusy("apis_search");
        JsonNode body = exchange(() -> apisClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search-api/omni-search")
                        .queryParam("searchQuery", query)
                        .queryParam("pageType", "all")
                        .queryParam("sessionId", java.util.UUID.randomUUID().toString())
                        .build())
                .retrieve()
                .body(String.class), "apis_search");
        List<SearchResult> results = new java.util.ArrayList<>();
        for (JsonNode group : body.path("searchResults")) {
            for (JsonNode c : group.path("contents")) {
                long universeId = c.path("universeId").asLong(0);
                if (universeId == 0 || c.path("isSponsored").asBoolean(false)) {
                    continue;   // 게임 아닌 콘텐츠(검색어 제안 등)·광고 제외
                }
                results.add(new SearchResult(universeId, c.path("name").asText(""),
                        c.path("playerCount").asInt(0)));
            }
        }
        return results;
    }

    public record SearchResult(long universeId, String name, int playerCount) {
    }

    // ---- 표시 데이터 즉석 채움용 (realtime 레인, 블로킹) — 추천 후보·비슷한게임·스크린샷 ----

    private final RestClient thumbsClient = RestClient.create("https://thumbnails.roblox.com");
    private final RestClient apisClient = RestClient.create("https://apis.roblox.com");

    /** 게임 상세 배치 (universeIds → detail JsonNode 목록). batchSize씩 묶어 호출. */
    public List<JsonNode> fetchGameDetailsRealtime(List<Long> universeIds) throws InterruptedException {
        List<JsonNode> out = new java.util.ArrayList<>();
        for (int i = 0; i < universeIds.size(); i += detailBatch) {
            List<Long> chunk = universeIds.subList(i, Math.min(i + detailBatch, universeIds.size()));
            lanes.acquireRealtimeBlocking("games_detail");
            String ids = chunk.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
            JsonNode body = exchangeBlocking(() -> gamesClient.get()
                    .uri("/v1/games?universeIds=" + ids).retrieve().body(String.class), "games_detail", false);
            if (body != null) {
                for (JsonNode g : body.path("data")) {
                    out.add(g);
                }
            }
        }
        return out;
    }

    /** 게임 아이콘 배치 (universeId → iconUrl). batchSize씩. */
    public Map<Long, String> fetchGameIconsRealtime(List<Long> universeIds) throws InterruptedException {
        Map<Long, String> out = new java.util.HashMap<>();
        for (int i = 0; i < universeIds.size(); i += iconBatch) {
            List<Long> chunk = universeIds.subList(i, Math.min(i + iconBatch, universeIds.size()));
            lanes.acquireRealtimeBlocking("thumb_icon");
            String ids = chunk.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
            JsonNode body = exchangeBlocking(() -> thumbsClient.get()
                    .uri("/v1/games/icons?universeIds=" + ids + "&size=256x256&format=Png")
                    .retrieve().body(String.class), "thumb_icon", false);
            if (body != null) {
                for (JsonNode t : body.path("data")) {
                    if ("Completed".equals(t.path("state").asText()) && !t.path("imageUrl").isNull()) {
                        out.put(t.path("targetId").asLong(), t.path("imageUrl").asText());
                    }
                }
            }
        }
        return out;
    }

    /** 스크린샷 에셋 image_id → 표시 URL (실측: /v1/assets, 180일 유효). batchSize씩. */
    public Map<Long, String> fetchAssetThumbnailsRealtime(List<Long> imageIds) throws InterruptedException {
        Map<Long, String> out = new java.util.HashMap<>();
        for (int i = 0; i < imageIds.size(); i += assetBatch) {
            List<Long> chunk = imageIds.subList(i, Math.min(i + assetBatch, imageIds.size()));
            lanes.acquireRealtimeBlocking("thumb_thumbnail");
            String ids = chunk.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
            JsonNode body = exchangeBlocking(() -> thumbsClient.get()
                    .uri("/v1/assets?assetIds=" + ids + "&size=768x432&format=Png")
                    .retrieve().body(String.class), "thumb_thumbnail", false);
            if (body != null) {
                for (JsonNode t : body.path("data")) {
                    if ("Completed".equals(t.path("state").asText()) && !t.path("imageUrl").isNull()) {
                        out.put(t.path("targetId").asLong(), t.path("imageUrl").asText());
                    }
                }
            }
        }
        return out;
    }

    /** 연쇄추천(People-Also-Join, C-1) — universeId → 연관 게임 universeId 목록(rank 순, 최대 6).
     *  realtime 레인(games_rec). 장르·설명은 빈 값으로 오므로 표시 상세는 별도 backfill 필요. 실패 시 빈 목록. */
    public List<Long> fetchRecommendationsRealtime(long universeId) throws InterruptedException {
        lanes.acquireRealtimeBlocking("games_rec");
        JsonNode body = exchangeBlocking(() -> gamesClient.get()
                .uri("/v1/games/recommendations/game/{uid}?maxRows=6", universeId)
                .retrieve().body(String.class), "games_rec", false);
        if (body == null) {
            return List.of();
        }
        List<Long> ids = new java.util.ArrayList<>();
        for (JsonNode g : body.path("games")) {
            long id = g.path("universeId").asLong();
            if (id > 0 && id != universeId) {
                ids.add(id);
            }
        }
        return ids;
    }

    // ---- 정밀모드(precise 레인) 전용 — 블로킹 대기 + 429 재시도 (백그라운드 잡용) ----

    /** 그룹 멤버 한 페이지(Asc, batchSize명). ok=false → 비공개/차단(빈 페이지와 구분). */
    public MemberPage fetchGroupMembersPrecise(long groupId, String cursor) throws InterruptedException {
        lanes.acquirePreciseBlocking(BUCKET_MEMBERS);
        String url = "/v1/groups/" + groupId + "/users?limit=" + memberPage + "&sortOrder=Asc"
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
                        .uri("/v2/users/{userId}/favorite/games?limit={limit}", userId, favPage)
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

    /** 블로킹 호출: 429는 백오프 후 해당 레인 재획득, 5xx/네트워크는 지연 재시도 (수치: defaults.http/aimd). 실패 시 null. */
    private JsonNode exchangeBlocking(ThrowingSupplier call, String bucket, boolean preciseLane)
            throws InterruptedException {
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                return mapper.readTree(call.get());
            } catch (org.springframework.web.client.HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429) {
                    Thread.sleep(backoff429Ms);
                    if (preciseLane) {
                        lanes.acquirePreciseBlocking(bucket);
                    } else {
                        lanes.acquireRealtimeBlocking(bucket);
                    }
                    continue;
                }
                return null;   // 403/404 등 — 비공개 그룹/유저
            } catch (org.springframework.web.client.HttpServerErrorException e) {
                Thread.sleep(serverErrorMs);
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                Thread.sleep(networkErrorMs);
            }
        }
        return null;
    }

    private JsonNode exchangePrecise(ThrowingSupplier call, String bucket) throws InterruptedException {
        return exchangeBlocking(call, bucket, true);
    }

    public record MemberPage(List<Long> userIds, String nextCursor, boolean ok) {
    }

    // ---- 내부 공통 ----

    private void acquireOrBusy(String bucket) {
        if (!lanes.tryAcquire(bucket)) {
            long waitMs = Math.max(1, (long) Math.ceil(lanes.nextAvailableMillis(bucket) / 1000.0));
            throw ApiException.busy("로블록스 호출 예산 초과 — 약 " + waitMs + "s 후 재시도해 주세요");
        }
    }

    private JsonNode exchange(ThrowingSupplier call, String bucket) {
        try {
            String raw = call.get();
            return mapper.readTree(raw);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode().equals(HttpStatusCode.valueOf(429))) {
                log.warn("로블록스 429 (버킷 {}) — {}초 쿨다운 시작 (A-2: 재시도 금지)", bucket, 30);
                lanes.reportRobloxRateLimited(bucket);
                long sec = (long) Math.ceil(lanes.nextAvailableMillis(bucket) / 1000.0);
                throw ApiException.busy("로블록스 rate limit: 약 " + sec + "초 후 다시 시도해주세요.");
                // throw ApiException.busy("로블록스 rate limit — 잠시 후 재시도해 주세요");
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
