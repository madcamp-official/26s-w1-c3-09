"""
엔드포인트(버킷)별 AIMD rate 제어. (담당: KJH)

배치의 모든 로블록스 호출이 여기를 거친다. 버킷마다 독립 리미터(A-1: 엔드포인트별
독립 토큰버킷)를 두고, AIMD로 로블록스 한계 근처를 스스로 찾는다.

AIMD (Additive Increase, Multiplicative Decrease):
  - 무사히 가면 rate를 조금씩 올림(+step) → 한계 탐침
  - 429 만나면 rate 절반(×factor) + 백오프 → 즉시 물러남 (A-2: 밀어붙이면 30초+ 페널티)
  → "빠르게 많이"보다 "느려도 꾸준히"가 총 처리량 높음 (실측)

모든 수치는 rate_governance.json에서 (하드코딩 금지 원칙):
  버킷 상한 = ratePerS × (1−margin) − 타 레인 floor 합 (G-6 레인 차감, 단일 코드)
  AIMD 파라미터 = defaults.aimd (startFraction·increaseStep·window·backoff·decreaseFactor)
"""
import asyncio
import time

from config import load_rate_governance


class _AIMD:
    """단일 버킷의 AIMD 리미터. acquire()로 호출 허가 획득, on_429()로 백오프."""

    def __init__(self, ceiling, aimd):
        self.ceiling = max(0.05, ceiling)                  # 배치 몫 상한 (이미 여유·floor 반영)
        self.increase_step = aimd["increaseStep"]
        self.increase_window = aimd["increaseWindowSeconds"]
        self.backoff_seconds = aimd["backoffSeconds"]
        self.decrease_factor = aimd["decreaseFactor"]
        # 시작 rate = 상한 × startFraction — 실측 기반이라 낮게 램프업할 이유 없음
        self.rate = min(self.ceiling, max(0.3, self.ceiling * aimd["startFraction"]))
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
                    if now - self.last_increase >= self.increase_window:
                        if self.errors_since == 0:
                            self.rate = min(self.ceiling, self.rate + self.increase_step)
                        self.errors_since = 0
                        self.last_increase = now
                    return
                await asyncio.sleep(min(1.0 / self.rate, self.next_free - now))

    def on_429(self):
        """429 수신 시: rate 감소 + 백오프. (락 밖에서 호출해도 안전한 원자 갱신)"""
        self.rate = max(self.min_rate, self.rate * self.decrease_factor)
        self.backoff_until = time.monotonic() + self.backoff_seconds
        self.errors_since += 1

    def set_ceiling(self, ceiling):
        """상한 동적 변경(정밀 조율용). 낮추면 현재 rate도 즉시 클램프. (락 밖 원자 갱신)"""
        self.ceiling = max(0.05, ceiling)
        if self.rate > self.ceiling:
            self.rate = self.ceiling
        self.min_rate = max(0.1, self.ceiling * 0.1)


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
        defaults = gov.get("defaults", {})
        default_margin = defaults.get("margin", 0.125)
        aimd = defaults["aimd"]
        self._buckets = {}
        for name, spec in gov.get("buckets", {}).items():
            measured = spec.get("ratePerS", 1.0)
            margin = spec.get("margin", default_margin)
            usable = measured * (1.0 - margin)
            # G-6 레인 차감: 기본 배치 몫 = 가용 − 실시간 floor 합(항상 예약).
            # precise.activeShare는 "항상"이 아니라 정밀이 실제로 도는 동안만 차감(런타임 조율, B안) →
            # set_precise_active(True/False)로 상한을 오르내린다. 정밀 유휴 시 배치가 그 몫을 되찾음.
            realtime_reserved = 0.0
            precise_share = 0.0
            for lane_name, lane in (spec.get("lanes") or {}).items():
                if lane_name == "batch":
                    continue
                if lane.get("floor"):
                    realtime_reserved += lane["floor"]
                if lane_name == "precise" and lane.get("activeShare"):
                    precise_share += lane["activeShare"]
            base_ceiling = usable - realtime_reserved   # 정밀 유휴 상한 (activeShare 되찾음)
            lim = _AIMD(base_ceiling, aimd)
            lim.base_ceiling = base_ceiling
            lim.precise_share = precise_share
            self._buckets[name] = lim

    async def acquire(self, bucket):
        limiter = self._buckets.get(bucket)
        if limiter is None:
            raise KeyError(f"rate_governance.json에 없는 버킷: {bucket}")
        await limiter.acquire()

    def on_429(self, bucket):
        limiter = self._buckets.get(bucket)
        if limiter is not None:
            limiter.on_429()

    def set_precise_active(self, active):
        """정밀 활성 여부에 따라 precise.activeShare가 있는 버킷의 상한을 조율(B안).
        active=True면 그 몫을 정밀에 양보(상한↓), False면 배치가 되찾음(상한↑=base). 정밀 없는 버킷은 무영향."""
        for lim in self._buckets.values():
            share = getattr(lim, "precise_share", 0.0)
            if share > 0:
                lim.set_ceiling(lim.base_ceiling - (share if active else 0.0))

    def current_rate(self, bucket):
        """진단용 — 현재 수렴 rate."""
        limiter = self._buckets.get(bucket)
        return limiter.rate if limiter else None
