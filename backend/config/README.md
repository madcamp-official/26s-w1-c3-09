# backend/config — server(Java)·batch(Python) 공용 설정

> JSON은 주석을 못 쓰므로 필드 설명을 여기에 둔다.
> **측정값(measured)과 정책(policy)을 분리**: rate 재측정 시 measured만 갱신, 튜닝값은 안 덮인다.
> 근거·실측 과정: `docs/KJH/의사결정-기록.md` (A: rate limit, E~F: 알고리즘·경계)

## rate_governance.json — 로블록스 호출 예산

### buckets (측정된 사실 — 로블록스가 실제로 막는 단위)
rate limit은 **엔드포인트별 독립 토큰버킷** (A-1 실측). 단 **users 계열 3개 경로는 한 버킷을 공유**(실측 — 유일한 예외).

| 필드 | 의미 |
|---|---|
| `ratePerS` | 실측 지속 rate (호출/s). 재측정 시 여기만 갱신 |
| `margin` | 여유 비율 (없으면 `defaults.margin`=0.15). 429 페널티 30초+ 예방 (A-2) |
| `realtimeFloor` | 실시간 레인 예약 바닥(호출/s). 배치가 침범 못 함. 없으면 서버가 가용 전체 사용 |
| `note` | 측정 신뢰도·운용 지침 |

가용 rate = `ratePerS × (1 - margin)`. 레인 규칙(F-4/F-5):
- **실시간(서버)**: `realtimeFloor`만큼 보장 (신규 유저 몫 — username 하드캡 1.2/s에 맞춤)
- **배치(Python)**: 나머지 전부 work-conserving (실시간이 안 쓰면 다 씀, 수요 오면 즉시 양보)

### operations (논리 작업 → 버킷 매핑)
| 필드 | 의미 |
|---|---|
| `bucket` | 이 작업이 소비하는 버킷. **여러 작업이 같은 버킷이면 예산을 나눠 씀** (users 계열) |
| `batchSize` | 호출당 최대 ID 수 (실측: detail 50, votes/icon/thumb/users 100). 1 = 경로형 단건 |

실효 처리량 = rate × batchSize (예: detail 1.1/s × 50 = ~55게임/s → 병목은 캐싱으로 회피).

### 재측정 예정
- `users_lookup`: 측정이 앞 테스트 429 오염 — 정밀값·usernames 배치상한 클린 재측정 필요
- `resolveUsernames`의 batchSize 100은 미검증 추정

## scoring.json — 추천 점수 튜닝값 (F-7)

```
raw(c) = Σ_g 티어가중치(g) × overlap(g, c)     ← g = 티어표 게임 + 미배치 즐겨찾기
final  = raw / visits^alpha                     ← 유명도 보정 (E-2 두 섹션)
```

| 필드 | 값 | 의미 |
|---|---|---|
| `tierWeights` | SSS 5.5 / A 3 / B 2 / C 1 | SSS는 특별 자리(2게임 한정). SS·S 등급 없음 |
| `unplacedFavoriteWeight` | 0.3 | 미배치 즐겨찾기 = 약한 양의 신호 (즐겨찾기 자체가 선호 — 배치 안 한 건 귀찮았을 뿐) |
| `sssMaxCount` | 2 | SSS 배치 상한 (서비스 계층 검증) |
| `excludeUserFavoritesFromCandidates` | true | 유저의 모든 즐겨찾기(미배치 포함)를 추천 후보에서 제외 |
| `sections.*.alpha` | popular 0.15 / discovery 0.35 | 유명도 보정 강도. **E-4: 실데이터 후 튜닝 예정** |
| `playingFloor` | 100 | 동접 하한 — 죽은 게임 필터 (E-3) |
| `minOverlap` | 2 | cofavorite 최소 겹침 (노이즈 컷) |
| `topN` | 20 | 추천 응답 상위 개수 |

역신호(음수 가중치)는 안 씀 — 전 가중치 양수 (F-7).

## 로딩 방식
- **server**: `ConfigFileLoader`가 부팅 시 로드 (`application.yaml`의 `madfinder.config-dir`, 기본 `../config`)
- **batch**: `backend/batch/config.py`에서 로드 (구현 예정)
- 값 변경 후 서버 재시작 필요 (핫리로드 없음)
