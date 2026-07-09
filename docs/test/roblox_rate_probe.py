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
    unverified = set()   # 첫 단계부터 실패 → "직전 통과"가 실측이 아닌 추정(start−step)인 버킷
    for b in PROBES:
        if b in frozen:
            limits[b] = frozen[b]
            if frozen[b] >= starts[b] - 1e-9:
                note = ""
            else:
                unverified.add(b)
                note = " [미검증 — 한 단계도 통과 못 함, 이 값은 추정]" if frozen[b] > 0 \
                    else " (첫 단계부터 실패 — 아래 회복 측정 참고)"
            print(f"  {b:16} 한계 ~{max(frozen[b], 0):.1f}/s  (config {rates.get(b)}/s){note}")
        else:
            limits[b] = args.max
            print(f"  {b:16} 한계 >= {args.max}/s (상한까지 무429)  (config {rates.get(b)}/s)")
    # 첫 단계부터 실패한 버킷 → 진짜 윈도우/페널티 시간 측정
    for b, lim in limits.items():
        if lim <= 0:
            print()
            recovery_probe(b)
    # 확인 패스 전 휴지 — 램프 말미의 429 페널티(실측 ~37초) 소거
    print()
    print(f"  (확인 패스 전 {args.cooldown}초 휴지 — 램프 429 페널티 소거)")
    time.sleep(args.cooldown)
    # 확인 패스 — 검증된 한계의 90%로 전부 동시 60초 (윈도우형 제한 방어). 미검증 버킷 제외.
    confirm = {b: round(lim * 0.9, 2) for b, lim in limits.items() if lim > 0 and b not in unverified}
    if confirm:
        print()
        print(f"  --- 확인 패스: 한계의 90%로 동시 60초 → 429가 0이어야 확정 ---")
        print(f"  rates: {confirm}")
        res = fire_window(confirm, {b: 60 for b in confirm})
        bad = {b: c.get(429, 0) for b, c in res.items() if c.get(429, 0)}
        print("  => " + (f"[!] 429 발생: {bad} — 윈도우형 제한/전역 캡 의심, 해당 버킷 더 낮게"
                         if bad else "전부 통과 — 위 한계값으로 config 설정 가능(margin 별도)"))


# games.roblox.com 도메인의 버킷들 (EC2 램프에서 4개가 같은 스텝(3.4)에서 동시 동결 → 도메인 합산 캡 의심)
GAMES_DOMAIN = ["games_fav", "games_rec", "games_votes", "games_media"]


def domain_cap_mode(args):
    """도메인 합산 캡 측정: games 도메인 4개를 같은 rate로 동시에, 합산을 올려가며 429 시작점을 찾는다.
    단계 사이 cooldown(기본 45초) 휴지 — 앞 단계 429의 페널티(실측 ~37초)가 다음 측정을 오염시키지 않게."""
    print("=" * 78)
    print(f"DOMAIN-CAP — games.roblox.com 합산 캡 측정 (4개 균등, 단계당 {args.hold}s, 단계 사이 {args.cooldown}s 휴지)")
    print(f"  대상: {GAMES_DOMAIN}")
    print("=" * 78)
    last_ok_sum = None
    for total in [4, 6, 8, 10, 12, 14, 16]:
        per = round(total / len(GAMES_DOMAIN), 2)
        schedule = {b: per for b in GAMES_DOMAIN}
        res = fire_window(schedule, {b: args.hold for b in GAMES_DOMAIN})
        n429 = {b: c.get(429, 0) for b, c in res.items() if c.get(429, 0)}
        ok = sum(c.get(200, 0) for c in res.values())
        if n429:
            print(f"  합 {total:>2}/s (각 {per}) → 429 발생 {n429}  (200 {ok}개)")
            print()
            print(f"  => 도메인 합산 한계: ~{last_ok_sum if last_ok_sum else f'<{total}'}/s"
                  f"  (마지막 통과 합 {last_ok_sum}, 실패 합 {total})")
            print(f"     config의 games 계열 개별 합(fav 6.1+rec 6.2+votes 6.9+media 7.2=26.4)은 EC2에서 위험")
            return
        print(f"  합 {total:>2}/s (각 {per}) → 전부 200 ({ok}개)")
        last_ok_sum = total
        time.sleep(args.cooldown)
    print(f"  => 합 16/s까지 무429 — 도메인 캡이 그보다 높거나 없음")


def concurrency_mode(args):
    """rate 제한 vs 동시성 제한 판별: 같은 버킷에 N개를 '동시에'(버스트) 발사해 429 나는 동시성 수준을 찾는다.
    rate 기반 제한이면 낮은 N에서도 순간 몰리면 429, 시간 분산하면 통과. 수준 사이 cooldown 휴지."""
    bucket = args.bucket
    url = PROBES[bucket]
    print("=" * 78)
    print(f"CONCURRENCY — [{bucket}] 동시 버스트 N개 → 429 나는 동시성 수준 (수준 사이 {args.cooldown}s 휴지)")
    print("=" * 78)
    for n in [1, 2, 5, 10, 20, 40]:
        out = []
        threads = [threading.Thread(target=hit, args=(url, out)) for _ in range(n)]
        for t in threads:
            t.start()
        for t in threads:
            t.join()
        c = collections.Counter(out)
        n429 = c.get(429, 0)
        print(f"  동시 {n:>2}발 → {dict(c)}" + ("  <<< 429" if n429 else ""))
        if n429:
            print(f"  => 동시 {n}발부터 429 — 순간 버스트에 민감. 같은 양을 시간 분산하면 통과하는지 대조:")
            time.sleep(args.cooldown)
            out2 = []
            for _ in range(n):                     # 같은 n발을 1초 간격 순차로
                hit(url, out2)
                time.sleep(1.0)
            c2 = collections.Counter(out2)
            print(f"     같은 {n}발을 1초 간격 순차 → {dict(c2)}"
                  + ("  → rate 제한(분산하면 통과)" if not c2.get(429) else "  → 총량/윈도우 제한(분산해도 429)"))
            return
        time.sleep(args.cooldown)
    print("  => 동시 40발까지 무429 — 이 버킷은 동시성에 관대")


def slow_probe_mode(args):
    """극저속 한계 측정 (apis_search용): 단발을 긴 간격으로 보내며, 전부 성공하는 최소 간격을 찾는다.
    간격을 60s→45s→40s→30s→20s→15s→10s로 줄여가고, 간격당 shots발 전부 200이어야 통과.
    실패하면 직전 통과 간격이 한계 → json ratePerS = 1/간격. 실패 후엔 cooldown 휴지(페널티 소거)."""
    bucket = args.bucket
    url = PROBES[bucket]
    intervals = [60, 45, 40, 30, 20, 15, 10]
    shots = args.shots
    print("=" * 78)
    print(f"SLOW-PROBE — [{bucket}] 단발 간격을 줄여가며 한계 탐색 (간격당 {shots}발 전부 200이어야 통과)")
    est = sum(iv * shots for iv in intervals) / 60
    print(f"  간격 후보: {intervals}s  (최악 총 ~{est:.0f}분)")
    print("=" * 78)
    last_ok = None
    first_request = True
    for iv in intervals:
        ok = True
        print(f"  간격 {iv:>2}s × {shots}발: ", end="", flush=True)
        for i in range(shots):
            if not first_request:
                time.sleep(iv)     # 발사 '전' 간격 확보 — 레벨 경계에서 간격 0 되는 것 방지
            first_request = False
            out = []
            hit(url, out)
            code = out[0] if out else "?"
            print(code, end=" ", flush=True)
            if code != 200:
                ok = False
                break
        if ok:
            print(" → 통과")
            last_ok = iv
        else:
            print(f" → 실패. 한계 = 간격 {last_ok}s" if last_ok else " → 실패 (60s 간격도 안 됨)")
            break
    if last_ok:
        rate = round(1.0 / last_ok, 3)
        print()
        print(f"  => 안전 간격 {last_ok}s = {rate}/s")
        print(f"     json 권장: ratePerS {rate} (margin 12.5% 적용 후 usable {round(rate * 0.875, 3)}/s)")
    else:
        print()
        print(f"  => 60초 간격도 실패 — 이 IP에서 이 엔드포인트는 사실상 사용 불가. 폴백(캐싱/DB검색) 필요")


def main():
    ap = argparse.ArgumentParser()
    here = os.path.dirname(os.path.abspath(__file__))
    ap.add_argument("--config", default=os.path.join(here, "..", "backend", "config", "rate_governance.json"))
    ap.add_argument("--margin", type=float, default=0.10, help="여유분(기본 0.10 = 실측 max의 90%%로 동시부하)")
    ap.add_argument("--secs", type=int, default=25, help="Phase 2 동시부하 지속 시간(초)")
    ap.add_argument("--skip-phase1", action="store_true", help="Phase 1 건너뛰고 config 값으로 Phase 2만")
    ap.add_argument("--ramp", action="store_true", help="정밀 모드: 동시 선형 램프 + 회복 측정 + 확인 패스 (~13분)")
    ap.add_argument("--start", type=float, default=0.6, help="램프 시작 rate (apis_search는 0.1 고정)")
    ap.add_argument("--step", type=float, default=0.2, help="램프 증가 폭 (apis_search는 0.1 고정)")
    ap.add_argument("--hold", type=int, default=20, help="단계당 유지 시간(초)")
    ap.add_argument("--search-hold", type=int, default=30, help="apis_search 단계당 유지 시간(초)")
    ap.add_argument("--max", type=float, default=7.5, help="램프 상한 rate")
    ap.add_argument("--domain-cap", action="store_true", help="games 도메인 4개 합산 캡 측정 (~8분)")
    ap.add_argument("--concurrency", action="store_true", help="동시 버스트 판별 (--bucket 지정, ~3분)")
    ap.add_argument("--bucket", default="games_fav", help="--concurrency/--recovery 대상 버킷")
    ap.add_argument("--recovery", action="store_true", help="지정 버킷 429 유발 후 회복 시간 측정")
    ap.add_argument("--slow", action="store_true", help="극저속 한계 측정 (--bucket, 검색용 — 간격 60s부터 줄여감)")
    ap.add_argument("--shots", type=int, default=4, help="--slow에서 간격당 발수 (기본 4)")
    ap.add_argument("--cooldown", type=int, default=45, help="단계/수준 사이 휴지(초) — 페널티(~37s) 소거용")
    args = ap.parse_args()

    print(f"프로브 대상 엔드포인트 {len(PROBES)}개 | config={args.config}")
    rates = load_rates(args.config)
    if args.domain_cap:
        domain_cap_mode(args)
        return
    if args.concurrency:
        concurrency_mode(args)
        return
    if args.recovery:
        recovery_probe(args.bucket)
        return
    if args.slow:
        slow_probe_mode(args)
        return
    if args.ramp:
        ramp_mode(rates, args)
        return
    measured = {} if args.skip_phase1 else phase1(rates)
    print()
    phase2(rates, measured, args.margin, args.secs)


if __name__ == "__main__":
    main()
