# API 문서 (프론트 ↔ 백엔드 계약)

> API 주소, 요청 방식, 요청값, 응답값, 에러 상황을 정리. **프론트 개발 시 이 문서가 기준.**
> 응답 필드명은 서버 `dto/` 패키지의 record 필드명과 1:1 — 의심되면 dto 파일이 최종 정답.

## 공통

- Base URL (로컬): `http://localhost:8080`
- CORS: `localhost:5173`(Vite) 허용돼 있음 — 프론트에서 그냥 fetch 하면 됨
- 응답은 전부 JSON. 성공 시 200. `{userId}` 등은 경로에 실제 값
- 에러 공통 형식: `{ "error": "코드", "message": "사람이 읽을 설명" }` + HTTP 상태코드
- 로블록스 외부 API는 `api-명세.md` 참고 (이 문서는 우리 백엔드 계약만)

## 엔드포인트

| Method | Endpoint | 설명 | 요청 | 응답 |
|---|---|---|---|---|
| GET | `/api/health` | 서버 생존 확인 | 없음 | `{ "status": "ok" }` |
| GET | `/api/users/{username}/favorites?refresh=` | 닉네임으로 유저 확인 + 즐겨찾기 + 저장된 티어표 (1페이지). **캐시 우선** — 이미 조회한 적 있는 유저는 DB에서 즉시. `refresh=true`면 로블록스 재조회(무겁고 예산 소비 → **접속당 1회만**, 버튼 비활성화는 프론트가) | Path: `username` (로블록스 닉네임) · Query: `refresh` (선택, 기본 false) | `{ "userId": 4162489653, "username": "kim_chulsu", "favorites": [ { "universeId": 8360491918, "name": "Korea Army", "iconUrl": "https://..." } ], "favoritesEmpty": false, "savedTier": [ { "universeId": 8360491918, "tier": "SSS", "position": 1 } ] \| null }` |
| GET | `/api/search?q={검색어}` | 게임 이름 검색 (티어표 직접 추가용, 2페이지). **⚠️ 미구현 — 현재 501 반환** | Query: `q` (1자 이상) | (예정) `{ "results": [ { "universeId": 994732206, "name": "Blox Fruits", "playerCount": 265292, "iconUrl": "https://..." } ] }` 상위 10개 |
| PUT | `/api/tiers` | 티어표 저장 — 유저당 1세트 전체 덮어쓰기 (2페이지) | Body: `{ "userId": 4162489653, "entries": [ { "universeId": 8360491918, "tier": "SSS", "position": 1 } ] }` · tier ∈ SSS/A/B/C · SSS 최대 2개 · position은 티어 내 왼쪽부터 1 | `{ "ok": true, "saved": 7 }` |
| POST | `/api/recommend` | 추천 계산 실행 (2→3페이지). 티어 가중 합산 + 유명도 보정, 결과 저장 후 반환. **⚠️ 응답 형태 개편 예정: 두 섹션(인기/발견)으로 분리 논의 중** — 확정 전까지 단일 리스트 | Body: `{ "userId": 4162489653 }` | `{ "recommendations": [ { "rank": 1, "universeId": 855824334, "name": "Nonsan Training Center", "genreL1": "Military", "score": 8.43, "playerCount": 74, "iconUrl": "https://..." } ] }` 상위 20 |
| GET | `/api/recommendations/{userId}` | 마지막 추천 결과 재조회 — 상세에서 뒤로가기·재방문 시 복원 (재계산 없음) | Path: `userId` | POST /api/recommend와 동일 형식. 없으면 `{ "recommendations": [] }` |
| GET | `/api/games/{universeId}` | 게임 상세 (4페이지) | Path: `universeId` | `{ "universeId": 6035872082, "name": "[🏖️] RIVALS", "description": "...", "genreL1": "Shooter", "genreL2": "Deathmatch Shooter", "playing": 258585, "visits": 16250583479, "upVotes": 9919496, "downVotes": 633791, "minimumAge": 0, "screenshots": [], "videoUrl": null, "robloxUrl": "https://www.roblox.com/games/17625359962" }` · **현재 screenshots는 빈 배열, videoUrl은 null** (URL 변환 구현 전 — 프론트는 없을 때의 표시를 기본으로) |
| GET | `/api/games/{universeId}/videos` | 유튜브 영상 목록 (4페이지 폴백) | Path: `universeId` | `{ "videos": [ { "youtubeVideoId": "aB3xYz9kQw1", "title": "RIVALS 꿀팁 모음", "thumbnailUrl": "https://..." } ] }` · 재생은 `youtube.com/embed/{id}` |
| GET | `/api/games/{universeId}/similar` | **(신규 예정 — BMS 작업 1)** 이 게임과 함께 즐겨찾기된 상위 6개 (4페이지 "비슷한 게임") | Path: `universeId` | (예정) `{ "similar": [ { "universeId": 111, "name": "...", "iconUrl": "https://..." } ] }` 최대 6개 |

## 호출 특성 (프론트가 알아야 할 것)

로블록스 API 호출 예산이 매우 빡빡해서(상세: `backend/config/README.md`, `시행착오-기록.tex`), 엔드포인트마다 성격이 다르다:

| Endpoint | 속도 | 프론트가 대비할 것 |
|---|---|---|
| users/{username}/favorites (신규 유저 or refresh) | **1~2초 가능** (로블록스 2회 호출) | 로딩 표시 필수. 429(BUSY) 가능 → "잠시 후 재시도" 안내 |
| users/{username}/favorites (재방문 유저) | 즉시 (DB만) | — |
| 나머지 전부 | 즉시 (DB만) | BUSY 없음 |
| recommend / games/{id} | 즉시, 단 **빈 결과·404가 정상일 수 있음** (배치가 데이터 채우기 전) | 빈 상태 화면 필요: "추천 준비 중" / "게임 정보 수집 중" |

## 에러 상황

| 상태코드 | error 코드 | 발생 상황 | 관련 Endpoint |
|---|---|---|---|
| 404 | `USER_NOT_FOUND` | 존재하지 않는 로블록스 닉네임 | GET /api/users/... |
| 200 | — (`favorites: []` + `favoritesEmpty: true`) | 즐겨찾기 0개 또는 비공개 (에러 아님 — 직접 추가 유도) | GET /api/users/... |
| 400 | `INVALID_TIER` | tier 값이 SSS/A/B/C 외 | PUT /api/tiers |
| 400 | `SSS_LIMIT` | SSS 3개 이상 배치 | PUT /api/tiers |
| 400 | `EMPTY_TIER` | entries가 빈 배열 | PUT /api/tiers |
| 404 | `NO_TIER` | 티어표 저장 안 된 유저의 추천 요청 | POST /api/recommend |
| 404 | `GAME_NOT_FOUND` | DB에 없는 universeId (수집 전 게임 포함) | GET /api/games/... |
| 429 | `BUSY` | 로블록스 호출 예산 소진 (드묾 — 잠시 후 재시도 안내) | 로블록스 호출하는 것들 |
| 501 | `NOT_IMPLEMENTED` | 아직 구현 안 된 기능 | GET /api/search |
| 502 | `ROBLOX_ERROR` | 로블록스 API가 실패 | 로블록스 호출하는 것들 |
| 500 | `INTERNAL` | 그 외 서버 오류 | 전부 |

## 참고 (구현 담당용) — 내부 처리

| Endpoint | 내부 처리 (구현 기준) |
|---|---|
| GET /api/users/{username}/favorites | users에서 닉네임 캐시 조회 → 미스 시만 로블록스 POST usernames/users → users UPSERT → fav는 캐시 우선(fav_fetched_at 기준), 최초/refresh만 로블록스 조회 후 user_favorites 전체 교체 저장 → 미보유 게임 collect_queue INSERT → tier_entries 조회 |
| GET /api/search | (예정) 로블록스 omni-search 호출 (apis_search 버킷) — DB 미사용 |
| PUT /api/tiers | 검증(SSS≤2 등, 값은 scoring.json) → 트랜잭션(tier_entries DELETE+INSERT) → 미보유 게임 collect_queue |
| POST /api/recommend | tier_entries + user_favorites 조회 → game_cofavorite depth1 가중 합산(scoring.json 가중치) → 즐겨찾기 전부 후보 제외 → visits^α 보정 + 동접 하한 → user_recommendations 덮어쓰기 → games JOIN 응답. **로블록스 호출 0** |
| GET /api/recommendations/{userId} | user_recommendations + games 조회만 |
| GET /api/games/{universeId} | games/game_media 조회만 (미스 시 404 — 단건 실시간 조회는 추후). 스크린샷 URL 변환·영상 URL 발급은 미구현 |
| GET /api/games/{universeId}/videos | game_videos 조회만 (유튜브 G-1 연동은 추후) |
| GET /api/games/{universeId}/similar | (예정) game_cofavorite 상위 6 → games JOIN — DB만 |

## 관련 문서

- 호출 예산·rate 상세: `backend/config/README.md` + `backend/config/rate_governance.json`
- 점수 계산 근거: `추천-알고리즘.tex`, 튜닝값: `backend/config/scoring.json`
- 설계 결정 이력: `의사결정-기록.md`
