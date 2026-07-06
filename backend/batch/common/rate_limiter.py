"""
엔드포인트(버킷)별 AIMD rate 제어. (담당: KJH)

배치의 모든 로블록스 호출이 여기를 거친다. 버킷마다 독립 리미터(A-1: 엔드포인트별
독립 토큰버킷)를 두고, AIMD로 로블록스 한계 근처를 스스로 찾는다.

AIMD (Additive Increase, Multiplicative Decrease):
  - 무사히 가면 rate를 조금씩 올림(+step) → 한계 탐침
  - 429 만나면 rate 절반(×0.5) + 백오프 → 즉시 물러남 (A-2: 밀어붙이면 30초+ 페널티)
  → "빠르게 많이"보다 "느려도 꾸준히"가 총 처리량 높음 (실측)

목표 상한 = rate_governance.json의 실측 rate × (1 - margin). 그 아래에서만 탐침.
검증된 원형: scratchpad collect_jujutsu1k / full_api_test 의 AIMD.
"""
import asyncio
import time

from config import load_rate_governance


class _AIMD:
    """단일 버킷의 AIMD 리미터. acquire()로 호출 허가 획득, on_429()로 백오프."""

    def __init__(self, ceiling, start=None):
        self.ceiling = max(0.05, ceiling)                  # 배치 몫 상한 (이미 여유·floor 반영)
        # 시작 rate = 상한의 70% — 실측 기반이라 낮게 램프업할 이유 없음.
        # (기존 2.0 고정 시작은 상한 도달까지 ~40초 낭비 → 매 실행 초기 손실)
        self.rate = start if start is not None else min(self.ceiling, max(0.3, self.ceiling * 0.7))
        self.min_rate = max(0.1, self.ceiling * 0.1)
        self.next_free = time.monotonic()   # 다음 토큰 발생 시각
        self.backoff_until = 0.0
        self.last_increase = time.monotonic()
        self.errors_since = 0
        self._lock = asyncio.Lock()

    async def acquire(self):
        """호출 1건 허가를 얻을 때까지 대기. 스페이싱 = 1/rate."""
        async with self._lock:
            while True:
                now = time.monotonic()
                if now < self.backoff_until:            # 429 백오프 중
                    await asyncio.sleep(self.backoff_until - now)
                    continue
                if now >= self.next_free:
                    self.next_free = max(self.next_free + 1.0 / self.rate, now)
                    # 일정 시간 무사고면 rate 가산 증가 (한계 탐침)
                    if now - self.last_increase >= 6.0:
                        if self.errors_since == 0:
                            self.rate = min(self.ceiling, self.rate + 0.5)
                        self.errors_since = 0
                        self.last_increase = now
                    return
                await asyncio.sleep(min(1.0 / self.rate, self.next_free - now))

    def on_429(self, backoff=3.0):
        """429 수신 시: rate 절반 + 백오프. (락 밖에서 호출해도 안전한 원자 갱신)"""
        self.rate = max(self.min_rate, self.rate * 0.5)
        self.backoff_until = time.monotonic() + backoff
        self.errors_since += 1


class RateLimiter:
    """엔드포인트(버킷)별 AIMD 묶음. 배치 전 과정에서 하나만 공유해 쓴다.

    사용:
        rl = RateLimiter()
        await rl.acquire("games_detail")   # detail 호출 전
        ... 실제 호출 ...
        if status == 429: rl.on_429("games_detail")
    """

    def __init__(self, governance=None):
        gov = governance or load_rate_governance()
        default_margin = gov.get("defaults", {}).get("margin", 0.125)
        self._buckets = {}
        for name, spec in gov.get("buckets", {}).items():
            measured = spec.get("ratePerS", 1.0)
            margin = spec.get("margin", default_margin)
            usable = measured * (1.0 - margin)
            # G-6 레인 차감(단일 코드): 배치 몫 = 가용치 − 타 레인 floor 합.
            # 서버와 같은 IP(EC2)에서 돌 때 실시간 유저 몫(floor)을 구조적으로 보장.
            # 서버가 없는 환경(기숙사 등)에선 floor만큼 덜 쓰지만, 코드 분기 없이 동일 동작.
            reserved = 0.0
            for lane_name, lane in (spec.get("lanes") or {}).items():
                if lane_name != "batch" and lane.get("floor"):
                    reserved += lane["floor"]
            self._buckets[name] = _AIMD(usable - reserved)

    async def acquire(self, bucket):
        limiter = self._buckets.get(bucket)
        if limiter is None:
            raise KeyError(f"rate_governance.json에 없는 버킷: {bucket}")
        await limiter.acquire()

    def on_429(self, bucket, backoff=3.0):
        limiter = self._buckets.get(bucket)
        if limiter is not None:
            limiter.on_429(backoff)

    def current_rate(self, bucket):
        """진단용 — 현재 수렴 rate."""
        limiter = self._buckets.get(bucket)
        return limiter.rate if limiter else None
