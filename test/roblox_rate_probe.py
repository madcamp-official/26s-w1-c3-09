#!/usr/bin/env python3
"""
로블록스 rate limit 실측 프로브 — 로컬 vs EC2 비교용.

목적:
  Phase 1) 엔드포인트(버킷)별 실제 지속 rate 측정 = 몇 /s부터 429가 시작되나
           → rate_governance.json의 ratePerS가 맞는지(과대/과소) 검증
  Phase 2) 전역 IP 캡 판별 = 모든 엔드포인트를 각자 실측 max의 90%(여유 10%)로
           동시에 계속 호출 → 개별론 안전한데 합치면 429가 나나?
           (429 나면 엔드포인트 독립 가정이 깨지고 IP 총량 캡이 있다는 뜻)

핵심: 로컬(가정용 IP)과 EC2(AWS IP)에서 똑같이 돌려 결과를 비교.
      의존성 없음(urllib). 실행:  python test/roblox_rate_probe.py
                                  python test/roblox_rate_probe.py --secs 30 --margin 0.10

주의: 로블록스를 실제로 세게 때립니다. 측정 목적의 짧은 실행만.
"""
import argparse
import collections
import json
import os
import threading
import time
import urllib.error
import urllib.request
import uuid

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"

# 각 버킷의 GET 프로브 URL. {uuid}는 호출마다 새 UUID로 치환(검색·explore의 sessionId용).
# 샘플 ID는 실재하는 값(builderman=156, RIVALS그룹=3461453, universe 7709344486/6035872082, AdoptMe place=920587237).
PROBES = {
    "groups_members":  "https://groups.roblox.com/v1/groups/3461453/users?limit=100&sortOrder=Asc",
    "groups_info":     "https://groups.roblox.com/v1/groups/3461453",
    "games_fav":       "https://games.roblox.com/v2/users/156/favorite/games?limit=50",
    "games_rec":       "https://games.roblox.com/v1/games/recommendations/game/7709344486?maxRows=6",
    "games_detail":    "https://games.roblox.com/v1/games?universeIds=7709344486,6035872082",
    "games_votes":     "https://games.roblox.com/v1/games/votes?universeIds=7709344486,6035872082",
    "games_media":     "https://games.roblox.com/v2/games/7709344486/media",
    "users_lookup":    "https://users.roblox.com/v1/users/156",
    "apis_search":     "https://apis.roblox.com/search-api/omni-search?searchQuery=fish&pageType=all&sessionId={uuid}",
    "apis_explore":    "https://apis.roblox.com/explore-api/v1/get-sort-content?sessionId={uuid}&sortId=top-trending",
    "apis_place":      "https://apis.roblox.com/universes/v1/places/920587237/universe",
    "thumb_icon":      "https://thumbnails.roblox.com/v1/games/icons?universeIds=7709344486&size=256x256&format=Png",
    "thumb_thumbnail": "https://thumbnails.roblox.com/v1/games/multiget/thumbnails?universeIds=7709344486&size=768x432&format=Png",
}

# config를 못 읽을 때의 폴백 (rate_governance.json 값)
DEFAULT_RATES = {
    "groups_members": 7.3, "groups_info": 0.3, "games_fav": 6.1, "games_rec": 6.2,
    "games_detail": 1.1, "games_votes": 6.9, "games_media": 7.2, "users_lookup": 1.4,
    "apis_search": 2.8, "apis_explore": 1.4, "apis_place": 1.6, "thumb_icon": 7.3, "thumb_thumbnail": 1.4,
}

# Phase 1에서 훑어볼 목표 rate들 (429 시작점 찾기)
RAMP = [2, 5, 10, 15, 20]


def hit(url, out):
    """URL 1회 GET → 상태코드(또는 'ERR')를 out 리스트에 append. 스레드에서 호출."""
    u = url.replace("{uuid}", str(uuid.uuid4()))
    req = urllib.request.Request(u, headers={"User-Agent": UA})
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            out.append(r.status)
    except urllib.error.HTTPError as e:
        out.append(e.code)
    except Exception:
        out.append("ERR")


def fire_at_rate(url, rate, secs):
    """target rate(호출/s)로 secs초간 발사. 각 호출은 별도 스레드(레이턴시가 발사속도를 안 늦추게)."""
    out, threads = [], []
    n = max(1, int(rate * secs))
    interval = 1.0 / rate
    for _ in range(n):
        t = threading.Thread(target=hit, args=(url, out))
        t.start()
        threads.append(t)
        time.sleep(interval)
    for t in threads:
        t.join()
    return collections.Counter(out)


def load_rates(path):
    try:
        with open(path, encoding="utf-8") as f:
            gov = json.load(f)
        return {b: spec.get("ratePerS") for b, spec in gov.get("buckets", {}).items()}
    except Exception as e:
        print(f"  (config 못 읽음: {e} → 기본값 사용)")
        return dict(DEFAULT_RATES)


def phase1(rates):
    print("=" * 78)
    print("PHASE 1 — 엔드포인트별 지속 rate (429 시작점 = 실제 한계)")
    print("=" * 78)
    measured = {}
    for bucket, url in PROBES.items():
        cfg = rates.get(bucket)
        stats, limit = [], None
        for r in RAMP:
            c = fire_at_rate(url, r, 3)
            ok, n429, err = c.get(200, 0), c.get(429, 0), c.get("ERR", 0)
            other = sum(v for k, v in c.items() if k not in (200, 429, "ERR"))
            tag = f"{r}/s:{ok}ok"
            if n429:
                tag += f"/{n429}x429"
            if other:
                tag += f"/{other}?"
            if err:
                tag += f"/{err}ERR"
            stats.append(tag)
            if n429 and limit is None:
                limit = round(ok / 3.0, 1)   # 429 시작 시점의 초당 성공 수 = 대략 실제 한계
            time.sleep(1)
        measured[bucket] = limit
        verdict = ""
        if limit is not None and cfg:
            verdict = "  [config가 보수적]" if cfg < limit * 0.8 else ("  [config가 한계 근접/초과!]" if cfg > limit else "")
        print(f"  {bucket:16} config={str(cfg):>5}/s  실측~{str(limit):>5}/s{verdict}")
        print(f"      {'  '.join(stats)}")
    return measured


def phase2(rates, measured, margin, secs):
    print("=" * 78)
    print(f"PHASE 2 — 전역 IP 캡 판별: 모든 엔드포인트 동시에 (실측 max × {1 - margin:.0%}) {secs}초 지속")
    print("=" * 78)
    results = {b: [] for b in PROBES}
    stop = time.time() + secs
    used = {}

    def worker(bucket, url, rate):
        interval = 1.0 / rate
        while time.time() < stop:
            hit(url, results[bucket])
            time.sleep(interval)

    threads = []
    for bucket, url in PROBES.items():
        base = measured.get(bucket) or rates.get(bucket) or DEFAULT_RATES.get(bucket, 1.0)
        rate = max(0.2, base * (1 - margin))
        used[bucket] = round(rate, 2)
        t = threading.Thread(target=worker, args=(bucket, url, rate))
        t.start()
        threads.append(t)
    print("  각 엔드포인트 동시 발사 rate:", used)
    print(f"  {secs}초 동안 동시 부하 중...")
    for t in threads:
        t.join()

    print("  --- 결과 (429가 나면 개별론 안전한데 합쳐서 걸린 것 = 전역/IP 캡 의심) ---")
    total429 = 0
    for bucket in PROBES:
        c = collections.Counter(results[bucket])
        n429 = c.get(429, 0)
        total429 += n429
        flag = "  <<< 429!" if n429 else ""
        print(f"  {bucket:16} {dict(c)}{flag}")
    print("  => " + ("[!] 429 발생 — 전역/IP 총량 캡 가능성 (엔드포인트 독립 가정 깨짐)"
                     if total429 else "429 없음 — 전역 캡 안 보임 (엔드포인트 독립)"))


def fire_window(schedule, holds):
    """schedule={bucket: rate} 전부를 동시에 발사 → {bucket: Counter}. holds={bucket: 유지시간(초)}."""
    results = {b: [] for b in schedule}
    threads = []
    t0 = time.time()

    def worker(bucket, url, rate, hold):
        stop = t0 + hold
        interval = 1.0 / rate
        nxt = time.time()
        while time.time() < stop:
            hit(url, results[bucket])
            nxt += interval
            wait = nxt - time.time()
            if wait > 0:
                time.sleep(wait)

    for b, r in schedule.items():
        t = threading.Thread(target=worker, args=(b, PROBES[b], r, holds[b]))
        t.start()
        threads.append(t)
    for t in threads:
        t.join()
    return {b: collections.Counter(results[b]) for b in schedule}


def recovery_probe(bucket, max_wait=120):
    """429를 유발한 뒤 5초 간격 단발 프로브 — 몇 초 만에 회복(200)되는지 = 진짜 윈도우/페널티 시간."""
    url = PROBES[bucket]
    print(f"  [{bucket}] 회복 측정: 연발로 429 유발 후 5초 간격 단발...")
    out = []
    for _ in range(12):                       # 빠른 연발로 429 유발
        hit(url, out)
        if out and out[-1] == 429:
            break
    if 429 not in out:
        print(f"    429를 못 냈음(제한이 매우 관대) — 회복 측정 불필요")
        return
    t0 = time.time()
    while time.time() - t0 < max_wait:
        time.sleep(5)
        one = []
        hit(url, one)
        el = int(time.time() - t0)
        if one and one[0] == 200:
            print(f"    429 후 {el}초 만에 200 회복  → 이 값이 로블록스의 실제 회복 시간")
            return
        print(f"    +{el}s: {one[0] if one else '?'}")
    print(f"    {max_wait}초 내 회복 안 됨 — 긴 페널티 존재 가능")


def ramp_mode(rates, args):
    """선형 동시 램프: 전 엔드포인트를 동시에 조금씩 올리며 각자의 429 시작점(=한계)을 찾는다.
    apis_search만 낮은 시작·작은 스텝(한계가 매우 낮아서). 429 시 동결(직전 통과=한계, 이후 호출 중단).
    끝나면 ① 첫 단계부터 실패한 버킷은 회복 측정 ② 찾은 한계의 90%로 60초 확인 패스."""
    starts = {b: (0.1 if b == "apis_search" else args.start) for b in PROBES}
    steps = {b: (0.1 if b == "apis_search" else args.step) for b in PROBES}
    holds = {b: (args.search_hold if b == "apis_search" else args.hold) for b in PROBES}
    current = dict(starts)
    frozen = {}          # bucket -> 직전 통과 rate (0.0 = 첫 단계부터 실패)
    print("=" * 78)
    print(f"RAMP — 동시 선형 램프 (시작 {args.start}/s, +{args.step}/s, 단계당 {args.hold}s, 상한 {args.max}/s)")
    print(f"       apis_search만 0.1 시작·+0.1·단계당 {args.search_hold}s (저한계 정밀). 429 → 동결(직전 통과=한계)")
    print("=" * 78)
    step_no = 0
    while True:
        active = {b: r for b, r in current.items() if b not in frozen and r <= args.max + 1e-9}
        if not active:
            break
        step_no += 1
        res = fire_window(active, {b: holds[b] for b in active})
        line = []
        for b, c in res.items():
            if c.get(429, 0) > 0:
                frozen[b] = round(current[b] - steps[b], 2)
                line.append(f"{b}@{current[b]:.1f}[429->동결,한계~{max(frozen[b],0):.1f}]")
            else:
                line.append(f"{b}@{current[b]:.1f}ok")
                current[b] = round(current[b] + steps[b], 2)
        print(f"  step{step_no:>2}: " + "  ".join(line))
    print()
    print("  --- 측정 결과 ---")
    limits = {}
    for b in PROBES:
        if b in frozen:
            limits[b] = frozen[b]
            note = " (첫 단계부터 실패 — 아래 회복 측정 참고)" if frozen[b] <= 0 else ""
            print(f"  {b:16} 한계 ~{max(frozen[b], 0):.1f}/s  (config {rates.get(b)}/s){note}")
        else:
            limits[b] = args.max
            print(f"  {b:16} 한계 >= {args.max}/s (상한까지 무429)  (config {rates.get(b)}/s)")
    # 첫 단계부터 실패한 버킷 → 진짜 윈도우/페널티 시간 측정
    for b, lim in limits.items():
        if lim <= 0:
            print()
            recovery_probe(b)
    # 확인 패스 — 한계의 90%로 전부 동시 60초 (윈도우형 제한 방어)
    confirm = {b: round(lim * 0.9, 2) for b, lim in limits.items() if lim > 0}
    if confirm:
        print()
        print(f"  --- 확인 패스: 한계의 90%로 동시 60초 → 429가 0이어야 확정 ---")
        print(f"  rates: {confirm}")
        res = fire_window(confirm, {b: 60 for b in confirm})
        bad = {b: c.get(429, 0) for b, c in res.items() if c.get(429, 0)}
        print("  => " + (f"[!] 429 발생: {bad} — 윈도우형 제한/전역 캡 의심, 해당 버킷 더 낮게"
                         if bad else "전부 통과 — 위 한계값으로 config 설정 가능(margin 별도)"))


def main():
    ap = argparse.ArgumentParser()
    here = os.path.dirname(os.path.abspath(__file__))
    ap.add_argument("--config", default=os.path.join(here, "..", "backend", "config", "rate_governance.json"))
    ap.add_argument("--margin", type=float, default=0.10, help="여유분(기본 0.10 = 실측 max의 90%로 동시부하)")
    ap.add_argument("--secs", type=int, default=25, help="Phase 2 동시부하 지속 시간(초)")
    ap.add_argument("--skip-phase1", action="store_true", help="Phase 1 건너뛰고 config 값으로 Phase 2만")
    ap.add_argument("--ramp", action="store_true", help="정밀 모드: 동시 선형 램프 + 회복 측정 + 확인 패스 (~13분)")
    ap.add_argument("--start", type=float, default=0.6, help="램프 시작 rate (apis_search는 0.1 고정)")
    ap.add_argument("--step", type=float, default=0.2, help="램프 증가 폭 (apis_search는 0.1 고정)")
    ap.add_argument("--hold", type=int, default=20, help="단계당 유지 시간(초)")
    ap.add_argument("--search-hold", type=int, default=30, help="apis_search 단계당 유지 시간(초)")
    ap.add_argument("--max", type=float, default=7.5, help="램프 상한 rate")
    args = ap.parse_args()

    print(f"프로브 대상 엔드포인트 {len(PROBES)}개 | config={args.config}")
    rates = load_rates(args.config)
    if args.ramp:
        ramp_mode(rates, args)
        return
    measured = {} if args.skip_phase1 else phase1(rates)
    print()
    phase2(rates, measured, args.margin, args.secs)


if __name__ == "__main__":
    main()
