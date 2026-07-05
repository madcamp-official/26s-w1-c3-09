"""
배치 워커 공통 설정. (담당: KJH)
DB 접속: 환경변수 — server의 application.yaml과 같은 변수명 (.env.example 참고)
rate 예산: ../config/rate_governance.json 이 진실의 원천 (A-1: 엔드포인트별 독립 버킷).
          "IP 전역 예산"·"동시성 상한" 개념은 폐기됨 — rate(초당 호출)로만 제어.
"""
import json
import os

DB_HOST = os.environ.get("DB_HOST", "localhost")   # 없으면 로컬 (server와 동일 원칙)
DB_PORT = 3306
DB_NAME = "roblox_rec"
DB_USER = "root"
DB_PASSWORD = os.environ.get("DB_PASSWORD")        # 필수 — 없으면 시작 시 에러 내는 게 안전

# 공용 설정 디렉토리 (server와 공유)
CONFIG_DIR = os.environ.get("CONFIG_DIR", os.path.join(os.path.dirname(__file__), "..", "config"))


def load_rate_governance():
    """rate_governance.json 로드 → dict. server와 같은 파일(진실의 원천 하나)."""
    path = os.path.join(CONFIG_DIR, "rate_governance.json")
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def load_collection():
    """collection.json 로드 → dict. 배치 전용(수집 정책)."""
    path = os.path.join(CONFIG_DIR, "collection.json")
    with open(path, encoding="utf-8") as f:
        return json.load(f)

