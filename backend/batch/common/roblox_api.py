"""
로블록스 API 호출 공통 모듈. (담당: KJH)

rate_limiter(엔드포인트별 AIMD)를 감싸 "허가 → 호출 → 상태처리"를 한 번에.
  - 429  : rate_limiter.on_429(버킷) → rate 감소 + 백오프 후 재시도 (A-2)
  - 5xx  : 간헐 오류 → 재시도
  - 4xx  : 즉시 실패(재시도 안 함)
HTTP 재시도·타임아웃 수치는 rate_governance.json defaults.http (하드코딩 금지 원칙).
멀티겟 배치 크기는 operations의 batchSize/pageSize를 호출부가 읽어 사용.

사용:
    api = RobloxApi(rate_limiter)
    d = await api.get("games_detail",
                      "https://games.roblox.com/v1/games?universeIds=1,2,3")
"""
import asyncio

import aiohttp

from config import load_rate_governance

UA = {
    "User-Agent": ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                   "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"),
}


class RobloxApi:
    """aiohttp 세션 + rate_limiter. async with 로 세션 수명 관리."""

    def __init__(self, rate_limiter, governance=None):
        gov = governance or load_rate_governance()
        http = gov["defaults"]["http"]
        self._rl = rate_limiter
        self._max_retries = http["maxRetries"]
        self._timeout_seconds = http["timeoutSeconds"]
        self._server_error_delay = http["serverErrorRetryDelaySeconds"]
        self._network_error_delay = http["networkErrorRetryDelaySeconds"]
        self._session = None

    async def __aenter__(self):
        self._session = aiohttp.ClientSession(
            connector=aiohttp.TCPConnector(limit=100, ssl=False),
            headers=UA,
            timeout=aiohttp.ClientTimeout(total=self._timeout_seconds),
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
                        self._rl.on_429(bucket)   # rate 감소 + 백오프
                        continue
                    if status in (500, 502, 503, 504):
                        await asyncio.sleep(self._server_error_delay)   # 간헐 서버오류 → 재시도
                        continue
                    return None                    # 4xx 등 → 즉시 실패
            except (aiohttp.ClientError, asyncio.TimeoutError):
                await asyncio.sleep(self._network_error_delay)          # 네트워크 오류 → 재시도
                continue
        return None
