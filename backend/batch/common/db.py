"""
MySQL 연결. (담당: KJH)
pymysql 컨텍스트매니저. config의 DB_* 사용. server와 같은 roblox_rec DB.
"""
import contextlib

import pymysql

import config


def connect():
    """단일 연결 생성. utf8mb4(이모지 게임명), autocommit=False(호출측이 commit)."""
    if not config.DB_PASSWORD:
        raise RuntimeError("DB_PASSWORD 환경변수가 없습니다 (.env.example 참고)")
    return pymysql.connect(
        host=config.DB_HOST,
        port=config.DB_PORT,
        user=config.DB_USER,
        password=config.DB_PASSWORD,
        database=config.DB_NAME,
        charset="utf8mb4",
        autocommit=False,
        cursorclass=pymysql.cursors.DictCursor,
    )


@contextlib.contextmanager
def cursor():
    """with db.cursor() as cur: ... 형태. 정상 종료 시 commit, 예외 시 rollback."""
    conn = connect()
    try:
        with conn.cursor() as cur:
            yield cur
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()
