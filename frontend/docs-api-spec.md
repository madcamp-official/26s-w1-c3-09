# RBX Tierlist — Backend API 명세 (Spring Boot 연동용)

> 작성일: 2026-07-04 · 기준: 프론트엔드 MSW 목업 v1 (favorites / recommend / game 도메인)
> 프론트엔드의 `src/types/**`와 이 문서의 응답 스키마는 1:1로 일치해야 한다.
> MSW 핸들러 3개(`src/mocks/handlers/*.handlers.ts`)가 이 명세의 참조 구현이다.

---

## 1. 아키텍처 개요

```
[React FE] ──/api/*──> [Spring Boot] ──> Roblox Web API (users/games/thumbnails)
                          │      └────> YouTube Data API v3
                          └──> MySQL (games, game_relations, user_favorites,
                                       tier_lists, recommendations, game_videos ...)
```

- FE는 **우리 백엔드하고만** 통신한다. Roblox/YouTube 호출은 전부 백엔드가 프록시한다.
  (이유: Roblox API는 CORS를 허용하지 않아 브라우저에서 직접 호출 불가 + 레이트리밋/캐싱을 서버에서 통제)
- Roblox 데이터는 원본이 아니라 **캐시**다. 각 테이블의 `fetched_at`으로 TTL을 관리한다.

---

## 2. 사용하는 외부 API (Roblox)

> 신뢰도 표기 — ✅ 확인됨(공식 문서/최근 사용 사례), ⚠️ 요검증(구현 첫날 curl로 반드시 직접 확인)
> Roblox는 2024~2025년에 legacy 엔드포인트를 대거 정리 중이므로, ⚠️ 항목은 대체 경로까지 준비한다.
> 공식 legacy 문서: https://create.roblox.com/docs/cloud/legacy

### 2.1 닉네임 → userId ✅

```
POST https://users.roblox.com/v1/usernames/users
Body: { "usernames": ["김치"], "excludeBannedUsers": true }
응답: { "data": [{ "id": 12345678, "name": "김치", "displayName": "..." }] }
```
- `data`가 빈 배열이면 존재하지 않는 닉네임 → 우리 API는 404 반환.

### 2.2 userId → 즐겨찾기 게임 목록 ⚠️ (이 프로젝트 최대 리스크 지점)

**1순위** — games.roblox.com 문서화 엔드포인트:
```
GET https://games.roblox.com/v2/users/{userId}/favorite/games?accessFilter=2&limit=50&cursor=...
응답: { "data": [{ "id": <universeId>, "name": ..., "rootPlace": { "id": <placeId> }, ... }], "nextPageCursor": ... }
```

**2순위(폴백)** — 구형 list-json (2024-08 시점 동작 확인, 비공개 인벤토리여도 조회됨):
```
GET https://www.roblox.com/users/favorites/list-json?assetTypeId=9&itemsPerPage=100&pageNumber={n}&userId={userId}
```
- assetTypeId=9 = Place. 응답의 `Item.ItemId`가 placeId → 2.3으로 universeId 변환 필요.

**리스크**: 2025년 하반기부터 즐겨찾기 계열 엔드포인트가 순차적으로 잠기고 있다는
개발자 포럼 보고가 있음(catalog favorites의 Place 타입이 "Invalid asset type id" 반환 사례).
→ 구현 시 `RobloxFavoritesClient` 인터페이스 하나에 두 구현체(Primary/Fallback)를 두고
  `@Retryable` + 폴백으로 감싸는 것을 권장. 둘 다 실패하는 날이 오면 UX 대안 필요(§7 Q2).

### 2.3 placeId ↔ universeId 변환 ✅

```
GET https://apis.roblox.com/universes/v1/places/{placeId}/universe   → { "universeId": ... }
GET https://games.roblox.com/v1/games/multiget-place-details?placeIds=1,2,3   (인증 필요할 수 있음 ⚠️)
```

### 2.4 게임 상세 (배치 조회) ✅

```
GET https://games.roblox.com/v1/games?universeIds=1000001,1000002,...
응답 data[] 필드: id(universeId), rootPlaceId, name, description, creator.name,
                 playing, visits, favoritedCount, genre, created, updated, maxPlayers ...
```
- **한 번에 여러 universeId 조회 가능** → N+1 호출 금지, 반드시 배치로 모아 호출.

### 2.5 평점(votes) / 즐겨찾기 수 ✅

```
GET https://games.roblox.com/v1/games/votes?universeIds=...   → upVotes/downVotes
GET https://games.roblox.com/v1/games/{universeId}/favorites/count
```
- FE의 `rating`(4.2 등)은 `upVotes/(upVotes+downVotes)*5` 로 환산해 소수1자리 반올림.

### 2.6 관련 게임 "People Also Join" ⚠️ (추천 알고리즘의 그래프 소스)

```
GET https://games.roblox.com/v1/games/recommendations/game/{universeId}?maxRows=6
```
- 게임 페이지 하단의 추천 목록에 해당. **구현 첫날 최우선 검증 대상.**
- 실패 시 대안: (a) 같은 genre의 인기 게임으로 대체 관계 생성,
  (b) games-autocomplete/검색 API 기반 유사도. 어느 쪽이든 `game_relations` 테이블
  스키마는 그대로 유지되므로 알고리즘 코드는 변경 없음.

### 2.7 썸네일 ✅

```
GET https://thumbnails.roblox.com/v1/games/icons?universeIds=...&size=256x256&format=Png&isCircular=false
GET https://thumbnails.roblox.com/v1/games/multiget/thumbnails?universeIds=...&size=768x432&format=Png
```
- 결과 imageUrl을 `games.thumbnail_url`에 캐시. FE `thumbnailTheme`(그라데이션)은
  실제 URL이 생기면 사용 중단 예정 — FE `Game` 타입에 `thumbnailUrl?: string` 추가 예정.

### 2.8 YouTube Data API v3 (쇼츠) ✅

```
GET https://www.googleapis.com/youtube/v3/search
    ?part=snippet&type=video&videoDuration=short&maxResults=5
    &q=roblox {gameName} shorts&key={API_KEY}
```
- **쿼터 주의**: search.list = 호출당 100유닛, 일일 기본 10,000유닛 = 하루 100회 검색.
  → 게임당 최초 1회만 검색하고 `game_videos`에 저장, TTL 7일. 절대 요청마다 호출 금지.

---

## 3. 내부 API 명세 (FE ↔ Spring Boot)

공통: Base URL `/api`, 응답 `application/json; charset=utf-8`

### 3.1 에러 포맷 (RFC 9457 Problem Details 유사)

모든 4xx/5xx 응답 body. FE `ApiError` 타입과 일치:
```json
{ "status": 404, "title": "닉네임 조회 오류",
  "detail": "입력하신 닉네임의 로블록스 사용자를 찾을 수 없습니다.",
  "instance": "/api/users/김치/favorites" }
```
Spring 구현: `@RestControllerAdvice` + `ProblemDetail`(Spring 6 내장) 사용 권장.

### 3.2 GET /api/users/{nickname}/favorites — 즐겨찾기 조회

- 처리: 닉네임→userId(2.1) → 즐겨찾기 universeId 목록(2.2) → 게임 상세 배치(2.4)
  → `users`/`games`/`user_favorites` upsert → 응답
- 200 응답 (FE `FavoritesSearchResult`):
```json
{
  "nickname": "김치",
  "displayName": "김치",
  "favorites": [
    {
      "id": "1000003",            // universeId의 문자열. FE 전역 게임 식별자
      "universeId": 1000003,
      "placeId": 2000003,
      "name": "Blox Fruits",
      "genre": "Adventure",
      "tags": ["action", "adventure", "open-world"],
      "playingCount": 55000,
      "playingLabel": "55K",       // 서버에서 포맷 (1_000→"1K", 1_000_000→"1M")
      "rating": 4.6,
      "releasedYear": 2019,
      "developerName": "Gamer Robot Inc",
      "description": "...",
      "thumbnailTheme": { "from": "#1E5F74", "to": "#133B5C" }
    }
  ]
}
```
- `tags`: Roblox 응답의 genre + (있다면) genre_l1/l2를 소문자 배열로. 없으면 `[genre.toLowerCase()]`.
- **즐겨찾기 0개 / 즐겨찾기 API 차단 시(확정)**: 에러가 아니라 `favorites: []`로 200 응답.
  FE가 티어표 화면에서 "즐겨찾기 목록이 존재하지 않아 조회할 수 없습니다" 안내를 띄우고
  빈 보드를 보여준다. (§2.2 폴백까지 모두 실패한 경우도 동일하게 빈 배열)
- 404: 닉네임 없음 / 502: Roblox 사용자 조회 자체가 장애일 때

### 3.3 POST /api/recommendations — 추천 계산

- 요청 (FE `TierEntryPayload[]` + 닉네임):
```json
{ "nickname": "김치",
  "entries": [ { "gameId": "1000003", "tier": "S" }, { "gameId": "1000001", "tier": "A" } ] }
```
- 처리(§4 알고리즘) 후 200 응답 (FE `RecommendationsResponse`):
```json
{
  "recommendations": [
    {
      "game": { ...3.2의 Game과 동일 스키마... },
      "rank": 1,
      "totalScore": 1.8600,
      "matchPercent": 99,
      "reason": "action, competitive 장르를 좋아하는 분께 추천",
      "sources": [
        { "seedGameId": "1000003", "seedGameName": "Blox Fruits",
          "seedTier": "S", "depth": 1, "contribution": 0.6 }
      ]
    }
  ]
}
```
- 400: entries 비어있음 / tier가 S|A|B|C 외 값
- 저장 정책(무상태 vs DB 영속)은 §7 Q1 — 어느 쪽이든 응답 스키마는 동일.

### 3.4 GET /api/games/{gameId} — 게임 상세

- `gameId` = universeId 문자열
- 처리: `games` 캐시 확인(TTL 내면 DB만) → 관련 게임(2.6, `game_relations` upsert)
  → 쇼츠(`game_videos` 캐시, 만료 시에만 2.8 호출)
- 200 응답 (FE `GameDetailResponse`):
```json
{
  "game": { ...Game... },
  "relatedGames": [ ...Game 최대 6개... ],
  "videos": [
    { "youtubeVideoId": "abc123XYZ_-", "title": "Blox Fruits 꿀팁 TOP 5", "viewsLabel": "1.7M 조회" }
  ]
}
```
- 404: 존재하지 않는 universeId

### 3.5 티어표 저장/불러오기 (확정: 닉네임당 활성 티어표 1개 영속화)

같은 닉네임으로 재방문하면 이전에 정리한 티어표가 복원되어야 한다.
`tier_lists`는 (user_id, status='ACTIVE') 유니크로 1개만 유지하고 PUT마다 upsert.

```
GET /api/users/{nickname}/tier-list
  200: { "entries": [ { "gameId": "1000003", "tier": "S" }, ... ] }   // 없으면 entries: []

PUT /api/users/{nickname}/tier-list
  요청: { "entries": [ { "gameId": "1000003", "tier": "S" }, ... ] }
  처리: tier_lists upsert → tier_entries 전체 삭제 후 재삽입 (배열 순서 = position)
  200: { "entries": [...] }  (저장된 그대로 반환)
```

- FE는 티어표에서 게임을 배치/제거/초기화할 때마다 PUT을 호출한다(자동 저장).
- POST /api/recommendations 요청 body에 `nickname`을 추가하여, 서버가 해당 유저의
  ACTIVE tier_list와 연결해 recommendations/recommendation_sources를 저장한다.
  (응답 스키마는 §3.3과 동일 — FE 변경 없음)

---

## 4. 추천 알고리즘 명세 (참조 구현: `src/mocks/selectors/recommend.selectors.ts`)

```
입력: entries = [{gameId(=universeId), tier}]
정책: TIER_WEIGHT  = {S:1.0, A:0.7, B:0.4, C:0.1}   ← tier_weights 테이블
      DEPTH_WEIGHT = {1:0.6, 2:0.3}                  ← weight_configs 테이블
      MAX_RESULTS  = 9

1. seed마다 game_relations에서 depth1(최대 6개), 그 각각의 depth2(최대 6개) 순회
2. 후보 gameId별 contribution = TIER_WEIGHT[seedTier] × DEPTH_WEIGHT[depth] 를 "합산"
   - 같은 후보에 여러 시드가 도달하면 전부 더한다 (max가 아님)
   - 시드 자신 및 티어표에 있는 게임은 후보에서 제외
   - (선택) 사용자의 전체 즐겨찾기도 제외 — FE 목업은 제외함
3. 각 contribution을 recommendation_sources 행으로 기록
4. matchPercent = min(99, round(60 + (totalScore / maxTotalScore) × 39))
5. reason: source들의 시드 게임 tags에 contribution을 가중 합산 → 상위 2개 태그로
   "{tag1}, {tag2} 장르를 좋아하는 분께 추천"
6. totalScore 내림차순 정렬, 상위 9개, rank = 1부터 부여
```

단위 테스트 기대값 예시(검증용): S티어 시드 1개, 그 depth1 후보 A가 다른 시드(B티어)의
depth2이기도 하면 → A.totalScore = 1.0×0.6 + 0.4×0.3 = 0.72, sources 2건.

---

## 5. Roblox 응답 ↔ ERD 필드 매핑

| ERD 컬럼 | Roblox 소스 |
|---|---|
| games.universe_id | games?universeIds 응답 `id` |
| games.place_id | 응답 `rootPlaceId` |
| games.name / description | `name` / `description` |
| games.developer_name | `creator.name` |
| games.genre | `genre` |
| games.playing_count | `playing` |
| games.visits | `visits` |
| games.favorites_count | `favoritedCount` |
| games.rating | votes 응답에서 환산 (§2.5) |
| games.released_year | `created`의 연도 |
| games.thumbnail_url | thumbnails API `imageUrl` |
| game_relations.related_game_id | recommendations 응답 각 항목 (§2.6) |
| game_relations.display_order | 응답 배열 인덱스 1~6 |
| user_favorites | §2.2 응답 |
| game_videos.* | YouTube search 응답 snippet (§2.8) |

## 6. 캐싱/동기화 정책 (권장 TTL)

| 데이터 | TTL | 비고 |
|---|---|---|
| users (닉네임→userId) | 24h | |
| user_favorites | 1h | 사용자가 새로고침하면 강제 갱신 허용 |
| games 상세 | 6h | playing_count만 오래되면 stale 표기 |
| game_relations | 24h | 추천 품질에 직결, 너무 짧게 잡지 않기 |
| game_videos | 7d | YouTube 쿼터 보호 (§2.8) |

Roblox 호출 공통: 레이트리밋 대비 지수 백오프 재시도(429/5xx), 타임아웃 3s,
User-Agent 지정. 배치 엔드포인트(2.4, 2.5, 2.7)는 universeId 최대 50~100개씩 묶기.

## 7. 확정된 설계 결정 (v2)

- **Q1 (확정)**: ERD대로 영속화. 닉네임당 ACTIVE 티어표 1개를 §3.5로 저장/복원하고,
  추천 계산 시 recommendations/recommendation_sources를 tier_list에 연결해 저장한다.
- **Q2 (확정)**: 즐겨찾기 0개·조회 불가 시 200 + 빈 배열. FE는 티어표 화면에서
  "즐겨찾기 목록이 존재하지 않아 조회할 수 없습니다" 안내와 빈 보드를 표시한다.
