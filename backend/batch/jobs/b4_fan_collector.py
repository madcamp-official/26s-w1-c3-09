"""
B4. 유저 즐겨찾기 수집 (가장 무거움). (담당: KJH) — B-2/B-3/C-1/D-1 확정 설계
대상: 차트 등재 + 신생(2년 이내) + creator_type='Group' 우선

1) 그룹 멤버를 Asc(오래된 가입순)로 게임당 200명 순회 (B-2)
   - 스킵 없음: 뉴비 스킵·개발자(앞 100명) 스킵 전부 폐기 (B-3 — 효과 없음 실측)
   - Asc라 커서 밀림 없음 → 중단 시 progress_cursor로 정확히 이어받기
2) 멤버 즐겨찾기 조회 → user_favorites에 무조건 저장 (D-1 — "팬 판별·임계값" 폐기)
   - game_fans 테이블은 삭제됨: "게임 X의 팬" = user_favorites 역조회 (D-2)
   - 이미 있고 최신인 유저는 재조회 생략
3) 커서·진행 저장: group_cursors (users_collected, progress_cursor, 완료 시 status=complete)
4) 집계: user_favorites GROUP BY → game_cofavorite (겹침 1명 제외)
5) 확장 순서: 1단계 200명 광역 → (커버 완료 후) 범위 확장 or 커서 재개 500명 심화 (C-1)
※ 검증된 원형: collect_jujutsu1k 계열 (Asc 200명, AIMD) — DB 저장형으로 이식
TODO(KJH): 구현.
"""
