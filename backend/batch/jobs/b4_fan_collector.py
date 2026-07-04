"""
B4. 팬 수집 (매일 새벽, 가장 무거움). (담당: KJH) — 시스템-설계서 §5-4
대상: creator_type='Group' 이고 fan_cacheable != false
1) E-0 그룹정보 → member_count 저장, 최신 가입자 스킵 지점 계산
2) E-2 커서 순회 (순차, 병렬 불가) — anchor_cursor에서 시작 / progress_cursor로 이어받기
3) 멤버 즐겨찾기 E-3 병렬 조회 (user_favorites에 있고 최신이면 재조회 생략)
4) 팬 판별(그룹+즐겨찾기, 임계값 이상) → game_fans + user_favorites 저장
5) 완료: anchor 저장, status=complete / 중단: progress_cursor+fans_collected 저장
6) 집계: user_favorites GROUP BY (겹침 1명 제외) → game_cofavorite
7) 부실 판정(RIVALS형) → fan_cacheable=false
※ 기존 수집 스크립트(collect_10k 계열)를 DB 저장형으로 이식
TODO(KJH): 구현.
"""
