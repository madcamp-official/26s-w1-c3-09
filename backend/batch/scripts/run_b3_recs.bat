@echo off
REM B3 연쇄추천(People-Also-Join) 캐시 — 매일 1회 권장.
REM 인기 게임의 관련 게임을 game_recommendations에 채움 (한 실행 = recCache.perRun 게임).
REM 용도: 상세 '함께 즐기는 게임' + 정밀모드 덤확장 캐시 워밍 (없어도 서버가 온디맨드로 채움).
cd /d "%~dp0.."
python -m jobs.b3_rec_cache
