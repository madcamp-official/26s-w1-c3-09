# batch — 배치 워커 (Python)

로블록스 데이터를 주기적으로 수집·집계하는 백그라운드 워커.
서버(`../server`)와는 통신하지 않고 MySQL(`roblox_rec`)로만 연결된다.
설계 근거: `archive/docs/시스템-설계서.md` §5 (배치 파이프라인).

## 구조

```
batch/
├── requirements.txt      의존성 (pymysql, requests)
├── config.py             DB 접속·동시성 상한 (환경변수에서 읽음)
├── common/
│   ├── db.py             MySQL 연결
│   ├── roblox_api.py     로블록스 호출 + 재시도 + 동시성 세마포어
│   └── logger.py         파일 로그
└── jobs/
    ├── b1_charts.py           인기 차트 수집 (Rolimons→explore 폴백)
    ├── b2_queue_consumer.py   collect_queue 소비 → games 채움
    ├── b3_rec_cache.py        인기 게임 recommendations 캐시
    ├── b4_fan_collector.py    그룹 팬 수집(커서) → cofavorite 집계
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
