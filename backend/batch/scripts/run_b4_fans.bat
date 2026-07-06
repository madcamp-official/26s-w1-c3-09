@echo off
REM B4 팬 수집(cofavorite) — 매일 1회 권장. 가장 무거움(게임당 ~30초).
REM 한 실행 = maxGamesPerRun(기본 10)게임. 여러 번 돌려 사다리 진행.
REM 커서(group_cursors)로 이어받기라 중단돼도 안전.
cd /d "%~dp0.."
python -m jobs.b4_fan_collector
