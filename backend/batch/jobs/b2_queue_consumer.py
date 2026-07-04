"""
B2. collect_queue 소비 (30분마다) — lazy population. (담당: KJH) — 시스템-설계서 §5-3
1) status='pending' LIMIT 50 조회
2) 로블록스 B-1(50개 묶음)+B-2(투표)+F-1(아이콘) → games 채움
3) status='done' / 실패 시 'failed'
TODO(KJH): 구현.
"""
