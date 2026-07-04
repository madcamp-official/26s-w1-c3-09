# batch — 배치 워커 (Python)

로블록스 데이터를 주기적으로 수집·집계하는 백그라운드 워커.
서버(`../server`)와는 통신하지 않고 MySQL(`roblox_rec`)로만 연결된다.
설계 근거: `docs/KJH/의사결정-기록.md` (A: rate limit 실측, B~F: 수집·알고리즘·경계).
rate 예산: `../config/rate_governance.json` (server와 공유 — 진실의 원천 하나).

## 구조

```
batch/
├── requirements.txt      의존성 (pymysql, requests, aiohttp)
├── config.py             DB 접속(환경변수) + 공용 config 경로
├── common/
│   ├── db.py             MySQL 연결
│   ├── roblox_api.py     로블록스 호출 (aiohttp + 배치 멀티겟 + 429 백오프)
│   ├── rate_limiter.py   엔드포인트별 AIMD rate 제어 (예정 — 검증된 프로토타입 이식)
│   └── logger.py         파일 로그
└── jobs/
    ├── b1_charts.py           차트 수집 — explore 매일(우선) + Rolimons 주1회(보강)
    ├── b2_queue_consumer.py   collect_queue 소비 → games 채움 (top-down 캐싱 실행자)
    ├── b3_rec_cache.py        인기 게임 recommendations + cofavorite 집계 캐시
    ├── b4_fan_collector.py    유저 즐겨찾기 수집 (Asc 200명, 무조건 저장) → cofavorite
    └── b5_refresh_cleanup.py  캐시 갱신 + 만료 데이터 삭제
```

## 실행 (개발)

```
conda activate mad1
pip install -r requirements.txt
python -m jobs.b1_charts
```

환경변수 `DB_PASSWORD` 필요 (`.env.example` 참고).

## 운영

EC2에서 cron으로 주기 실행 (배포 단계에서 설정).
