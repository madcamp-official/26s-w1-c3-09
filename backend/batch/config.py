"""
배치 워커 공통 설정. (담당: KJH)
환경변수에서 읽음 — server의 application.yaml과 같은 변수명 사용 (.env.example 참고)
"""
import os

DB_HOST = os.environ.get("DB_HOST", "localhost")   # 없으면 로컬 (server와 동일 원칙)
DB_PORT = 3306
DB_NAME = "roblox_rec"
DB_USER = "root"
DB_PASSWORD = os.environ.get("DB_PASSWORD")        # 필수 — 없으면 시작 시 에러 내는 게 안전

# 로블록스 호출 동시성 상한 (서버 IP 예산 100 중 배치 몫 — server의 50과 합산 주의)
ROBLOX_CONCURRENCY = 50
