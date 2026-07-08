# API 명세 (최종)

> 전 엔드포인트 무인증 실측 검증 (2026-07-03 ~ 07-04).
> 응답 필드·타입·최대값은 실제 호출 결과 기준. DB 컬럼 타입의 근거 문서.
> 표기: 요청(보내는 것) / 응답(받는 것) / 저장(어느 테이블로 가나) / 주의

---

## 사용 흐름 요약

| 단계 | 사용 API |
|---|---|
| ① 닉네임 입력 | A-1 (닉네임→ID), E-3 (즐겨찾기) |
| ② 티어 배치·게임 추가 | A-2, A-3 (검색) |
| ③ 추천 계산 | C-1 (연쇄 추천), 캐시(game_cofavorite) |
| ④ 결과·상세 표시 | B-1, B-2, B-3, F-1, F-2, (G-1) |
| 배치: 인기 목록 | D-1 (Rolimons 주력), D-2/D-3 (explore 폴백) |
| 배치: 팬 수집 | E-0 (그룹 정보), E-2 (멤버), E-3 (즐겨찾기) |
| 배치: 게임 채우기 | B-1, B-2, B-4, F-1 |

---

# A. 입력 / 검색

## A-1. 닉네임 → 유저 ID

```
POST https://users.roblox.com/v1/usernames/users
Content-Type: application/json
```
요청 body:
```json
{ "usernames": ["kim_chulsu"], "excludeBannedUsers": false }
```
| 요청 필드 | 타입 | 설명 |
|---|---|---|
| usernames | string 배열 | 닉네임 목록 (여러 개 가능) |
| excludeBannedUsers | bool | 밴 계정 제외 여부 |

응답 `data[]`:
| 필드 | 타입 | 예 |
|---|---|---|
| requestedUsername | string | 요청한 닉네임 |
| id | **number (BIGINT, 실측 73억)** | userId ★ |
| name | string | 실제 닉네임 (대소문자 정정됨) |
| displayName | string | 표시 이름 |
| hasVerifiedBadge | bool | 인증 뱃지 |

- 저장: `users` (user_id, username, display_name)
- 주의: 존재하지 않는 닉네임이면 data가 빈 배열 → "닉네임 없음" 처리

## A-2. 게임명 자동완성

```
GET https://apis.roblox.com/games-autocomplete/v1/get-suggestion/{입력어}
```
응답 `entries[]`: `searchQuery`(추천 검색어 string), `canonicalTitle`, `thumbnailUrl`, `universeId`(대개 0)

- 저장: 안 함 (실시간 전용)
- 주의: **검색어 문자열만 줌** (게임 정보 없음) → 이 검색어로 A-3 재검색하는 2단 구조

## A-3. 게임명 검색 (omni-search)

```
GET https://apis.roblox.com/search-api/omni-search?searchQuery={이름}&pageType=all&sessionId={임의문자열}
```
| 요청 파라미터 | 타입 | 설명 |
|---|---|---|
| searchQuery | string | 검색어 (URL 인코딩) |
| pageType | string | `all` 고정 |
| sessionId | string | 임의 값 아무거나 |

응답 `searchResults[].contents[]`:
| 필드 | 타입 |
|---|---|
| universeId / contentId | number (BIGINT) |
| name | string |
| description | string (905자+ 관찰) |
| rootPlaceId | number (BIGINT) |
| playerCount | number (INT) |
| totalUpVotes / totalDownVotes | number (INT) |
| creatorId, creatorName, creatorHasVerifiedBadge | number/string/bool |
| minimumAge | number |
| contentMaturity, ageRecommendationDisplayName | string |
| canonicalUrlPath | string |
| isSponsored, emphasis | bool |

- 저장: 유저가 선택한 게임만 `games` (+ 없으면 `collect_queue`)
- 용도: 티어표 직접 추가(F-03)

---

# B. 게임 정보

## B-1. 게임 상세 ★핵심 (최대 50개 배치)

```
GET https://games.roblox.com/v1/games?universeIds={id1},{id2},...
```
| 요청 파라미터 | 타입 | 설명 |
|---|---|---|
| universeIds | number 콤마 목록 | **최대 50개** 한 번에 |

응답 `data[]` (전체 필드, 실측):
| 필드 | 타입 (실측 최대값) | 저장 컬럼 |
|---|---|---|
| id | number (**102억** → BIGINT) | games.universe_id |
| rootPlaceId | number (**140조** → BIGINT) | games.place_id |
| name | string (이모지 포함 "[🏖️] RIVALS") | games.name |
| description | string (800자+) | games.description |
| genre_l1 | string ("Shooter") | games.genre_l1 |
| genre_l2 | string ("Deathmatch Shooter") | games.genre_l2 |
| genre | string (구버전 장르) | 미사용 |
| playing | number (55만 관찰 → INT) | games.playing |
| visits | number (**625억** → BIGINT) | games.visits |
| favoritedCount | number (8,200만 → BIGINT) | games.favorited_count |
| creator | object `{id, name, type, hasVerifiedBadge, isRNVAccount}` | creator_type, creator_group_id |
| created / updated | string ISO8601 ("2024-05-26T16:47:48.617Z", 소수부 3~7자리 가변) | 미저장 (필요시 DATETIME 변환) |
| price | number 또는 null | 미사용 |
| maxPlayers, isGenreEnforced, copyingAllowed, universeAvatarType 등 | | 미사용 |

- **creator.type == "Group"이면 creator.id가 소유 그룹 ID** → 2단계 팬 확장의 입구 (E-0, E-2로 연결)
- 저장: `games`
- 주의: 배치 워커의 캐시 갱신도 이 API (updated_at 오래된 것부터 50개씩)

## B-2. 좋아요/싫어요 (최대 50개 배치)

```
GET https://games.roblox.com/v1/games/votes?universeIds={id,..}
```
응답 `data[]`: `id`(BIGINT), `upVotes`(INT, 1,200만 관찰), `downVotes`(INT)
- 저장: `games.up_votes / down_votes`

## B-3. 게임 미디어 (스크린샷 + 개발자 영상)

```
GET https://games.roblox.com/v2/games/{universeId}/media
```
응답 `data[]`:
| 필드 | 타입 | 설명 |
|---|---|---|
| assetType | string | `Image`(스크린샷) / `GamePreviewVideo`(개발자 영상) |
| assetTypeId | number | Image=1, GamePreviewVideo=86 |
| imageId | number (**119조** → BIGINT) | 이미지 에셋 ID → F-2로 URL 변환 |
| videoId | **string (숫자 14자리, 예 "99244789216819")** | **로블록스 영상 에셋 ID** |
| videoHash, videoTitle | null (구필드) | 미사용 |
| approved | bool | 승인 여부 |

- ⚠️ **실측 정정: videoId는 유튜브 ID가 아님** (유튜브 oembed 400 확인). 로블록스 자체 호스팅 영상.
- 재생 방법: `GET https://assetdelivery.roblox.com/v2/assetId/{videoId}` → `locations[0].location`에 CDN 영상 URL → `<video>` 태그 재생
- **CDN URL에 만료 토큰 포함 → URL 저장 금지, videoId만 저장하고 재생 시마다 재발급**
- 저장: `game_media` (asset_type, image_id, video_asset_id)
- 영상은 개발자가 등록한 게임만 있음 (없는 게임 많음) → 없으면 G-1 폴백

## B-4. placeId → universeId 변환

```
GET https://apis.roblox.com/universes/v1/places/{placeId}/universe
```
응답: `{ "universeId": 994732206 }`
- 용도: **Rolimons(D-1)가 placeId만 주므로** universeId 변환에 필수

---

# C. 추천 (알고리즘 핵심)

## C-1. 연쇄 추천 — People Also Join ★핵심

```
GET https://games.roblox.com/v1/games/recommendations/game/{universeId}?maxRows=6
```
응답 `games[]` — **게임당 최대 6개** (maxRows를 늘려도 6개 상한):
| 필드 | 타입 | 비고 |
|---|---|---|
| universeId | number (BIGINT) | 연관 게임 ★ |
| placeId | number (BIGINT) | |
| name | string | |
| playerCount | number (INT) | 동접 |
| totalUpVotes / totalDownVotes | number (INT) | |
| creatorId, creatorName, creatorType | number/string | |
| imageToken | string | 썸네일 토큰 |
| canonicalUrlPath | string | |
| **gameDescription** | **string — 빈 문자열 ""** | ⚠️ 실측 정정 |
| **genre** | **string — 빈 문자열 ""** | ⚠️ 실측 정정 |
| minimumAge / ageRecommendationDisplayName | 0 / "" | 사실상 미제공 |

- ⚠️ **실측 정정 (07-04): 장르·설명이 빈 값으로 옴** → 연관 게임의 상세는 **반드시 B-1로 별도 조회**해야 함
- 간헐적 HTTP 500 발생 → **재시도 로직 필수** (2초 간격 2~3회로 복구 확인됨)
- 저장: `game_recommendations` (from, to, rec_rank 1~6, fetched_at)
- 알고리즘 규칙 (조회 코드에서 처리, 저장 안 함):
  - **depth 1~2 보장** (캐시 없으면 API 호출: 1+6=7회) → 최대 36개
  - **depth 3은 캐시에 있을 때만 확장** (API 추가 호출 없음)
  - depth 가중치: 깊을수록 낮게 / **다중 경로 도달 시 소폭 가산**
  - 티어 가중치 × 티어 내 위치(position) 보정 곱함

---

# D. 인기 게임 목록 (캐싱 대상 선정)

## D-1. Rolimons 게임 목록 — 주력

```
GET https://api.rolimons.com/games/v1/gamelist
필수 헤더: User-Agent(브라우저 값), Referer: https://www.rolimons.com/games
```
응답:
```json
{ "success": true, "game_count": 6716,
  "games": { "1818": ["Classic: Crossroads", 36, "https://tr.rbxcdn.com/..."], ... } }
```
| 구조 | 타입 | 설명 |
|---|---|---|
| games의 key | string (**placeId**, 최대 140조) | ⚠️ universeId 아님 → B-4로 변환 필요 |
| value[0] | string | 게임 이름 |
| value[1] | number | 동접 |
| value[2] | string | 아이콘 URL (만료성 토큰) |

- 약 6,716개 한 번에 (explore보다 훨씬 넓은 풀)
- 저장: `chart_snapshot` (sort_id='rolimons') → 상세는 B-1로 채움
- ⚠️ **비공식 서드파티**: 헤더 없으면 403. 언제든 막힐 수 있음 → **실패 시 D-2로 자동 폴백 필수**

## D-2. explore-api 차트 — 폴백 (로블록스 공식)

```
GET https://apis.roblox.com/explore-api/v1/get-sort-content?sessionId={임의}&sortId={차트}&device=computer&country={all|kr}
```
| 요청 파라미터 | 값 |
|---|---|
| sortId | `most-popular`, `top-playing-now`, `top-trending`, `top-revisited`, `top-earning`, `up-and-coming`, `trending-in-{장르}` (17장르) |
| country | `all` 또는 `kr` (한국 차트) |

응답 `games[]`: universeId(BIGINT), rootPlaceId(BIGINT), name, playerCount(INT), totalUpVotes/DownVotes(INT), minimumAge, contentMaturity, isSponsored
- **차트당 최대 ~95개**, 총 26종 차트
- 저장: `chart_snapshot` (sort_id=차트명)

## D-3. 차트 종류 목록

```
GET https://apis.roblox.com/explore-api/v1/get-sorts?sessionId={임의}&device=computer&country=all
```
- 26종 차트 ID 목록 (페이지네이션 `nextSortsPageToken`)

---

# E. 그룹 / 팬 (2단계 알고리즘, 배치 전용)

## E-0. 그룹 정보 (memberCount) ★07-04 추가 확인

```
GET https://groups.roblox.com/v1/groups/{groupId}
```
응답:
| 필드 | 타입 | 예 (Gamer Robot) |
|---|---|---|
| id | number (BIGINT) | 4372130 |
| name | string | "Gamer Robot Inc" |
| **memberCount** | **number (1,895만 관찰 → BIGINT)** | 18948421 |
| owner | object {userId, username, ...} | |
| description, shout 등 | | 미사용 |

- 용도: **"전체 멤버의 X% 스킵 지점" 계산** (뉴비 구간 건너뛰기)
- 저장: `group_cursors.member_count`

## E-1. 게임 → 소유 그룹

별도 API 없음 — **B-1 응답의 `creator` 필드** 사용 (type=="Group"이면 id가 그룹 ID)

## E-2. 그룹 멤버 목록 (커서 페이지네이션)

```
GET https://groups.roblox.com/v1/groups/{groupId}/users?limit=100&sortOrder={Asc|Desc}&cursor={커서}
```
| 요청 파라미터 | 설명 |
|---|---|
| limit | **최대 100** (더 못 늘림, 실측) |
| sortOrder | Asc=오래된 가입순(정착 유저) / Desc=최신 가입순 |
| cursor | 이전 응답의 nextPageCursor (첫 페이지는 생략) |

응답:
| 필드 | 타입 |
|---|---|
| data[].user | {userId(BIGINT, 73억 관찰), username, displayName, hasVerifiedBadge} |
| data[].role | {id, name, rank} |
| **nextPageCursor** | **string (실측 296자 → TEXT)** — 다음 페이지 커서 |

- ⚠️ **커서 순차 전용**: 점프 불가, 병렬화 불가 (100명당 ~0.65초 고정)
- **커서는 저장·재사용 가능** (그룹별 개별) → `group_cursors.anchor_cursor / progress_cursor`
- Desc는 신규 가입으로 약간 밀림, Asc는 과거 고정이라 안정적
- 저장: 판별된 팬만 `game_fans`

## E-3. 유저 즐겨찾기 게임

```
GET https://games.roblox.com/v2/users/{userId}/favorite/games?limit=50
```
응답 `data[]`:
| 필드 | 타입 |
|---|---|
| id | number (BIGINT) — universeId |
| name | string |
| description | string |
| creator | {id, type, name} |
| rootPlace | {id, type} |
| created / updated | string ISO8601 |
| placeVisits | number |

- **유저별 독립 → 동시성 가능** (안전 운영 50~100)
- 두 용도: ①서비스 이용자의 즐겨찾기(실시간, 저장 안 함) ②수집된 팬의 즐겨찾기 → `user_favorites` (recorded_at 포함, 1년 초과 삭제)

---

# F. 썸네일 / 에셋

## F-1. 게임 아이콘 (배치 가능)

```
GET https://thumbnails.roblox.com/v1/games/icons?universeIds={id,..}&size=256x256&format=Png
```
응답 `data[]`: `targetId`(BIGINT), `state`("Completed"), `imageUrl`(string, CDN)
- 저장: `games.icon_url` (URL 만료 가능 → 주기 갱신)

## F-2. 게임 스크린샷 URL 변환

```
GET https://thumbnails.roblox.com/v1/games/multiget/thumbnails?universeIds={id}&size=768x432&format=Png&countPerUniverse={n}
```
응답 `data[].thumbnails[]`: `targetId`(=imageId), `state`, `imageUrl`
- B-3의 imageId를 실제 이미지 URL로 변환
- 저장 안 함 (표시 시 변환) 또는 단기 캐시

## F-3. 영상 에셋 재생 URL (개발자 영상)

```
GET https://assetdelivery.roblox.com/v2/assetId/{video_asset_id}
```
응답: `assetTypeId`(86=영상), `locations[0].location`(CDN 영상 URL)
- ⚠️ **URL에 만료 토큰 → 저장 금지, 재생 직전마다 발급**
- game_media.video_asset_id → 이 API → `<video src=...>`

---

# G. 유튜브 (F-10 선택 기능)

## G-1. 게임 영상 검색

```
YouTube Data API v3: GET https://www.googleapis.com/youtube/v3/search
  ?part=snippet&q={게임명}+roblox&type=video&key={API키}
```
응답 items[]: `id.videoId`(string 11자), `snippet.title`, `snippet.thumbnails.*.url`

- **하루 할당량 10,000유닛, search 1회=100유닛 → 하루 100회 한도**
- 저장: `game_videos` (영상 1개당 1행: youtube_video_id, title, thumbnail_url) — **한 번 검색한 게임은 DB에서만 조회**
- 우선순위: B-3 개발자 영상 있으면 그것 먼저, 없을 때만 G-1

---

# 사용하지 않기로 한 API

| API | 이유 |
|---|---|
| `users.roblox.com/v1/users/{id}` (계정 나이) | rate limit 심함 (대량 조회 14%만 성공) → 계정나이 가중치 폐기 |
| `thumbnails.../users/avatar-headshot` | 유저 프로필 표시 없음 |
| 홈 추천 `discovery-api/omni-recommendation` | 인증 필요 + 본인 것만 |
| 게임→유저 역방향 조회 | 존재하지 않음 (로블록스 의도적 미제공) |

---

# 공통 제약 (실측)

## Rate Limit / 동시성
| 항목 | 실측값 |
|---|---|
| 순간 동시성 | 150까지 OK, **200부터 커넥션 강제 종료** (WinError 10054) |
| 지속 운용 | 동시성 4로 1시간 = 에러 0% |
| 안전 운영값 | **동시성 50~100** |
| E-2 멤버 조회 | 커서 순차라 **병렬 불가** (10,000명=65초, 100만명=1.8시간) |
| E-3 즐겨찾기 | 유저별 독립 → **병렬 가능** |

## 호출 실패 처리
- C-1: 간헐 500 → 2초 간격 재시도 2~3회
- D-1(Rolimons): 403/실패 → D-2로 폴백
- 전 API: User-Agent 헤더(브라우저 값) 포함 권장

## 타입 규칙 (DB 스키마 근거)
- 모든 ID (universeId, placeId, userId, groupId, imageId, videoAssetId): **BIGINT** (placeId 140조 실측)
- visits, favoritedCount, memberCount: **BIGINT**
- playing, votes, overlap 등 카운트: INT
- 시각 문자열: ISO8601 (소수부 자릿수 가변) → DATETIME(UTC)
- 커서: TEXT (296자 관찰)
- 게임명: 이모지 포함 → **utf8mb4 필수**
