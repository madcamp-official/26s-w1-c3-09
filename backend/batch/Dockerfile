# batch 워커 이미지 — 잡을 일회성으로 실행하고 종료 (데몬 아님)
# 빌드 컨텍스트는 backend/ (batch가 아님!) — backend/config를 이미지에 담기 위함
#   docker build -f batch/Dockerfile .          (backend/ 에서)
#   compose에서는 context: ./backend, dockerfile: batch/Dockerfile
#
# 실행은 EC2 호스트 cron이 담당 (batch/README.md 참고):
#   docker compose -f docker-compose-prod.yml run --rm batch python -m jobs.b1_charts

FROM python:3.12-slim
WORKDIR /app

# 의존성 먼저 설치 (소스만 바뀌면 이 레이어 캐시 재사용)
COPY batch/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY batch/ .

# server·batch 공용 설정 (rate_governance.json, collection.json, scoring.json)
COPY config /app/config
ENV CONFIG_DIR=/app/config

# ENTRYPOINT 없음 — 실행할 잡은 run 명령에서 지정 (python -m jobs.b1_charts 등)
