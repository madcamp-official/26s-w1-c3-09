"""
B1. 인기 차트 수집 (매일 1~2회). (담당: KJH) — 시스템-설계서 §5-2
1) Rolimons gamelist (주력, 브라우저 헤더 필수) → 실패(403 등) 시 explore-api 26종 폴백
2) placeId → universeId 변환 (B-4, 변환 결과 재사용)
3) chart_snapshot 덮어쓰기 (INSERT ... ON DUPLICATE KEY UPDATE)
4) 새로 등장한 게임 → collect_queue 등록
TODO(KJH): 구현.
"""
