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

## 운영 (EC2 + Docker)

batch는 데몬이 아니라 일회성 잡 — compose의 `profiles: ["batch"]` 때문에 `up -d`로는 뜨지 않고,
EC2 호스트 cron이 `docker compose run --rm`으로 잡 단위 실행한다 (`../../docker-compose-prod.yml` 참고).

이미지 빌드 (최초 1회 + batch 코드 변경 시):

```
docker compose -f docker-compose-prod.yml build batch
```

cron 등록 (`crontab -e`) — 한국시간(KST) 새벽 기준:

```
# EC2 기본 시간대가 UTC라서 CRON_TZ로 KST 지정 (Amazon Linux cronie 지원)
CRON_TZ=Asia/Seoul

# 매일 KST 새벽: 04:00 차트 수집 → 04:30 큐 소비 → 05:00 팬 수집
0 4 * * *  cd /home/ec2-user/26s-w1-c3-09 && docker compose -f docker-compose-prod.yml run --rm batch python -m jobs.b1_charts   >> /home/ec2-user/batch-cron.log 2>&1
30 4 * * * cd /home/ec2-user/26s-w1-c3-09 && docker compose -f docker-compose-prod.yml run --rm batch python -m jobs.b2_queue_consumer >> /home/ec2-user/batch-cron.log 2>&1
0 5 * * *  cd /home/ec2-user/26s-w1-c3-09 && docker compose -f docker-compose-prod.yml run --rm batch python -m jobs.b4_fan_collector  >> /home/ec2-user/batch-cron.log 2>&1
```

등록 후 확인: `crontab -l`. `CRON_TZ`가 안 먹는 배포판이면 UTC로 환산해 등록
(KST 04:00 = UTC 전날 19:00 → `0 19 * * *`).

수동 실행·테스트는 같은 명령을 그대로 셸에서:

```
docker compose -f docker-compose-prod.yml run --rm batch python -m jobs.b1_charts
```
