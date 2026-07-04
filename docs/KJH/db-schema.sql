-- ============================================================
-- 로블록스 게임 추천 서비스 — DB 스키마 (MySQL 8.0)
-- 근거: 2026-07-03 API 실측 (docs/api-명세.md, 데이터-명세.md)
-- 타입 근거(실측 최대값):
--   placeId 140조 / visits 625억 / universeId 102억 / userId 73억 → BIGINT
--   playing 55만 / votes 1,200만 → INT
--   created "2024-05-26T16:47:48.617Z" (ISO8601) → DATETIME (UTC로 저장)
--   그룹 멤버 커서 296자 → TEXT
-- 로그인 없음. 단, 로블록스 닉네임 재입력 시 티어표를 재사용할 수 있도록
-- users + tier_entries에 마지막 티어표를 저장 (유저당 1개, 덮어쓰기)
-- 주의: 게임명에 이모지 포함 ("[🏖️] RIVALS") → utf8mb4 필수
-- ============================================================

-- ------------------------------------------------------------
-- 1. games — 게임 메타데이터 캐시 (모든 테이블의 중심)
--    출처: B-1(/v1/games) + B-2(votes) + F-1(아이콘)
--    주의: recommendations(C-1)는 장르·설명을 안 줌(실측) → 반드시 B-1로 채움
-- ------------------------------------------------------------
CREATE TABLE games (
    universe_id      BIGINT PRIMARY KEY,          -- B-1 id (실측 102억 → BIGINT)
    place_id         BIGINT NOT NULL,             -- rootPlaceId (실측 140조 → BIGINT)
    name             TEXT   NOT NULL,             -- 이모지 포함 가능 "[🏖️] RIVALS"
    description      TEXT,                        -- 905자+ 관찰 → TEXT
    genre_l1         VARCHAR(64),                 -- "Shooter"
    genre_l2         VARCHAR(64),                 -- "Deathmatch Shooter"
    playing          INT,                         -- 동접 (55만 관찰) — 유명도 보정·표시
    visits           BIGINT,                      -- 누적 방문 (625억 관찰)
    favorited_count  BIGINT,                      -- 8,200만 관찰, 증가 추세 → BIGINT
    up_votes         INT,
    down_votes       INT,
    creator_type     VARCHAR(8),                  -- 'Group' / 'User'
    creator_group_id BIGINT,                      -- creator.type='Group'일 때만 (2단계 팬 확장 입구)
    minimum_age      SMALLINT DEFAULT 0,
    icon_url         TEXT,                        -- F-1 CDN URL (만료 가능 → 주기 갱신)
    fan_cacheable    BOOLEAN,                     -- 팬 방식 적합 여부 (NULL=미판정)
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                     -- 주의: ON UPDATE 자동갱신 안 씀. 로블록스 재조회(B-1) 시에만
                     -- 코드가 명시적으로 갱신 (fan_cacheable 등 내부 컬럼 변경으로
                     -- 캐시가 신선한 척 되는 것 방지)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------
-- 2. game_media — 스크린샷 + 개발자 등록 영상 (게임 상세 페이지)
--    출처: B-3(/v2/games/{id}/media) + F-2(이미지 URL 변환)
--    실측 수정: videoId는 유튜브 ID가 아니라 로블록스 에셋 ID(14자리 숫자).
--              CDN URL은 만료 토큰 포함 → URL 저장 불가, 에셋 ID만 저장하고
--              재생 시 assetdelivery로 매번 URL 재발급.
-- ------------------------------------------------------------
CREATE TABLE game_media (
    universe_id    BIGINT   NOT NULL,
    sort_order     SMALLINT NOT NULL,             -- 응답 배열 순서
    asset_type     VARCHAR(32) NOT NULL,          -- 'Image' / 'GamePreviewVideo'
    image_id       BIGINT,                        -- 실측 119조 → BIGINT (F-2로 URL 변환)
    video_asset_id BIGINT,                        -- 로블록스 영상 에셋 ID (유튜브 아님!)
    fetched_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- B-3 수신 시각 (갱신 판단)
    PRIMARY KEY (universe_id, sort_order),
    FOREIGN KEY (universe_id) REFERENCES games(universe_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------
-- 3. game_recommendations — 연쇄 추천(People Also Join) 캐시
--    출처: C-1. 게임당 최대 6개 (rank 1~6).
--    depth 2 고정·겹침 가산·가중치는 전부 조회 코드에서 처리 (저장 안 함)
--    to_universe_id는 games에 아직 없을 수 있음 → FK 없음 (collect_queue로 채움)
--    주의: rank는 MySQL 8.0 예약어 → rec_rank로 명명
-- ------------------------------------------------------------
CREATE TABLE game_recommendations (
    from_universe_id BIGINT   NOT NULL,
    to_universe_id   BIGINT   NOT NULL,
    rec_rank         SMALLINT NOT NULL,           -- 1~6 (연관 강도)
    fetched_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (from_universe_id, to_universe_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------
-- 4. user_favorites — 유저별 즐겨찾기 풀 (원시, 크로스 게임 재사용)
--    출처: E-3(favorite/games). "어떤 게임의 팬"에 묶지 않고 유저 기준 저장
--    → RIVALS 팬으로 수집한 유저가 다른 게임 분석에도 재사용됨
--    recorded_at 1년 초과 행은 배치가 삭제 (오래된 취향 폐기)
-- ------------------------------------------------------------
CREATE TABLE user_favorites (
    user_id         BIGINT NOT NULL,              -- 실측 73억 → BIGINT
    fav_universe_id BIGINT NOT NULL,
    recorded_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, fav_universe_id),
    INDEX idx_user_favorites_game (fav_universe_id)  -- 역방향 조회 (이 게임을 즐겨찾기한 수집 유저)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------
-- 5. game_fans — 게임별 팬 명단 (그룹 가입 + 즐겨찾기 둘 다 확인된 유저)
--    출처: E-2(그룹멤버) × E-3(즐겨찾기) 대조로 판별
--    즐겨찾기 수 임계값 이상인 팬만 저장 (임계값은 코드 파라미터)
--    recorded_at 오래되면 재검증 대상
-- ------------------------------------------------------------
CREATE TABLE game_fans (
    seed_universe_id BIGINT NOT NULL,             -- 어느 게임의 팬인가
    fan_user_id      BIGINT NOT NULL,
    favorite_count   INT NOT NULL,                -- 이 팬의 즐겨찾기 수 (품질 지표)
    recorded_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (seed_universe_id, fan_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------
-- 6. game_cofavorite — 팬 공통 즐겨찾기 집계 (빠른 조회용 캐시)
--    user_favorites(원시)에서 배치로 계산. 공식 바뀌면 원시로 재계산 가능
--    overlap_count=1(1명만 겹침)은 노이즈 컷으로 저장 안 함
-- ------------------------------------------------------------
CREATE TABLE game_cofavorite (
    seed_universe_id    BIGINT NOT NULL,          -- 기준 게임
    related_universe_id BIGINT NOT NULL,          -- 팬들이 함께 즐겨찾기한 게임
    overlap_count       INT NOT NULL,             -- 겹친 팬 수 (가중치 원천)
    sample_size         INT NOT NULL,             -- 집계에 쓴 팬 수 (신뢰도·부분집계 표시)
    computed_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (seed_universe_id, related_universe_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------
-- 7. group_cursors — 그룹별 멤버 조회 커서 (그룹당 1행)
--    앵커(정착 유저 시작점)와 진행(중단 지점 이어받기)을 분리 저장
--    member_count: 그룹 정보 API(/v1/groups/{id}) 실측 1,895만 → BIGINT
-- ------------------------------------------------------------
CREATE TABLE group_cursors (
    group_id          BIGINT PRIMARY KEY,
    member_count      BIGINT,                     -- 총원 (X% 스킵 지점 계산용)
    sort_order        VARCHAR(4) NOT NULL DEFAULT 'Desc',  -- 'Asc'/'Desc'
    anchor_cursor     TEXT,                       -- 정착 유저 시작점 (실측 296자 → TEXT)
    progress_cursor   TEXT,                       -- 마지막 수집 중단 지점 (이어받기)
    fans_collected    INT NOT NULL DEFAULT 0,     -- 지금까지 확보한 팬 수
    collection_status VARCHAR(16) NOT NULL DEFAULT 'idle', -- idle/in_progress/complete
    updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                      ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------
-- 8. chart_snapshot — 인기 차트 (덮어쓰기, 이력 없음)
--    출처: D-3 Rolimons(주력) + D-1 explore-api(폴백)
--    snapshot_at은 PK가 아님 → 같은 (sort_id, universe_id)는 갱신 시 덮어씀
--    주의: rank는 MySQL 8.0 예약어 → chart_rank로 명명
-- ------------------------------------------------------------
CREATE TABLE chart_snapshot (
    sort_id     VARCHAR(64) NOT NULL,             -- 'rolimons' / 'most-popular' / 'trending-in-rpg' 등
    universe_id BIGINT      NOT NULL,
    chart_rank  INT         NOT NULL,             -- 차트 내 순위
    snapshot_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (sort_id, universe_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------
-- 9. collect_queue — 수집 대기열 (lazy population)
--    기록되는 4경우: ①마이너 게임 티어 배치(cofavorite 없음) ②유저 입력/즐겨찾기 중
--    DB에 없는 게임 ③연쇄 추천 결과 중 DB에 없는 게임 ④실시간 수집 시간초과 중단
--    status='partial'이면 group_cursors.progress_cursor부터 이어받기
-- ------------------------------------------------------------
CREATE TABLE collect_queue (
    universe_id  BIGINT PRIMARY KEY,
    reason       VARCHAR(32) NOT NULL,            -- 'user_tier'/'user_favorite'/'recommendation'/'timeout'
    status       VARCHAR(16) NOT NULL DEFAULT 'pending', -- pending/partial/done/failed
    requested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                 ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_collect_queue_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------
-- 10. users — 서비스를 이용한 로블록스 유저 (재방문 시 티어표 재사용)
--     로그인/비밀번호 없음. 닉네임 입력 → A-1로 userId 확인 → 이 테이블 조회.
--     PK는 로블록스 userId 그대로 사용 (불변). 닉네임은 바뀔 수 있어 표시용.
-- ------------------------------------------------------------
CREATE TABLE users (
    user_id      BIGINT PRIMARY KEY,              -- 로블록스 userId (실측 73억 → BIGINT)
    username     VARCHAR(50) NOT NULL,            -- 마지막으로 확인된 닉네임 (표시용)
    display_name VARCHAR(50),
    last_seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                 ON UPDATE CURRENT_TIMESTAMP      -- 마지막 이용 시각
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------
-- 11. tier_entries — 유저의 마지막 티어표 (유저당 1세트, 덮어쓰기)
--     재방문 시 이 표를 불러와 이어서 사용. 새로 즐겨찾기한 게임은
--     여기 없으므로 코드가 "미배치 게임"으로 구분해 보여줌.
--     SSS 최대 2개 제한은 코드에서 검증 (스키마 강제 아님)
-- ------------------------------------------------------------
CREATE TABLE tier_entries (
    user_id     BIGINT NOT NULL,
    universe_id BIGINT NOT NULL,
    tier        VARCHAR(4) NOT NULL,              -- 'SSS'/'A'/'B'/'C'
    position    SMALLINT NOT NULL DEFAULT 0,      -- 티어 내 표시 순서
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, universe_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------
-- 12. user_recommendations — 유저별 추천 결과 (유저당 1세트, 덮어쓰기)
--     용도: ①상세 페이지 갔다 돌아와도 목록 유지(재계산 불필요)
--           ②재방문 시 지난 추천 결과 다시 보기
--     추천을 새로 돌리면 해당 유저 행 전체 삭제 후 재삽입
-- ------------------------------------------------------------
CREATE TABLE user_recommendations (
    user_id     BIGINT NOT NULL,
    universe_id BIGINT NOT NULL,                  -- 추천된 게임
    score       DOUBLE NOT NULL,                  -- 알고리즘 최종 점수
    rec_rank    SMALLINT NOT NULL,                -- 표시 순위 1,2,3... (rank는 예약어)
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, universe_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------
-- 13. game_videos — 유튜브 영상 캐시 (F-10, 영상 1개당 1행)
--     개발자 영상(game_media.video_asset_id)이 없을 때의 폴백.
--     G-1은 하루 100회 한도 → 한 번 검색한 게임은 여기서만 조회 (할당량 절약)
--     제목·썸네일까지 저장해 표시 시 유튜브 재호출 없음
-- ------------------------------------------------------------
CREATE TABLE game_videos (
    universe_id      BIGINT NOT NULL,
    youtube_video_id VARCHAR(20) NOT NULL,        -- 유튜브 영상 ID (11자, 여유 20)
    title            VARCHAR(200),
    thumbnail_url    TEXT,
    display_order    SMALLINT NOT NULL DEFAULT 0,
    fetched_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (universe_id, youtube_video_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
