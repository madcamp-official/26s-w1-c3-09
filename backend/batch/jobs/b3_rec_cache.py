"""
B3. 연쇄 추천(People-Also-Join) 캐시. (담당: KJH)

game_recommendations가 없거나 오래된(refreshAfterDays) 인기 게임을 매 사이클 조금씩 골라
C-1(recommendations API) 호출 → game_recommendations 저장. to-게임은 collect_queue(reason=recommendation)로 등록해 b2가 상세 채움.

용도: 상세페이지 '함께 즐기는 게임' + 정밀모드 덤확장이 이 캐시를 히트 → live 호출 감소.
(캐시 없어도 서버가 온디맨드로 채우므로 b3는 배경 워밍 역할 — 없어도 동작은 함)

값 출처: collection.json recCache / fanCollection.playingFloor.
실행: conda activate mad1 && python -m jobs.b3_rec_cache
"""
import asyncio

from common.db import cursor
from common.logger import get_logger
from common.rate_limiter import RateLimiter
from common.roblox_api import RobloxApi
from config import load_collection

log = get_logger("b3_rec_cache")

_col = load_collection()
PER_RUN = _col["recCache"]["perRun"]
REFRESH_DAYS = _col["recCache"]["refreshAfterDays"]
FLOOR = _col["fanCollection"]["playingFloor"]


def find_games_needing_recs(cur):
    """캐시 없거나 오래된 인기 게임 (동접순). refreshAfterDays 안에 받은 건 스킵."""
    cur.execute(
        "SELECT g.universe_id FROM games g "
        "WHERE g.playing >= %s "
        "  AND NOT EXISTS ("
        "     SELECT 1 FROM game_recommendations r "
        "     WHERE r.from_universe_id = g.universe_id "
        "       AND r.fetched_at > (NOW() - INTERVAL %s DAY)) "
        "ORDER BY g.playing DESC LIMIT %s",
        (FLOOR, REFRESH_DAYS, PER_RUN))
    return [row["universe_id"] for row in cur.fetchall()]


def store_recs(cur, from_id, to_ids):
    """game_recommendations 덮어쓰기(rank 1..n) + to-게임 collect_queue 등록."""
    cur.execute("DELETE FROM game_recommendations WHERE from_universe_id = %s", (from_id,))
    if not to_ids:
        return
    cur.executemany(
        "INSERT INTO game_recommendations (from_universe_id, to_universe_id, rec_rank) VALUES (%s, %s, %s)",
        [(from_id, to, rank + 1) for rank, to in enumerate(to_ids)])
    cur.executemany(
        "INSERT IGNORE INTO collect_queue (universe_id, reason) VALUES (%s, 'recommendation')",
        [(to,) for to in to_ids])


async def run():
    rl = RateLimiter()
    log.info("=== B3 rec cache start ===")
    with cursor() as cur:
        games = find_games_needing_recs(cur)
    if not games:
        log.info("no games need recs")
        return
    n = 0
    async with RobloxApi(rl) as api:
        for uid in games:
            data = await api.get(
                "games_rec",
                f"https://games.roblox.com/v1/games/recommendations/game/{uid}?maxRows=6")
            to_ids = []
            if data:
                for g in data.get("games", []):
                    tid = g.get("universeId")
                    if tid and tid != uid:
                        to_ids.append(tid)
            with cursor() as cur:
                store_recs(cur, uid, to_ids)
            n += 1
    log.info("=== done: %d games cached ===", n)


if __name__ == "__main__":
    asyncio.run(run())
