@echo off
REM B2 큐 소비(games 채우기) — 자주 실행(예: 매시간). 한 번에 200개 처리.
REM 큐를 비우려 5회 반복(=최대 1000개). 남으면 다음 스케줄에 이어서.
cd /d "%~dp0.."
for /L %%i in (1,1,5) do python -m jobs.b2_queue_consumer
