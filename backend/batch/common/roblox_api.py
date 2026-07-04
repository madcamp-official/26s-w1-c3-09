"""
로블록스 API 호출 공통 모듈. (담당: KJH)
- User-Agent 헤더, 재시도(C-1 간헐 500 → 2초 간격 2~3회), 동시성 세마포어(config.ROBLOX_CONCURRENCY)
- 기존 실측 스크립트(수집기)의 호출부를 이식하면 됨
엔드포인트 상세: docs/KJH/api-명세.md
TODO(KJH): 구현.
"""
