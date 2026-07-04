"""
B1. 인기 차트 수집. (담당: KJH) — B-4 확정: explore 최우선(매일), Rolimons 후순위(주1회 보강)
1) explore-api 27개 차트 순회 (공식·최신 반영 빠름 — Rolimons에 없는 신생 급상승작 22% 포함)
2) placeId → universeId 변환 (변환 결과 재사용)
3) chart_snapshot 덮어쓰기 (INSERT ... ON DUPLICATE KEY UPDATE)
4) 새로 등장한 게임 → collect_queue 등록
5) [주1회] Rolimons gamelist 보강 (롱테일 커버 — 브라우저 헤더 필수)
TODO(KJH): 구현.
"""
