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
| GET | `/api/users/{username}/favorites?refresh=` | 닉네임으로 유저 확인 + 즐겨찾기 + 저장된 티어표 (1페이지). **캐시 우선** — 이미 조회한 적 있는 유저는 DB에서 즉시. `refresh=true`면 로블록스 재조회(무겁고 예산 소비 → **접속당 1회만**, 버튼 비활성화는 프론트가) | Path: `username` (로블록스 닉네임) · Query: `refresh` (선택, 기본 false) | `{ "userId": 4162489653, "username": "kim_chulsu", "favorites": [ { "universeId": 8360491918, "name": "Korea Army", "iconUrl": "https://..." } ], "favoritesEmpty": false, "savedTier": [ { "universeId": 8360491918, "tier": "SSS", "position": 1, "name": "Korea Army", "iconUrl": "https://..." } ] \| null }` · savedTier의 name·iconUrl = 재방문 시 티어 카드 렌더용(검색으로 담은 게임은 세션 메모리라 소실되므로) · favorites의 name은 games 조인 → 저장된 fav 이름 → 라이브 응답 순 폴백(삭제·비공개 게임도 이름 유지) |
| GET | `/api/search?q={검색어}` | 게임 이름 검색 (티어표 직접 추가용, 2페이지). 로블록스 실시간 검색 + 아이콘 보충. 빈 q는 빈 결과. **같은 검색어는 서버가 10분 캐싱**(Caffeine, 재호출 없음). 로블록스가 429를 주면 그 버킷 **30초 쿨다운** — 그동안 이 API는 BUSY(안내문구 detail 포함) | Query: `q` (1자 이상) | `{ "results": [ { "universeId": 994732206, "name": "Blox Fruits", "playerCount": 265292, "iconUrl": "https://..." } ] }` 상위 10개(scoring.json searchResultLimit). 아이콘 변환 실패 시 iconUrl null 가능 |
| PUT | `/api/tiers` | 티어표 저장 — 유저당 1세트 전체 덮어쓰기 (2페이지) | Body: `{ "userId": 4162489653, "entries": [ { "universeId": 8360491918, "tier": "SSS", "position": 1 } ] }` · tier ∈ SSS/A/B/C · SSS 최대 2개 · position은 티어 내 왼쪽부터 1 | `{ "ok": true, "saved": 7 }` |
| POST | `/api/recommend` | 추천 계산 실행 (2→3페이지). 티어 가중 합산 + 유명도 보정(섹션별 alpha) + 나이 보정, 결과 저장 후 반환. **두 섹션**: popular(인기, alpha 0.15)/discovery(발견, alpha 0.35) 각 50개(scoring.json sections). 같은 게임이 양쪽에 뜰 수 있음(중복 제거 안 함 — 확정). rank·score는 섹션 내 기준. **mode 지원**: 생략/`"normal"`=DB만(즉시, cofavorite만 — 덤 확장 없음), `"precise"`=정밀모드(아래, cofavorite + **연쇄추천 덤 확장**: 티어 게임의 People-Also-Join 6개를 후보로 추가 — 점수 = 최고 소스티어 가중치 × scoring.json derivedTierFactor(0.15), 유저 배치 게임이 덤보다 우선) | Body: `{ "userId": 4162489653, "mode": "normal" }` (mode 생략 가능) | normal: `{ "sections": { "popular": [ { "rank": 1, "universeId": 855824334, "name": "...", "genreL1": "Shooter", "genreL2": "Deathmatch Shooter", "score": 8.43, "playerCount": 74, "iconUrl": "https://..." } ], "discovery": [ 동일 형태 ] } }` · precise: `{ "jobId": "uuid", "status": "accepted" }` 즉시 반환 → status API로 폴링 |
| GET | `/api/recommend/status/{jobId}` | **정밀모드 진행률/결과 폴링** (2~3초 간격 권장). 정밀모드 = 티어표 **전체(SSS/A/B/C, 나이·동접 무관)**에 대해 ①연쇄추천(People-Also-Join) 캐싱 + ②미수집 그룹 게임 팬수집을 그 자리에서 수행 후 추천(연쇄추천 덤 확장 포함 — 아래 mode 설명) | Path: `jobId` | 진행중: `{ "status": "running", "progress": { "current": 2, "total": 5, "collectingName": "게임이름", "percent": 73 } }` · **정리중**: `{ "status": "finalizing", "message": "..." }` · 완료: `{ "status": "done", "sections": { "popular": [...], "discovery": [...] } }` · 오류: `{ "status": "error", "message": "..." }` · total=전체 티어 게임 수. **percent는 티어 중요도 × 작업 비용(연쇄추천 1 : 팬수집 표본수) 시간비례 가중** — 진행바는 percent 사용. 같은 유저가 다시 POST /api/recommend(precise)를 부르면 **진행 중인 잡의 기존 jobId가 그대로 반환**(재연결 — 나갔다 와도 이어 봄) |
| POST | `/api/recommend/cancel/{jobId}` | **정밀 분석 중단** — 지금까지 수집한 것만으로 **즉시** 추천 계산·완료(done). 현재 긁던 게임은 백그라운드에서 마저 수집(데이터 안 깨짐, 다음 번에 재사용). 프론트는 취소 응답 후 status 재조회하면 바로 done | Path: `jobId` | `{ "status": "cancelling" }` 반환 → 곧바로 status=done. 없는 jobId면 404 JOB_NOT_FOUND |
| GET | `/api/recommendations/{userId}` | 마지막 추천 결과 재조회 — 상세에서 뒤로가기·재방문 시 복원 (재계산 없음) | Path: `userId` | POST /api/recommend와 동일 형식. 없으면 `{ "sections": { "popular": [], "discovery": [] } }` |
| GET | `/api/games/{universeId}` | 게임 상세 (4페이지) | Path: `universeId` | `{ "universeId": 6035872082, "name": "[🏖️] RIVALS", "description": "...", "genreL1": "Shooter", "genreL2": "Deathmatch Shooter", "playing": 258585, "visits": 16250583479, "upVotes": 9919496, "downVotes": 633791, "minimumAge": 0, "releasedYear": 2024, "screenshots": ["https://tr.rbxcdn.com/..."], "iconUrl": "https://tr.rbxcdn.com/...", "videoUrl": null, "robloxUrl": "https://www.roblox.com/games/17625359962" }` · screenshots는 media 백필된 게임이면 URL 배열(실시간 변환), 아니면 빈 배열 · **iconUrl** = 게임 아이콘(스크린샷 없을 때 히어로 폴백용) · **releasedYear** = games.created의 연도(없으면 0) · **videoUrl은 항상 null** (개발자 영상 안 씀 — 확정) · DB에 없는 게임도 즉석 채움으로 응답 (단 스크린샷은 media 백필 후부터) |
| GET | `/api/games/{universeId}/videos` | 유튜브 영상 목록 (4페이지 폴백) | Path: `universeId` | `{ "videos": [ { "youtubeVideoId": "aB3xYz9kQw1", "title": "RIVALS 꿀팁 모음", "thumbnailUrl": "https://..." } ] }` · 재생은 `youtube.com/embed/{id}` |
| GET | `/api/games/{universeId}/similar` | 이 게임과 **함께 즐기는 게임** — 로블록스 People-Also-Join(연쇄추천 C-1). game_recommendations 캐시 우선, 없으면 즉석 1회 호출 후 저장(games_rec realtime 레인). 미보유 게임은 즉석 채움 — 항상 이름·장르·아이콘 완비 | Path: `universeId` | `{ "similar": [ { "universeId": 111, "name": "...", "genreL1": "Shooter", "playerCount": 1234, "iconUrl": "https://..." } ] }` 최대 6개(scoring.json similarCount) |

## 호출 특성 (프론트가 알아야 할 것)

로블록스 API 호출 예산이 매우 빡빡해서(상세: `backend/config/README.md`, `시행착오-기록.tex`), 엔드포인트마다 성격이 다르다:

| Endpoint | 속도 | 프론트가 대비할 것 |
|---|---|---|
| users/{username}/favorites (신규 유저 or refresh) | **1~2초 가능** (로블록스 2회 호출) | 로딩 표시 필수. 429(BUSY) 가능 → "잠시 후 재시도" 안내 |
| users/{username}/favorites (재방문 유저) | 즉시 (DB만) | — |
| search | **1초 안팎** (로블록스 검색+아이콘 2회 호출) | 로딩 표시. 429(BUSY) 가능 → "잠시 후 재시도" 안내. 타이핑마다 부르지 말고 엔터/버튼으로 |
| recommend | 보통 즉시, **미보유 후보가 많으면 1~4초** (즉석 채움 — 최초 1회만, 채운 게임은 영구 저장) | 로딩 표시 권장. 빈 결과 가능(수집 전) → "추천 준비 중" |
| games/{id} 상세 | 스크린샷 URL 변환으로 **0.5~3초** 가능 (media 있는 게임), 캐시미스 게임은 +1~2초(즉석 채움) | 로딩 표시 권장. 404는 로블록스에도 없는 게임 |
| games/{id}/similar | 보통 즉시, 미보유 게임 섞이면 1~3초 (즉석 채움) | — |
| 나머지 전부 (tiers, recommendations, videos*) | 즉시 (DB만) | BUSY 없음. *videos는 최초 1회만 유튜브 검색(~1초) |

## 에러 상황

| 상태코드 | error 코드 | 발생 상황 | 관련 Endpoint |
|---|---|---|---|
| 404 | `USER_NOT_FOUND` | 존재하지 않는 로블록스 닉네임 / 닉네임 조회를 거치지 않은 userId로 티어 저장 | GET /api/users/... · PUT /api/tiers |
| 200 | — (`favorites: []` + `favoritesEmpty: true`) | 즐겨찾기 0개 또는 비공개 (에러 아님 — 직접 추가 유도) | GET /api/users/... |
| 400 | `INVALID_TIER` | tier 값이 SSS/A/B/C 외 | PUT /api/tiers |
| 400 | `SSS_LIMIT` | SSS 3개 이상 배치 | PUT /api/tiers |
| 400 | `EMPTY_TIER` | entries가 빈 배열 | PUT /api/tiers |
| 404 | `NO_TIER` | 티어표 저장 안 된 유저의 추천 요청 | POST /api/recommend |
| 404 | `GAME_NOT_FOUND` | DB에 없는 universeId (수집 전 게임 포함) | GET /api/games/... |
| 429 | `BUSY` | 로블록스 호출 예산 소진 (드묾 — 잠시 후 재시도 안내) | 로블록스 호출하는 것들 |
| 404 | `JOB_NOT_FOUND` | 없는/만료된 정밀모드 jobId (서버 재시작 시 소멸 — 메모리 보관) | GET /api/recommend/status |
| 502 | `ROBLOX_ERROR` | 로블록스 API가 실패 | 로블록스 호출하는 것들 |
| 500 | `INTERNAL` | 그 외 서버 오류 | 전부 |

## 참고 (구현 담당용) — 내부 처리

| Endpoint | 내부 처리 (구현 기준) |
|---|---|
| GET /api/users/{username}/favorites | users에서 닉네임 캐시 조회 → 미스 시만 로블록스 POST usernames/users → users UPSERT → fav는 캐시 우선(fav_fetched_at 기준), 최초/refresh만 로블록스 조회 후 user_favorites 전체 교체 저장 → 미보유 게임 collect_queue INSERT → tier_entries 조회 |
| GET /api/search | 로블록스 omni-search 호출(apis_search 버킷, 광고 제외) → 상위 N개 아이콘 보충(thumb_icon) — DB 미사용. 예산 소진 시 429 BUSY |
| PUT /api/tiers | 검증(SSS≤2 등, 값은 scoring.json) → 트랜잭션(tier_entries DELETE+INSERT) → 미보유 게임 collect_queue |
| POST /api/recommend | tier_entries + user_favorites 조회 → game_cofavorite depth1 가중 합산(scoring.json 가중치) → 즐겨찾기 전부 후보 제외 → 섹션별 visits^α 보정(popular 0.15/discovery 0.35) × 나이 보정 + 동접 하한 → user_recommendations 덮어쓰기(section 컬럼 포함, 유저당 최대 100행) → games JOIN 응답. **로블록스 호출 0** (후보 즉석 채움 제외) |
| GET /api/recommendations/{userId} | user_recommendations + games 조회만 |
| GET /api/games/{universeId} | games 조회 → 미스 시 즉석 채움(detail+icon 실시간, realtime 레인) 후 응답, 그래도 없으면 404 → game_media Image imageIds를 thumbnails /v1/assets로 URL 변환(768x432) |
| GET /api/games/{universeId}/videos | game_videos 조회만 (유튜브 G-1 연동은 추후) |
| GET /api/games/{universeId}/similar | game_recommendations 캐시(rank순) → 미스 시 로블록스 C-1 즉석 호출+저장 → 미보유 게임 즉석 채움 → games JOIN |
| POST /api/recommend (공통) | raw 점수 상위 후보(candidateBackfillLimit)를 즉석 채움 — 추천 결과는 항상 이름·장르·아이콘 완비 · 나이보정(agePenalty 점 보간) 서버 적용 |

## 관련 문서

- 호출 예산·rate 상세: `backend/config/README.md` + `backend/config/rate_governance.json`
- 점수 계산 근거: `추천-알고리즘.tex`, 튜닝값: `backend/config/scoring.json`
- 설계 결정 이력: `의사결정-기록.md`
