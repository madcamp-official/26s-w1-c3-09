"""
파일 로그. (담당: KJH)
새벽 배치가 뭘 했는지 아침에 확인하는 용도 — logs/ 폴더에 날짜별 기록.
콘솔(cp949) 인코딩 이슈 방지: 파일은 UTF-8, 콘솔은 안전 폴백.
"""
import logging
import os
import sys

_LOG_DIR = os.path.join(os.path.dirname(os.path.dirname(__file__)), "logs")


def get_logger(name):
    """잡별 로거. logs/{name}.log(UTF-8) + 콘솔. 날짜는 포맷에 포함."""
    logger = logging.getLogger(name)
    if logger.handlers:
        return logger
    logger.setLevel(logging.INFO)
    fmt = logging.Formatter("%(asctime)s | %(name)s | %(levelname)s | %(message)s",
                            datefmt="%Y-%m-%d %H:%M:%S")

    os.makedirs(_LOG_DIR, exist_ok=True)
    fh = logging.FileHandler(os.path.join(_LOG_DIR, f"{name}.log"), encoding="utf-8")
    fh.setFormatter(fmt)
    logger.addHandler(fh)

    ch = logging.StreamHandler(sys.stdout)
    ch.setFormatter(fmt)
    logger.addHandler(ch)
    return logger
