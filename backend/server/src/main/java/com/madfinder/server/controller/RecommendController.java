package com.madfinder.server.controller;

/**
 * E5. POST /api/recommend — 추천 계산 실행 (서비스 핵심). (담당: BMS=흐름 / KJH=점수)
 *   1) tier_entries 로드
 *   2) 1단계: game_recommendations 캐시 우선, 미스는 C-1 즉석 호출+저장.
 *      depth1~2 보장, depth3는 캐시에 있을 때만 확장 (시스템-설계서 §2-2)
 *   3) 2단계: 상위 2게임의 game_cofavorite 조회(없으면 건너뛰고 collect_queue 기록)
 *   4) 점수 계산 → RecommendService.score(...) 호출 ← KJH가 제공하는 함수
 *   5) user_recommendations 덮어쓰기 저장 → games JOIN해서 응답
 *
 * E6. GET /api/recommendations/{userId} — 저장된 결과 재조회 (뒤로가기 복원, 재계산 없음)
 * TODO(BMS): 흐름 구현. 점수 함수 시그니처는 KJH와 합의.
 */
public class RecommendController {
}
