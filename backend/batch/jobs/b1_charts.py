"""
B1. 인기 차트 수집. (담당: KJH) — B-4 확정: explore 최우선(매일), Rolimons 후순위(주1회 보강)
1) explore-api get-sorts 로 차트 종류 나열 → get-sort-content 로 각 차트 게임 (apis_explore 버킷)
2) chart_snapshot 덮어쓰기 (INSERT ... ON DUPLICATE KEY UPDATE)
3) 새로 등장한(games/collect_queue 둘 다 없는) 게임 → collect_queue 등록 (reason='chart')

Rolimons 보강(주1회)은 별도 실행 인자로 (기본은 explore만).
실행: conda activate mad1 && python -m jobs.b1_charts
"""
import asyncio
import time

from common.db import cursor
from common.logger import get_logger
from common.rate_limiter import RateLimiter
from common.roblox_api import RobloxApi

log = get_logger("b1_charts")
EXPLORE = "apis_explore"
BASE = "https://apis.roblox.com/explore-api/v1"


async def fetch_sort_ids(api, session_id):
    """get-sorts 페이지네이션 → 차트 sortId 목록."""
    sort_ids = []
    token = None
    for _ in range(10):
        url = f"{BASE}/get-sorts?sessionId={session_id}&device=computer&country=all"
        if token:
            url += f"&sortsPageToken={token}"
        d = await api.get(EXPLORE, url)
        if not d:
            log.warning("get-sorts 실패")
            break
        for s in d.get("sorts", []):
            sid = s.get("sortId")
            if sid and sid not in sort_ids:
                sort_ids.append(sid)
        token = d.get("nextSortsPageToken")
        if not token:
            break
    return sort_ids


async def fetch_chart(api, session_id, sort_id):
    """한 차트의 게임 목록 (순위 순서 보존)."""
    url = (f"{BASE}/get-sort-content?sessionId={session_id}"
           f"&sortId={sort_id}&device=computer&country=all")
    d = await api.get(EXPLORE, url)
    if not d:
        return []
    return d.get("games", [])


def save_chart(cur, sort_id, games):
    """chart_snapshot 덮어쓰기 + 신규 게임 collect_queue 등록. (신규 게임 수 반환)"""
    new_games = 0
    for rank, g in enumerate(games, 1):
        uid = g.get("universeId")
        if not uid:
            continue
        cur.execute(
            "INSERT INTO chart_snapshot (sort_id, universe_id, chart_rank) "
            "VALUES (%s, %s, %s) "
            "ON DUPLICATE KEY UPDATE chart_rank=VALUES(chart_rank), snapshot_at=CURRENT_TIMESTAMP",
            (sort_id, uid, rank),
        )
        # games·collect_queue 둘 다 없으면 신규 → 큐 등록
        cur.execute("SELECT 1 FROM games WHERE universe_id=%s", (uid,))
        if cur.fetchone():
            continue
        cur.execute(
            "INSERT IGNORE INTO collect_queue (universe_id, reason) VALUES (%s, 'chart')",
            (uid,),
        )
        if cur.rowcount:
            new_games += 1
    return new_games


async def run():
    session_id = f"b1_{int(time.time())}"
    rl = RateLimiter()
    log.info("=== B1 차트 수집 시작 ===")
    async with RobloxApi(rl) as api:
        sort_ids = await fetch_sort_ids(api, session_id)
        log.info("차트 종류 %d개", len(sort_ids))

        total_new = 0
        seen = set()
        for sid in sort_ids:
            games = await fetch_chart(api, session_id, sid)
            with cursor() as cur:
                new_n = save_chart(cur, sid, games)
            total_new += new_n
            seen.update(g.get("universeId") for g in games if g.get("universeId"))
            log.info("  [%s] %d개 (신규 %d)", sid, len(games), new_n)

    log.info("=== 완료: 차트 %d, 고유게임 %d, 신규 큐등록 %d ===",
             len(sort_ids), len(seen), total_new)


if __name__ == "__main__":
    asyncio.run(run())
