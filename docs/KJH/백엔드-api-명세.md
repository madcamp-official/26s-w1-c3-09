# API 문서

> API 주소, 요청 방식, 요청값, 응답값, 에러 상황을 정리
> 공통: 응답은 전부 JSON. 성공 시 200. `{userId}` 등은 경로에 실제 값. (로블록스 외부 API는 `api-명세.md` 참고 — 이 문서는 우리 백엔드 ↔ 프론트 계약)

| Method | Endpoint | 설명 | 요청 | 응답 |
|---|---|---|---|---|
| GET | `/api/health` | 서버 생존 확인 (배포·연결 테스트) | 없음 | `{ "status": "ok" }` |
| GET | `/api/users/{username}/favorites` | 닉네임으로 유저 확인 + 즐겨찾기 목록 + 저장된 티어표 (1페이지) | Path: `username` (로블록스 닉네임, string) | `{ "userId": 4162489653, "username": "kim_chulsu", "favorites": [ { "universeId": 8360491918, "name": "Korea Army", "iconUrl": "https://..." } ], "savedTier": [ { "universeId": 8360491918, "tier": "SSS", "position": 1 } ] \| null }` |
| GET | `/api/search?q={검색어}` | 게임 이름 검색 (티어표 직접 추가용, 2페이지) | Query: `q` (검색어, string, 1자 이상) | `{ "results": [ { "universeId": 994732206, "name": "Blox Fruits", "playerCount": 265292, "iconUrl": "https://..." } ] }` (상위 10개) |
| PUT | `/api/tiers` | 티어표 저장 — 유저당 1세트 전체 덮어쓰기 (2페이지) | Body: `{ "userId": 4162489653, "entries": [ { "universeId": 8360491918, "tier": "SSS", "position": 1 } ] }` · tier ∈ SSS/A/B/C · SSS 최대 2개 · position은 티어 내 왼쪽부터 1 | `{ "ok": true, "saved": 7 }` |
| POST | `/api/recommend` | 추천 계산 실행 (2→3페이지). 티어표 기반 1·2단계 알고리즘 + 점수 계산, 결과 저장 후 반환 | Body: `{ "userId": 4162489653 }` | `{ "recommendations": [ { "rank": 1, "universeId": 855824334, "name": "Nonsan Training Center", "genreL1": "Military", "score": 8.43, "playerCount": 74, "iconUrl": "https://..." } ] }` (상위 N=20, 장르 필드로 프론트에서 그룹핑) |
| GET | `/api/recommendations/{userId}` | 마지막 추천 결과 재조회 — 상세에서 뒤로가기·재방문 시 복원 (재계산 없음) | Path: `userId` (number) | POST /api/recommend와 동일 형식. 저장된 결과 없으면 `{ "recommendations": [] }` |
| GET | `/api/games/{universeId}` | 게임 상세 (4페이지): 정보 + 스크린샷 + 개발자 영상 | Path: `universeId` (number) | `{ "universeId": 6035872082, "name": "[🏖️] RIVALS", "description": "...", "genreL1": "Shooter", "genreL2": "Deathmatch Shooter", "playing": 258585, "visits": 16250583479, "upVotes": 9919496, "downVotes": 633791, "minimumAge": 0, "screenshots": [ "https://..." ], "videoUrl": "https://fts.rbxcdn.com/..." \| null, "robloxUrl": "https://www.roblox.com/games/17625359962" }` · videoUrl은 만료 토큰 포함 — 매 요청마다 새로 발급됨 |
| GET | `/api/games/{universeId}/videos` | 유튜브 영상 목록 (4페이지 폴백 — 개발자 영상 없을 때) | Path: `universeId` (number) | `{ "videos": [ { "youtubeVideoId": "aB3xYz9kQw1", "title": "RIVALS 꿀팁 모음", "thumbnailUrl": "https://..." } ] }` · 재생은 프론트에서 `youtube.com/embed/{youtubeVideoId}` |

## 에러 상황

에러 응답 공통 형식: `{ "error": "코드", "message": "사람이 읽을 설명" }`

| 상태코드 | error 코드 | 발생 상황 | 관련 Endpoint |
|---|---|---|---|
| 404 | `USER_NOT_FOUND` | 존재하지 않는 로블록스 닉네임 | GET /api/users/... |
| 200 | — (`favorites: []` + `"favoritesEmpty": true`) | 즐겨찾기 0개 또는 비공개 (에러 아님 — 직접 추가 유도) | GET /api/users/... |
| 400 | `INVALID_TIER` | tier 값이 SSS/A/B/C 외 | PUT /api/tiers |
| 400 | `SSS_LIMIT` | SSS 3개 이상 배치 | PUT /api/tiers |
| 400 | `EMPTY_TIER` | entries가 빈 배열 (추천 불가) | PUT /api/tiers, POST /api/recommend |
| 404 | `NO_TIER` | 티어표 저장 안 된 유저의 추천 요청 | POST /api/recommend |
| 404 | `GAME_NOT_FOUND` | 존재하지 않는 universeId | GET /api/games/... |
| 429 | `BUSY` | 서버 로블록스 호출 예산 초과로 대기열 한도 도달 (드묾 — 잠시 후 재시도 안내) | POST /api/recommend |
| 502 | `ROBLOX_ERROR` | 로블록스 API가 재시도 후에도 실패 | 로블록스 호출하는 전부 |
| 500 | `INTERNAL` | 그 외 서버 오류 | 전부 |

## 참고 (구현 담당용)

| Endpoint | 내부 처리 (DB / 외부 API) |
|---|---|
| GET /api/users/{username}/favorites | 로블록스 A-1 → users UPSERT → 로블록스 E-3(실시간) → tier_entries 조회 → 미보유 게임 collect_queue INSERT |
| GET /api/search | 로블록스 A-3 (DB 미사용) |
| PUT /api/tiers | 검증 → 트랜잭션(tier_entries DELETE+INSERT) → 미보유 게임 collect_queue |
| POST /api/recommend | tier_entries 조회 → game_recommendations 캐시(미스 시 C-1 호출+저장, depth1~2 보장·depth3 캐시한정) → game_cofavorite 조회(상위 2게임) → 점수 계산 → user_recommendations 덮어쓰기 → games JOIN 응답 |
| GET /api/recommendations/{userId} | user_recommendations + games 조회만 |
| GET /api/games/{universeId} | games/game_media 조회(미스 시 B-1/B-2/B-3 호출+저장) → F-2 스크린샷 URL 변환 → F-3 영상 URL 발급 |
| GET /api/games/{universeId}/videos | game_videos 조회 → 미스 시 유튜브 G-1(하루 100회) → 저장 후 반환 |
