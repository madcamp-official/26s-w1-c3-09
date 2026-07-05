"""
로블록스 API 호출 공통 모듈. (담당: KJH)

rate_limiter(엔드포인트별 AIMD)를 감싸 "허가 → 호출 → 상태처리"를 한 번에.
  - 429  : rate_limiter.on_429(버킷) → rate 절반 + 백오프 후 재시도 (A-2)
  - 5xx  : 간헐 오류 → 2초 간격 재시도
  - 4xx  : 즉시 실패(재시도 안 함)
멀티겟 배치: detail 50 / votes·icon·thumb·users 100 (rate_governance operations의 batchSize).

검증된 원형: scratchpad collect_jujutsu1k / full_api_test 의 get()/AIMD.
사용:
    api = RobloxApi(rate_limiter)
    d = await api.get("games_detail",
                      "https://games.roblox.com/v1/games?universeIds=1,2,3")
"""
import asyncio

import aiohttp

UA = {
    "User-Agent": ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                   "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"),
}


class RobloxApi:
    """aiohttp 세션 + rate_limiter. async with 로 세션 수명 관리."""

    def __init__(self, rate_limiter, max_retries=5):
        self._rl = rate_limiter
        self._max_retries = max_retries
        self._session = None

    async def __aenter__(self):
        self._session = aiohttp.ClientSession(
            connector=aiohttp.TCPConnector(limit=100, ssl=False),
            headers=UA,
            timeout=aiohttp.ClientTimeout(total=25),
        )
        return self

    async def __aexit__(self, *exc):
        if self._session:
            await self._session.close()

    async def get(self, bucket, url):
        """GET. 성공 시 파싱된 JSON(dict) 반환, 실패 시 None. 버킷 rate 준수."""
        return await self._request(bucket, "GET", url, None)

    async def post(self, bucket, url, body):
        """POST(JSON body). 닉네임→ID 등 배치 조회용."""
        return await self._request(bucket, "POST", url, body)

    async def _request(self, bucket, method, url, body):
        for attempt in range(self._max_retries):
            await self._rl.acquire(bucket)   # 이 버킷의 rate 허가 대기
            try:
                async with self._session.request(method, url, json=body) as r:
                    status = r.status
                    if status == 200:
                        return await r.json()
                    if status == 429:
                        self._rl.on_429(bucket)   # rate 절반 + 백오프
                        continue
                    if status in (500, 502, 503, 504):
                        await asyncio.sleep(2.0)  # 간헐 서버오류 → 재시도
                        continue
                    return None                    # 4xx 등 → 즉시 실패
            except (aiohttp.ClientError, asyncio.TimeoutError):
                await asyncio.sleep(1.0)           # 네트워크 오류 → 재시도
                continue
        return None
