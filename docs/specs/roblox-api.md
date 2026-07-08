# Roblox API 레퍼런스 (실측 검증본)

> 이 문서의 모든 엔드포인트는 문서만 보고 적은 것이 아니라, **실제로 호출해서 동작·응답을 확인**한 것이다.
> 검증일: 2026-07-02. Roblox 웹 API는 예고 없이 바뀔 수 있으므로, 사용 전 재확인을 권장한다.
> 별도 표기가 없으면 **인증 불필요(무인증 GET)** 이다.

## 0. 중요 원칙 (왜 어떤 건 되고 어떤 건 안 되나)

Roblox API 접근성의 기준은 하나다: **"Roblox 공식 웹/앱에 남에게 공개로 표시되는 정보인가."**

- 공개 표시됨 → API도 열림 (게임 정보, 즐겨찾기, 그룹 멤버, 유저 프로필)
- 본인에게만 보임 / 비공개 → API도 막힘 (최근 플레이, 플레이타임, 서버 접속자 신원)

"대상 → 그 대상에 연결된 유저 목록"(역방향)은 아동 안전 목적으로 **체계적으로 봉인**되어 있다.

---

## 1. 도메인 지도

| 도메인 | 담당 |
|---|---|
| `apis.roblox.com/explore-api` | 차트/디스커버 |
| `apis.roblox.com/search-api` | 게임 검색 |
| `apis.roblox.com/universes` | placeId ↔ universeId 변환 |
| `games.roblox.com` | 게임 상세/투표/추천/서버/미디어/유저별 게임 |
| `groups.roblox.com` | 그룹 정보/멤버 |
| `users.roblox.com` | 유저 프로필/검색 |
| `friends.roblox.com` | 친구/팔로워 |
| `thumbnails.roblox.com` | 모든 이미지 URL |
| `presence.roblox.com` | 접속 상태 |
| `inventory.roblox.com` | 인벤토리 |
| `avatar.roblox.com` | 아바타 착용 아이템 |
| `api.rolimons.com` | (제3자) 게임 목록 |

---

## 2. ✅ 쓸 수 있는 엔드포인트 (검증 완료)

### 2-1. 인기/트렌드 게임 (핵심)
```
GET https://apis.roblox.com/explore-api/v1/get-sorts?sessionId=아무값&device=computer&country=all
```
→ 차트 종류 반환: `top-trending`, `up-and-coming`, `top-playing-now`, `fun-with-friends`, `top-revisited`

```
GET https://apis.roblox.com/explore-api/v1/get-sort-content?sessionId=아무값&sortId=top-playing-now&device=computer&country=all
```
→ 차트별 게임 ~95개. 필드: `universeId`, `rootPlaceId`, `name`, `playerCount`, `totalUpVotes/DownVotes`, `minimumAge`, `contentMaturity`
- `country=kr` 로 한국 지역 차트 가능
- `sessionId`는 임의 문자열이면 됨

### 2-2. 게임 검색
```
GET https://apis.roblox.com/search-api/omni-search?searchQuery=검색어&pageType=all&sessionId=아무값
```
→ 검색 결과 게임 목록 (universeId, name, playerCount, 투표수, 연령등급, URL 경로)

### 2-3. 게임 상세 (장르 포함, 핵심)
```
GET https://games.roblox.com/v1/games?universeIds=1,2,...   (최대 50개)
```
→ `name`, `description`, `creator`(그룹/유저), `genre`/`genre_l1`/`genre_l2`(2단계 장르),
   `visits`(총 방문), `playing`(현재 동접), `favoritedCount`, `maxPlayers`, `created`/`updated`

### 2-4. 게임 투표수
```
GET https://games.roblox.com/v1/games/votes?universeIds=...
```
→ `upVotes`, `downVotes`

### 2-5. 유사 게임 추천 (핵심 — Roblox 자체 co-play 알고리즘)
```
GET https://games.roblox.com/v1/games/recommendations/game/{universeId}?maxRows=10
```
→ 유사 게임 목록. 재귀 호출로 "추천의 추천" 그래프 확장 가능

### 2-6. 게임 미디어
```
GET https://games.roblox.com/v2/games/{universeId}/media
```
→ 스크린샷/영상 자산 ID 목록

### 2-7. placeId → universeId 변환
```
GET https://apis.roblox.com/universes/v1/places/{placeId}/universe
```
→ `{ "universeId": ... }`  (Rolimons가 placeId를 주므로 이걸로 변환 필요)

### 2-8. 썸네일 (이미지)
```
GET https://thumbnails.roblox.com/v1/games/icons?universeIds=...&size=256x256&format=Png
GET https://thumbnails.roblox.com/v1/games/multiget/thumbnails?universeIds=...&size=768x432&countPerUniverse=2&format=Png
GET https://thumbnails.roblox.com/v1/users/avatar-headshot?userIds=...&size=420x420&format=Png
```
→ CDN 이미지 URL

### 2-9. 유저 프로필
```
GET  https://users.roblox.com/v1/users/{userId}
POST https://users.roblox.com/v1/usernames/users        body: {"usernames":["이름"]}   (이름→ID)
GET  https://users.roblox.com/v1/users/search?keyword=이름&limit=10
GET  https://users.roblox.com/v1/users/{userId}/username-history
```

### 2-10. 유저의 게임 (취향 파악, 핵심)
```
GET https://games.roblox.com/v2/users/{userId}/favorite/games?limit=50   ← 즐겨찾기 (취향 신호)
GET https://games.roblox.com/v2/users/{userId}/games?limit=50            ← 만든 게임
```
- 유저가 프로필 비공개면 빈 값

### 2-11. 그룹 & 멤버 (협업 필터링 핵심)
```
GET https://groups.roblox.com/v1/groups/{groupId}                        ← 그룹 정보, memberCount
GET https://groups.roblox.com/v1/groups/{groupId}/users?limit=100&sortOrder=Desc   ← 멤버 목록 (userId+이름)
```
- **주의: 그룹마다 멤버 목록 공개 여부가 다름.** 공개 그룹은 200 OK, 비공개/이상 그룹은 400.
  (예: Blox Fruits `Gamer Robot Inc`=공개 / `Korea Gamez`=400)
- `sortOrder=Desc`는 최신 가입 순 → 활성/최신 유저 표본에 유리

### 2-12. 친구/팔로워
```
GET https://friends.roblox.com/v1/users/{userId}/friends
GET https://friends.roblox.com/v1/users/{userId}/friends/count
GET https://friends.roblox.com/v1/users/{userId}/followers/count
```

### 2-13. 기타 유저 정보
```
POST https://presence.roblox.com/v1/presence/users   body: {"userIds":[...]}   ← 접속상태(게임중 여부는 프라이버시 종속)
GET  https://avatar.roblox.com/v1/users/{userId}/currently-wearing          ← 착용 아이템 assetId
GET  https://inventory.roblox.com/v1/users/{userId}/assets/collectibles     ← 한정판 인벤토리(공개 계정만)
```

### 2-14. (제3자) Rolimons — 넓은 게임 목록
```
GET https://api.rolimons.com/games/v1/gamelist
```
→ 약 6,700개 게임 `{placeId: [이름, 동접, 아이콘URL]}`
- ⚠️ 기본 요청은 403. **브라우저 유사 헤더 필요** (User-Agent, Referer 등). 헤더 위장은 회색지대이므로 캐싱·저빈도 필수.

---

## 3. ❌ 불가능한 것 (실측으로 막힘 확인)

| 하려던 것 | 결과 |
|---|---|
| 게임 서버 접속자 신원 (`players`/`playerTokens`) | 빈 배열. playerToken은 익명화 해시, 유저 변환 불가 (Roblox 개발자 확인) |
| 유저의 뱃지 목록 | 401 인증 필요 |
| 유저의 최근 플레이 게임 / 플레이타임 | 404. 엔드포인트 자체가 없음 (본인 전용 비공개) |
| 뱃지/게임패스 소유자 목록 (역방향) | 엔드포인트 없음 / 제한됨 |
| 아이템 소유자 신원 (`/v2/assets/{id}/owners`) | 200이지만 `owner` 필드 전부 null |
| 전체 게임 목록 (전수) | 존재하지 않음. 차트+검색+추천+Rolimons로 부분집합만 |

---

## 4. Rate Limit (실측)

- 유저 즐겨찾기 순차 조회: 초당 ~1.6명, 100% 성공
- 동시성 5+: 상당수 429 발생
- **권장: 동시성 3 + 429 시 지수 백오프 재시도** → 실측상 429 0건 달성
- 대량 수집은 반드시 **배치 → DB 캐싱**, 사용자 요청은 캐시에서만 응답

---

## 5. 참고 GitHub / 문서

| 자료 | 용도 |
|---|---|
| [create.roblox.com/docs/cloud](https://create.roblox.com/docs/cloud) | 공식 문서 (Open Cloud + Legacy). 단, 실동작과 다를 수 있음 |
| [matthewdean/roblox-web-apis](https://github.com/matthewdean/roblox-web-apis) | 커뮤니티 웹 API 전체 목록 |
| [S0ftwareUpd8/roblox-api](https://github.com/S0ftwareUpd8/roblox-api) | 엔드포인트별 상세 문서 |
| [vexsyx/rolimons-api-docs](https://github.com/vexsyx/rolimons-api-docs) | Rolimons 비공식 API 문서 |
| [AntiBoomz/BTRoblox](https://github.com/AntiBoomz/BTRoblox) | 내부 API(explore-api 등) 관찰 소스 |

---

## 6. 추천 서비스에 쓰는 조합 (요약)

- **인기/트렌드**: explore-api 차트
- **게임 유사도**: recommendations API (+ 재귀)
- **유저 취향**: 그 유저 즐겨찾기
- **팬층 협업 필터링**: 게임 그룹 멤버 표본 → 즐겨찾기 집계 (자체 구현, 차별점)
- **취향 데이터 없는 유저 폴백**: 질문 → 장르 매칭 → 사전 수집 데이터셋
