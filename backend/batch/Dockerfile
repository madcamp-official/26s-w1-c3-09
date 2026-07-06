# batch 워커 이미지 — compose가 24시간 수집 루프로 상시 실행 (b1→b2→b4 반복)
# 빌드 컨텍스트는 backend/ (batch가 아님!) — backend/config를 이미지에 담기 위함
#   docker build -f batch/Dockerfile .          (backend/ 에서)
#   compose에서는 context: ./backend, dockerfile: batch/Dockerfile
# 루프 명령은 docker-compose-prod.yml의 command 참고 (단발 실행도 run --rm으로 가능)

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
