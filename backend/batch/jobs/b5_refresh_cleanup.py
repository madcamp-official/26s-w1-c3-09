"""
B5. 갱신·청소 (매일 새벽). (담당: KJH) — 시스템-설계서 §5-5
- games: updated_at 24h 초과 → B-1 재조회 (updated_at 명시적 갱신)
- user_favorites: recorded_at 1년 초과 DELETE
- game_recommendations/media/videos: fetched_at 기준 재수집
- group_cursors: 오래된 앵커 재검증
TODO(KJH): 구현.
"""
