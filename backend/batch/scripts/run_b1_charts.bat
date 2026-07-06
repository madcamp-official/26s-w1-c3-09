@echo off
REM B1 차트 수집 — 매일 1회 권장. (작업 스케줄러 등록용)
REM DB_PASSWORD는 사용자 환경변수로 영구 설정돼 있어야 함(setx 또는 SetEnvironmentVariable).
REM 로그: backend/batch/logs/b1_charts.log 에도 남음.
cd /d "%~dp0.."
python -m jobs.b1_charts
