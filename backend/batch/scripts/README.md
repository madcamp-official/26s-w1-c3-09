# 배치 자동 실행 (윈도우 작업 스케줄러)

24시간 켜진 컴퓨터(예: 기숙사 PC)에서 배치를 주기적으로 자동 실행하기 위한 스크립트.
`git pull`로 받아서 작업 스케줄러에 등록하면, 사람이 아무것도 안 해도 데이터가 쌓인다.

## 선행 조건
1. `DB_PASSWORD`가 **사용자 환경변수로 영구 설정**돼 있어야 함 (스케줄러가 읽을 수 있게):
   ```powershell
   [Environment]::SetEnvironmentVariable("DB_PASSWORD", "본인비번", "User")
   ```
2. `python`이 PATH에 있어야 함 (Anaconda 설치 시 보통 자동).
   확인: **일반 cmd 창**(PowerShell 아님)에서 `python --version`이 되면 스케줄러도 됨.
   안 되면 각 .bat의 `python`을 전체 경로로 (예: `C:\Users\<user>\anaconda3\python.exe`).

## 스크립트
| 파일 | 하는 일 | 권장 주기 |
|---|---|---|
| run_b1_charts.bat | 인기 차트 수집 → collect_queue | 매일 1회 |
| run_b2_queue.bat  | 큐 소비 → games 채움 (5회 반복=최대 1000개) | 1~2시간마다 |
| run_b3_recs.bat   | 연쇄추천(People-Also-Join) → game_recommendations | 매일 1회 |
| run_b4_fans.bat   | 팬 수집 → cofavorite (무거움) | 매일 1회 (여러 번이면 사다리 진행) |

## 등록 (PowerShell, 관리자 권장) — schtasks 한 줄씩
경로는 각자 clone 위치에 맞게 수정 (아래는 `C:\Users\loser\26s-w1-c3-09` 예시).

```powershell
$B = "C:\Users\loser\26s-w1-c3-09\backend\batch\scripts"

# b1: 매일 03:00
schtasks /create /tn "madfinder_b1_charts" /tr "$B\run_b1_charts.bat" /sc daily /st 03:00 /f

# b2: 1시간마다
schtasks /create /tn "madfinder_b2_queue" /tr "$B\run_b2_queue.bat" /sc hourly /f

# b3: 매일 03:30 (b1 뒤, b4 앞)
schtasks /create /tn "madfinder_b3_recs" /tr "$B\run_b3_recs.bat" /sc daily /st 03:30 /f

# b4: 매일 04:00 (b1 뒤)
schtasks /create /tn "madfinder_b4_fans" /tr "$B\run_b4_fans.bat" /sc daily /st 04:00 /f
```

## 확인·관리
```powershell
schtasks /query /tn "madfinder_b2_queue"        # 상태
schtasks /run   /tn "madfinder_b2_queue"        # 지금 즉시 1회 실행(테스트)
schtasks /delete /tn "madfinder_b2_queue" /f    # 삭제
```
실행 결과 로그는 `backend/batch/logs/*.log` 에 쌓임 (아침에 확인).

## 주의
- 로블록스 rate limit 때문에 **같은 잡을 동시에 여러 개 돌리지 말 것** (스케줄이 겹치지 않게).
- 컴퓨터가 절전/최대절전으로 들어가면 스케줄이 안 돎 → 전원 옵션에서 "절전 안 함"으로.
