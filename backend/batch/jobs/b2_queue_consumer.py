"""
B2. collect_queue 소비 → games 채우기. (담당: KJH) — G-2/G-3
pending 게임을 detail·votes·icon으로 조회해 games UPSERT, 큐를 done 처리.

핵심(G-3, 버킷 독립): detail(50묶음)·votes(100묶음)·icon(100묶음)을 '동시에' 조회.
  버킷이 독립이라 각자 최고 속도로 병렬 → games 행이 점진 완성.
  detail이 없으면(삭제된 게임 등) 그 게임은 failed 처리.
실행: conda activate mad1 && python -m jobs.b2_queue_consumer
"""
import asyncio
from datetime import datetime, timezone

from common.db import cursor
from common.logger import get_logger
from common.rate_limiter import RateLimiter
from common.roblox_api import RobloxApi

log = get_logger("b2_queue_consumer")

BATCH = 200          # 한 번에 처리할 pending 게임 수
DETAIL_CHUNK = 50    # detail 배치상한 (실측)
MULTI_CHUNK = 100    # votes·icon 배치상한 (실측)


def _chunks(seq, n):
    for i in range(0, len(seq), n):
        yield seq[i:i + n]


def _parse_created(s):
    """로블록스 ISO8601('2024-05-26T16:47:48.617Z') → naive UTC datetime."""
    if not s:
        return None
    try:
        dt = datetime.fromisoformat(s.replace("Z", "+00:00"))
        return dt.astimezone(timezone.utc).replace(tzinfo=None)
    except Exception:
        return None


def fetch_pending(cur, limit):
    cur.execute(
        "SELECT universe_id FROM collect_queue WHERE status='pending' "
        "ORDER BY requested_at ASC LIMIT %s", (limit,))
    return [r["universe_id"] for r in cur.fetchall()]


async def fetch_details(api, ids):
    """uid -> detail dict (존재하는 게임만)."""
    out = {}
    for chunk in _chunks(ids, DETAIL_CHUNK):
        url = "https://games.roblox.com/v1/games?universeIds=" + ",".join(map(str, chunk))
        d = await api.get("games_detail", url)
        for g in (d.get("data", []) if d else []):
            out[g["id"]] = g
    return out


async def fetch_votes(api, ids):
    """uid -> (up, down)."""
    out = {}
    for chunk in _chunks(ids, MULTI_CHUNK):
        url = "https://games.roblox.com/v1/games/votes?universeIds=" + ",".join(map(str, chunk))
        d = await api.get("games_votes", url)
        for v in (d.get("data", []) if d else []):
            out[v["id"]] = (v.get("upVotes"), v.get("downVotes"))
    return out


async def fetch_icons(api, ids):
    """uid -> icon_url."""
    out = {}
    for chunk in _chunks(ids, MULTI_CHUNK):
        url = ("https://thumbnails.roblox.com/v1/games/icons?universeIds="
               + ",".join(map(str, chunk)) + "&size=256x256&format=Png")
        d = await api.get("thumb_icon", url)
        for t in (d.get("data", []) if d else []):
            if t.get("state") == "Completed" and t.get("imageUrl"):
                out[t["targetId"]] = t["imageUrl"]
    return out


def upsert_game(cur, g, votes, icon_url):
    """detail(g) + votes + icon → games UPSERT. place_id·name 없으면 스킵(False)."""
    place_id = g.get("rootPlaceId")
    name = g.get("name")
    if not place_id or not name:
        return False
    creator = g.get("creator") or {}
    ctype = creator.get("type")
    cgid = creator.get("id") if ctype == "Group" else None
    up, down = (votes or (None, None))
    cur.execute(
        "INSERT INTO games "
        "(universe_id, place_id, name, description, genre_l1, genre_l2, playing, visits, "
        " favorited_count, created, creator_type, creator_group_id, up_votes, down_votes, "
        " icon_url, updated_at) "
        "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,CURRENT_TIMESTAMP) "
        "ON DUPLICATE KEY UPDATE "
        " place_id=VALUES(place_id), name=VALUES(name), description=VALUES(description), "
        " genre_l1=VALUES(genre_l1), genre_l2=VALUES(genre_l2), playing=VALUES(playing), "
        " visits=VALUES(visits), favorited_count=VALUES(favorited_count), "
        " created=COALESCE(VALUES(created), created), "
        " creator_type=VALUES(creator_type), creator_group_id=VALUES(creator_group_id), "
        " up_votes=COALESCE(VALUES(up_votes), up_votes), "
        " down_votes=COALESCE(VALUES(down_votes), down_votes), "
        " icon_url=COALESCE(VALUES(icon_url), icon_url), updated_at=CURRENT_TIMESTAMP",
        (g["id"], place_id, name, g.get("description"), g.get("genre_l1"), g.get("genre_l2"),
         g.get("playing"), g.get("visits"), g.get("favoritedCount"), _parse_created(g.get("created")),
         ctype, cgid, up, down, icon_url),
    )
    return True


async def run():
    rl = RateLimiter()
    log.info("=== B2 큐 소비 시작 ===")
    with cursor() as cur:
        ids = fetch_pending(cur, BATCH)
    if not ids:
        log.info("pending 없음 — 종료")
        return
    log.info("pending %d개 조회", len(ids))

    async with RobloxApi(rl) as api:
        # 버킷 독립 → 세 종류 동시 (각자 최고 속도)
        details, votes, icons = await asyncio.gather(
            fetch_details(api, ids), fetch_votes(api, ids), fetch_icons(api, ids))

    done, failed = [], []
    with cursor() as cur:
        for uid in ids:
            g = details.get(uid)
            if g and upsert_game(cur, g, votes.get(uid), icons.get(uid)):
                done.append(uid)
            else:
                failed.append(uid)   # detail 없음(삭제 등) or place/name 없음
        if done:
            cur.executemany("UPDATE collect_queue SET status='done' WHERE universe_id=%s",
                            [(u,) for u in done])
        if failed:
            cur.executemany("UPDATE collect_queue SET status='failed' WHERE universe_id=%s",
                            [(u,) for u in failed])
    log.info("=== 완료: games 저장 %d, 실패 %d ===", len(done), len(failed))


if __name__ == "__main__":
    asyncio.run(run())
