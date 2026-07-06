"""
B2. collect_queue 소비 → games 채우기 + media 백필 + 오래된 games 수치 갱신. (담당: KJH)

세 단계 (모든 수치는 config에서 — 하드코딩 금지 원칙):
  ① 큐 소비: pending 게임을 detail·votes·icon 동시 조회(버킷 독립) → games UPSERT
  ② media 백필: media 없는 게임(인기순)에 스크린샷·영상 원천 수집
     Image/GamePreviewVideo→game_media, YouTubeVideo(videoHash)→game_videos,
     미디어 0개는 asset_type='Empty' 마커(재조회 방지 — 표시 코드는 'Empty' 제외)
  ③ refresh: updated_at이 오래된 games(인기순)를 detail+votes 재조회 → 동접·방문·즐겨찾기수 갱신
     (b1의 신작 '유입'과 별개로, 기존 게임 '수치'가 낡지 않게)

값 출처: collection.json queue/refresh, rate_governance.json operations의 batchSize.
실행: conda activate mad1 && python -m jobs.b2_queue_consumer
"""
import asyncio
from datetime import datetime, timezone

from common.db import cursor
from common.logger import get_logger
from common.rate_limiter import RateLimiter
from common.roblox_api import RobloxApi
from config import load_collection, load_rate_governance

log = get_logger("b2_queue_consumer")

_col = load_collection()
_ops = load_rate_governance()["operations"]
BATCH = _col["queue"]["batchPerRun"]
MEDIA_BACKFILL = _col["queue"]["mediaBackfillPerRun"]
REFRESH_AFTER_HOURS = _col["refresh"]["gamesAfterHours"]
REFRESH_PER_RUN = _col["refresh"]["gamesPerRun"]
DETAIL_CHUNK = _ops["getGameDetails"]["batchSize"]   # 실측 상한 — config가 진실
VOTES_CHUNK = _ops["getVotes"]["batchSize"]
ICON_CHUNK = _ops["getGameIcons"]["batchSize"]


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
    for chunk in _chunks(ids, VOTES_CHUNK):
        url = "https://games.roblox.com/v1/games/votes?universeIds=" + ",".join(map(str, chunk))
        d = await api.get("games_votes", url)
        for v in (d.get("data", []) if d else []):
            out[v["id"]] = (v.get("upVotes"), v.get("downVotes"))
    return out


async def fetch_icons(api, ids):
    """uid -> icon_url."""
    out = {}
    for chunk in _chunks(ids, ICON_CHUNK):
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


async def fetch_media(api, uid):
    """한 게임의 media 조회 → (game_media 행들, game_videos 행들). 실패 시 None."""
    d = await api.get("games_media", f"https://games.roblox.com/v2/games/{uid}/media")
    if d is None:
        return None
    media_rows, video_rows = [], []
    order = 1
    for m in d.get("data", []):
        if not m.get("approved", True):
            continue
        atype = m.get("assetType")
        if atype == "Image" and m.get("imageId"):
            media_rows.append((uid, order, "Image", m["imageId"], None)); order += 1
        elif atype == "GamePreviewVideo" and m.get("videoId"):
            media_rows.append((uid, order, "GamePreviewVideo", m.get("imageId"), int(m["videoId"]))); order += 1
        elif atype == "YouTubeVideo" and m.get("videoHash"):
            video_rows.append((uid, m["videoHash"][:20], m.get("videoTitle")))
    if not media_rows:
        media_rows.append((uid, 0, "Empty", None, None))   # 재조회 방지 마커
    return media_rows, video_rows


async def backfill_media(api):
    """media 미수집 게임(인기순)을 백필. games_media 버킷 — detail·fav와 독립이라 공짜 병렬."""
    with cursor() as cur:
        cur.execute(
            "SELECT g.universe_id FROM games g "
            "LEFT JOIN game_media m ON m.universe_id = g.universe_id "
            "WHERE m.universe_id IS NULL "
            "ORDER BY g.favorited_count DESC LIMIT %s", (MEDIA_BACKFILL,))
        ids = [r["universe_id"] for r in cur.fetchall()]
    if not ids:
        return 0
    results = await asyncio.gather(*(fetch_media(api, u) for u in ids))
    media_rows, video_rows = [], []
    for r in results:
        if r is None:
            continue
        media_rows += r[0]; video_rows += r[1]
    with cursor() as cur:
        if media_rows:
            cur.executemany(
                "INSERT IGNORE INTO game_media "
                "(universe_id, sort_order, asset_type, image_id, video_asset_id) "
                "VALUES (%s,%s,%s,%s,%s)", media_rows)
        if video_rows:
            cur.executemany(
                "INSERT IGNORE INTO game_videos (universe_id, youtube_video_id, title) "
                "VALUES (%s,%s,%s)", video_rows)
    log.info("media 백필: %d게임 (media %d행, 유튜브 %d행)", len(ids), len(media_rows), len(video_rows))
    return len(ids)


async def refresh_stale_games(api):
    """updated_at이 오래된 games(인기순)를 detail+votes 재조회 → 수치 갱신.
    icon은 재조회 안 함(180일 URL) — upsert의 COALESCE가 기존값 보존."""
    with cursor() as cur:
        cur.execute(
            "SELECT universe_id FROM games "
            "WHERE updated_at < (NOW() - INTERVAL %s HOUR) "
            "ORDER BY favorited_count DESC LIMIT %s",
            (REFRESH_AFTER_HOURS, REFRESH_PER_RUN))
        ids = [r["universe_id"] for r in cur.fetchall()]
    if not ids:
        return 0
    details, votes = await asyncio.gather(fetch_details(api, ids), fetch_votes(api, ids))
    updated = 0
    with cursor() as cur:
        for uid in ids:
            g = details.get(uid)
            if g and upsert_game(cur, g, votes.get(uid), None):
                updated += 1
    log.info("games 수치 갱신: %d/%d (동접·방문·즐겨찾기수)", updated, len(ids))
    return updated


async def run():
    rl = RateLimiter()
    log.info("=== B2 큐 소비 시작 ===")
    with cursor() as cur:
        ids = fetch_pending(cur, BATCH)

    async with RobloxApi(rl) as api:
        done, failed = [], []
        if ids:
            log.info("pending %d개 조회", len(ids))
            # 버킷 독립 → 세 종류 동시 (각자 최고 속도)
            details, votes, icons = await asyncio.gather(
                fetch_details(api, ids), fetch_votes(api, ids), fetch_icons(api, ids))
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
        else:
            log.info("pending 없음")

        backfilled = await backfill_media(api)      # 큐와 무관하게 media 원천 채우기
        refreshed = await refresh_stale_games(api)  # 오래된 수치 갱신 (b5의 핵심 절반)

    log.info("=== 완료: games 저장 %d, 실패 %d, media 백필 %d, 수치갱신 %d ===",
             len(done), len(failed), backfilled, refreshed)


if __name__ == "__main__":
    asyncio.run(run())
