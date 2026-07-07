# backend/config — server(Java)·batch(Python) 공용 설정

> JSON은 주석을 못 쓰므로 필드 설명을 여기에 둔다.
> **모든 운영·튜닝 수치는 코드가 아니라 이 폴더의 json에** (하드코딩 금지 원칙).
> `ratePerS`는 실측값 — 재측정 시 그 필드만 갱신하면 정책(lanes·margin)은 안 덮인다.
> 근거·실측 과정: `docs/KJH/의사결정-기록.md` (A: rate limit, E~G: 알고리즘·레인·수집)

## rate_governance.json — 로블록스 호출 예산

### defaults
| 필드 | 값 | 의미 |
|---|---|---|
| `margin` | 0.125 | 여유 비율 — 가용 rate = ratePerS × (1−margin). 429 페널티 30초+ 예방 (A-2) |
| `burstSeconds` | 2.0 | 서버 토큰버킷 버스트 용량(초) — 순간 몰림 흡수 |
| `aimd.*` | startFraction 0.7 등 | 배치 AIMD 파라미터. 서버는 backoffSeconds(429 대기)만 공유 |
| `http.*` | maxRetries 5 등 | HTTP 재시도/타임아웃 — 배치 python과 서버 블로킹 호출이 같은 값 사용 |

### buckets (측정된 사실 — 로블록스가 실제로 막는 단위)
rate limit은 **엔드포인트별 독립 토큰버킷** (A-1 실측). 단 **users 계열 3개 경로는 한 버킷을 공유**(클린 재측정 확인 — 유일한 예외).

| 필드 | 의미 |
|---|---|
| `ratePerS` | 실측 지속 rate (호출/s). 재측정 시 여기만 갱신 |
| `margin` | 버킷별 여유 (없으면 defaults.margin) |
| `lanes` | 레인 정책 (아래) |
| `note` | 측정 신뢰도·운용 지침 |

### lanes (우리 정책 — 레인 분배, G-6)
우선순위 realtime(1) > precise(2) > batch(3). `floor` = 그 레인 최소 보장 rate.

- **realtime(서버)**: floor만큼 전용 버킷. floor 없으면 그 버킷의 가용 전체 사용(유일 소비자란 뜻 — apis_search 등)
- **precise(서버 정밀모드)**: 예약 없이 batch보다 우선. 몫 = 가용 − 타 레인 floor 합
- **batch(python)**: 몫 = 가용 − 타 레인 floor 합 (단일 코드 — EC2처럼 서버와 같은 IP에서 돌 때 유저 몫을 구조적으로 보장. 서버 없는 기숙사에선 floor만큼 덜 쓰지만 코드 분기 없음)

### operations (논리 작업 → 버킷 매핑 + 실측 배치 상한)
| 필드 | 의미 |
|---|---|
| `bucket` | 이 작업이 소비하는 버킷. 여러 작업이 같은 버킷이면 예산을 나눠 씀 (users 계열) |
| `batchSize` | 호출당 최대 ID 수 (실측: detail 50, votes/icon/thumb/users 100). 1 = 경로형 단건 |
| `pageSize` | 페이지형 응답 최대 개수 (fav 50) |

실효 처리량 = rate × batchSize (예: detail 1.1/s × 50 = ~55게임/s).
**java(RobloxApiClient)·python(b2/b4) 모두 이 값을 읽는다 — 코드에 중복 정의 금지.**

## scoring.json — 추천 점수 튜닝값 (F-7)

```
raw(c) = Σ_g 티어가중치(g) × overlap(g, c)     ← g = 티어표 게임 + 미배치 즐겨찾기
final  = raw / visits^alpha × ageFactor         ← 유명도 보정(섹션별) × 나이 보정(G-5)
```

| 필드 | 값 | 의미 |
|---|---|---|
| `tierWeights` | SSS 5.5 / A 3 / B 2 / C 1 | SSS는 특별 자리(2게임 한정). SS·S 등급 없음 |
| `unplacedFavoriteWeight` | 0.3 | 미배치 즐겨찾기 = 약한 양의 신호 |
| `sssMaxCount` | 2 | SSS 배치 상한 (서비스 계층 검증) |
| `excludeUserFavoritesFromCandidates` | true | 유저의 모든 즐겨찾기(미배치 포함) 후보 제외 |
| `sections.{popular,discovery}.alpha` | 0.15 / 0.35 | 유명도 보정 강도. **겹침 42/50 관측 — 튜닝 예정(E-4)** |
| `sections.{...}.count` | 50 | 섹션당 응답 개수 (프론트는 배열 길이 그대로 렌더링 — 하드코딩 금지) |
| `playingFloor` | 100 | 동접 하한 — 죽은 게임 필터 (E-3) |
| `minOverlap` | 2 | cofavorite 최소 겹침 (노이즈 컷) |
| `similarCount` | 6 | 상세 "비슷한 게임" 개수 |
| `searchResultLimit` | 10 | 검색 결과 상위 개수 |
| `candidateBackfillLimit` | 100 | 추천 후보 즉석 채움 상한 (raw 상위 N) |
| `shortsPerGame` | 7 | 유튜브 쇼츠 게임당 검색 수 (쿼터: search 100유닛/회, 일 10,000) |
| `progressPositionRange` | 0.25 | 정밀모드 진행률 위치 가중 폭(±). 같은 등급 안에서 왼쪽 끝 ×(1+range)…오른쪽 끝 ×(1-range). 0.25면 SSS·A 등급 안 뒤집힘(5.5×0.75 > 3×1.25) |
| `agePenalty.points` | [3,1.0]…[9,0.68] | 게임 나이(년)→점수 배수. 점 사이 선형보간, 범위 밖 양끝 고정 |

역신호(음수 가중치)는 안 씀 — 전 가중치 양수 (F-7).

## collection.json — 수집 정책

| 필드 | 값 | 의미 |
|---|---|---|
| `queue.batchPerRun` | 200 | b2 실행당 큐 소비 게임 수 |
| `queue.mediaBackfillPerRun` | 300 | b2 실행당 media 백필 게임 수 (인기순) |
| `refresh.gamesAfterHours` | 24 | games 수치(동접·방문) 신선도 기준 |
| `refresh.gamesPerRun` | 300 | b2 실행당 수치 갱신 게임 수 (인기순) |
| `fanCollection.playingFloor` | 100 | 팬수집 자격 동접 하한 |
| `fanCollection.fanCacheableThreshold` | 0.30 | probe 보유율 미만이면 부실 그룹 → 중단 (D-1: 거의 안 걸러지게) |
| `fanCollection.fanCacheableProbe` | 50 | 판정 관찰 인원 |
| `fanCollection.maxGamesPerRun` | 100 | b4 실행당 게임 수 |
| `fanCollection.preciseFavWorkers` | 8 | 정밀모드 fav 병렬 스레드 |
| `fanCollection.expansionLadder` | 6단계 | 2y/200 → 2.5y → 3y → 3y/500(deepen) → 3.5y → 3.5y/500. 위에서부터 훑어 첫 단계 처리 |

fanCollection은 **배치 b4와 서버 정밀모드가 같은 값**을 쓴다 (자격 기준이 어긋나지 않게).

## 로딩 방식
- **server**: `ConfigFileLoader`가 부팅 시 로드 (`application.yaml`의 `madfinder.config-dir`, 기본 `../config`)
- **batch**: `backend/batch/config.py`의 load_rate_governance()/load_collection()
- 값 변경 후 서버/배치 재시작 필요 (핫리로드 없음). docker 이미지엔 빌드 시 복사됨(COPY config)

## 값 변경 규칙
- 새 항목 추가·값 변경은 **사전 논의·확정 후** (임의값 금지)
- 같은 값을 코드에 다시 적지 말 것 (중복 정의 금지 — json이 유일한 진실)
