"""
로블록스 API 호출 공통 모듈. (담당: KJH)
- aiohttp + 엔드포인트별 AIMD rate 제어 (rate_limiter.py) — 동시성 세마포어 아님 (A-1 폐기)
- 429 → rate 절반 + 백오프 (A-2: 밀어붙이면 30초+ 페널티), 간헐 500 → 2초 간격 재시도
- 배치 호출 활용: detail 50개/호출, votes·icon·thumb 100개/호출,
  닉네임→ID는 POST /v1/usernames/users (users 계열 3경로는 버킷 공유 — 실측)
- 검증된 원형: 실측 수집 스크립트(collect_jujutsu1k 계열)의 get()/AIMD 이식
엔드포인트·rate 상세: ../config/rate_governance.json + docs/KJH/api-명세.md
TODO(KJH): 구현.
"""
