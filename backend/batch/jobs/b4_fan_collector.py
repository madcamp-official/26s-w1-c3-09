"""
B4. 유저 즐겨찾기 수집 (가장 무거움). (담당: KJH) — B-2/B-3/C-1/D-1/G 확정 설계

파이프라인:
  1) collection.json 확장 사다리를 위에서부터 훑어 "수집할 게임이 있는 첫 단계"를 찾음
     (진행상태는 group_cursors가 게임별로 관리 — 전역 단계 저장 안 함)
  2) 그 단계의 자격 게임(그룹소유+신생+동접+fan_cacheable≠false)을 인기순으로 선별
  3) 게임마다: 그룹 멤버 Asc(오래된순)로 sampleSize까지 순회 → 각자 즐겨찾기 무조건 저장
     - 이미 수집된 유저(user_favorites에 있음)는 재조회 생략 (D-1 크로스 재사용)
     - progress_cursor로 이어받기 (Asc라 밀림 없음 — 실측)
     - 초반 probe명 즐겨찾기 보유율 < 임계면 fan_cacheable=false → 그 게임 중단 (낭비 방지)
  4) 게임별 cofavorite 집계: user_favorites 셀프조인 (X 즐겨찾기한 유저가 함께 즐겨찾기한 게임)

실행: conda activate mad1 && python -m jobs.b4_fan_collector
"""
import asyncio

from common.db import cursor
from common.logger import get_logger
from common.rate_limiter import RateLimiter
from common.roblox_api import RobloxApi
from config import load_collection, load_rate_governance

log = get_logger("b4_fan_collector")

_ops = load_rate_governance()["operations"]
MEMBER_PAGE = _ops["getGroupMembers"]["batchSize"]   # 페이지당 멤버 수 (실측 — config가 진실)
FAV_PAGE = _ops["getFavorites"]["pageSize"]          # fav 응답 최대 개수


def _age_days(years):
    return int(years * 365.25)


def find_stage_and_games(cur, ladder, floor, per_run):
    """사다리를 위에서부터 훑어 수집할 게임이 있는 첫 단계 반환. (stage, sample, games) or None."""
    for step in ladder:
        sample = step["sampleSize"]
        cur.execute(
            "SELECT g.universe_id, g.creator_group_id, "
            "       COALESCE(gc.users_collected, 0) AS collected, gc.progress_cursor "
            "FROM games g "
            "LEFT JOIN group_cursors gc ON gc.group_id = g.creator_group_id "
            "WHERE g.creator_type = 'Group' AND g.creator_group_id IS NOT NULL "
            "  AND g.playing >= %s "
            "  AND g.created >= (NOW() - INTERVAL %s DAY) "
            "  AND (g.fan_cacheable IS NULL OR g.fan_cacheable = TRUE) "
            "  AND COALESCE(gc.users_collected, 0) < %s "
            # 소진 완료(complete)된 그룹은 제외 — 멤버가 sample보다 적은 작은 그룹이
            # users_collected<sample 조건에 영영 걸려 재수집되는 '좀비'를 막음(사다리 정체 방지).
            "  AND (gc.collection_status IS NULL OR gc.collection_status <> 'complete') "
            "ORDER BY g.favorited_count DESC "
            "LIMIT %s",
            (floor, _age_days(step["maxAgeYears"]), sample, per_run),
        )
        rows = cur.fetchall()
        if rows:
            return step["stage"], sample, rows
    return None


async def _members_page(api, group_id, group_cursor):
    """그룹 멤버 한 페이지(Asc 100명). (userIds, next_cursor, ok).
    ok=False → 조회 실패(비공개 그룹 등). 빈 그룹(ok=True, ids=[])과 구분."""
    url = f"https://groups.roblox.com/v1/groups/{group_id}/users?limit={MEMBER_PAGE}&sortOrder=Asc"
    if group_cursor:
        url += f"&cursor={group_cursor}"
    d = await api.get("groups_members", url)
    if d is None:
        return [], None, False   # 멤버 비공개/차단 등 → 조회 불가
    ids = [m["user"]["userId"] for m in d.get("data", [])]
    return ids, d.get("nextPageCursor"), True


def _already_collected(cur, user_ids):
    """user_favorites에 이미 있는 user_id 집합 (재조회 생략용)."""
    if not user_ids:
        return set()
    placeholders = ",".join(["%s"] * len(user_ids))
    cur.execute(
        f"SELECT DISTINCT user_id FROM user_favorites WHERE user_id IN ({placeholders})",
        tuple(user_ids),
    )
    return {r["user_id"] for r in cur.fetchall()}


async def collect_game(api, game, sample, cfg):
    """한 게임의 그룹 멤버 즐겨찾기 수집. group_cursors 갱신 + fan_cacheable 판정.
    반환: (신규 저장 유저 수, 최종 users_collected, fan_cacheable).

    성능(핵심): fav 조회를 페이지(100명) 단위로 '동시에' 발사 — rate_limiter가
    games_fav 상한(여유 반영 ~5.3/s)으로 스페이싱하므로 429 없이 측정 최대치를 씀.
    DB 쓰기도 페이지당 1회(executemany) — 유저당 연결 오버헤드 제거."""
    uid = game["universe_id"]
    group_id = game["creator_group_id"]
    collected = game["collected"]
    group_cursor = game["progress_cursor"]

    probe_n = cfg["fanCacheableProbe"]
    probe_has = 0
    probe_total = 0
    new_users = 0
    fan_cacheable = None

    async def fetch_fav(mid):
        d = await api.get("games_fav",
                          f"https://games.roblox.com/v2/users/{mid}/favorite/games?limit={FAV_PAGE}")
        if d is None:
            return mid, None                      # 조회 실패(비공개 등) — 빈 것과 구분
        return mid, [g["id"] for g in d.get("data", []) if g.get("id")]

    # 페이지 프리페치: fav 조회(병목) 동안 다음 members 페이지를 미리 받음
    # (members 7.3/s와 fav 6.1/s는 독립 버킷 — 겹쳐 써도 서로 안 뺏음, 실측 A-1)
    next_page = asyncio.create_task(_members_page(api, group_id, group_cursor))
    while collected < sample:
        member_ids, next_cursor, ok = await next_page
        if not ok:
            # 멤버 비공개/차단 그룹 → 팬 수집 불가. 첫 시도면 fan_cacheable=false 마킹(재시도 낭비 방지)
            if collected == 0:
                with cursor() as cur:
                    cur.execute("UPDATE games SET fan_cacheable=FALSE WHERE universe_id=%s", (uid,))
                log.info("  %s 멤버 비공개/조회불가 → fan_cacheable=false, 스킵", uid)
                fan_cacheable = False
            break
        if not member_ids:
            # 멤버 소진(빈 페이지) — sample 미달이어도 complete로 확정 (좀비 방지).
            # 100/200배수 그룹은 마지막에 빈 페이지가 오는데, 이때 status가 in_progress로
            # 남으면 재선정 조건에 계속 걸림. 여기서 못박아 다음 사이클부터 제외되게 함.
            with cursor() as cur:
                cur.execute(
                    "INSERT INTO group_cursors (group_id, sort_order, progress_cursor, "
                    "  users_collected, collection_status) VALUES (%s, 'Asc', %s, %s, 'complete') "
                    "ON DUPLICATE KEY UPDATE collection_status='complete'",
                    (group_id, group_cursor, collected))
            break   # 빈 그룹(끝) — 정상 종료

        take = member_ids[: sample - collected]   # 표본 초과분 컷 (page 정렬과 무관하게 안전)

        # 다음 페이지 프리페치 시작 (이번 페이지 fav 조회와 병렬로 진행)
        if next_cursor and collected + len(take) < sample:
            next_page = asyncio.create_task(_members_page(api, group_id, next_cursor))

        with cursor() as cur:
            seen = _already_collected(cur, take)
        unseen = [m for m in take if m not in seen]

        # ★ 페이지의 미수집 유저 fav를 동시에 — rate_limiter가 상한으로 조절
        results = await asyncio.gather(*(fetch_fav(m) for m in unseen))

        fav_map = {}
        rows = []
        for mid, favs in results:
            fav_map[mid] = favs                    # None=실패 / []=없음 / [ids]
            if favs:
                new_users += 1
                rows.extend((mid, g) for g in favs)
        if rows:
            with cursor() as cur:                  # 페이지당 1회 배치 삽입
                cur.executemany(
                    "INSERT IGNORE INTO user_favorites (user_id, fav_universe_id) VALUES (%s, %s)",
                    rows)

        # probe 집계 (페이지 순서대로): 이미 수집됨=보유, 신규는 결과로
        for mid in take:
            if probe_total >= probe_n:
                break
            probe_total += 1
            if mid in seen or fav_map.get(mid):
                probe_has += 1

        collected += len(take)

        # 진행상황 저장 (커서·수집수) — 중단돼도 이어받기
        group_cursor = next_cursor
        with cursor() as cur:
            cur.execute(
                "INSERT INTO group_cursors (group_id, sort_order, progress_cursor, "
                "  users_collected, collection_status) "
                "VALUES (%s, 'Asc', %s, %s, %s) "
                "ON DUPLICATE KEY UPDATE progress_cursor=VALUES(progress_cursor), "
                "  users_collected=VALUES(users_collected), collection_status=VALUES(collection_status)",
                (group_id, group_cursor, collected,
                 "complete" if collected >= sample or not next_cursor else "in_progress"))

        # fan_cacheable 판정 (probe 채워지면 1회)
        if fan_cacheable is None and probe_total >= probe_n:
            rate = probe_has / probe_total
            fan_cacheable = rate >= cfg["fanCacheableThreshold"]
            with cursor() as cur:
                cur.execute("UPDATE games SET fan_cacheable=%s WHERE universe_id=%s",
                            (fan_cacheable, uid))
            if not fan_cacheable:
                log.info("  %s fan_cacheable=false (보유율 %.0f%%) → 중단", uid, rate * 100)
                break   # 부실 게임 → 남은 수집 낭비 방지

        if not next_cursor:   # 그룹 멤버 소진
            break

    if not next_page.done():   # 중단 경로에서 남은 프리페치 정리
        next_page.cancel()
    return new_users, collected, fan_cacheable


def aggregate_cofavorite(cur, seed_uid, min_overlap):
    """seed 게임을 즐겨찾기한 유저들이 함께 즐겨찾기한 게임 집계 → game_cofavorite.
    (D-2: game_fans 없이 user_favorites 셀프조인. '팬'=seed를 즐겨찾기한 유저)"""
    cur.execute("SELECT COUNT(*) AS n FROM user_favorites WHERE fav_universe_id=%s", (seed_uid,))
    sample_size = cur.fetchone()["n"]
    if sample_size == 0:
        return 0
    cur.execute("DELETE FROM game_cofavorite WHERE seed_universe_id=%s", (seed_uid,))
    cur.execute(
        "INSERT INTO game_cofavorite "
        "  (seed_universe_id, related_universe_id, overlap_count, sample_size) "
        "SELECT %s, uf2.fav_universe_id, COUNT(*), %s "
        "FROM user_favorites uf1 "
        "JOIN user_favorites uf2 ON uf1.user_id = uf2.user_id "
        "WHERE uf1.fav_universe_id = %s AND uf2.fav_universe_id <> %s "
        "GROUP BY uf2.fav_universe_id "
        "HAVING COUNT(*) >= %s",
        (seed_uid, sample_size, seed_uid, seed_uid, min_overlap),
    )
    return cur.rowcount


async def run():
    cfg = load_collection()["fanCollection"]
    ladder = cfg["expansionLadder"]
    floor = cfg["playingFloor"]
    per_run = cfg["maxGamesPerRun"]
    min_overlap = cfg["minOverlap"]

    rl = RateLimiter()
    log.info("=== B4 팬 수집 시작 ===")
    with cursor() as cur:
        found = find_stage_and_games(cur, ladder, floor, per_run)
    if not found:
        log.info("모든 단계 완료 — 수집할 게임 없음")
        return
    stage, sample, games = found
    log.info("단계 %d (표본 %d) — 대상 %d게임", stage, sample, len(games))

    async with RobloxApi(rl) as api:
        for game in games:
            new_u, collected, fc = await collect_game(api, game, sample, cfg)
            with cursor() as cur:
                cofav_n = aggregate_cofavorite(cur, game["universe_id"], min_overlap)
            log.info("  게임 %s: 수집 %d명(신규 %d), fan_cacheable=%s, cofav %d관계",
                     game["universe_id"], collected, new_u, fc, cofav_n)

    log.info("=== 완료: 단계 %d, %d게임 처리 ===", stage, len(games))


if __name__ == "__main__":
    asyncio.run(run())
