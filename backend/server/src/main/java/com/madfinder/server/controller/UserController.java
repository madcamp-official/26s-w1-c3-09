package com.madfinder.server.controller;

/**
 * E2. GET /api/users/{username}/favorites — 닉네임 확인 + 즐겨찾기 + 저장된 티어표. (담당: BMS)
 * 처리 순서 (백엔드-api-명세.md 참고):
 *   1) 로블록스 A-1(닉네임→userId). 없으면 404 USER_NOT_FOUND
 *   2) users UPSERT (UserRepository)
 *   3) 로블록스 E-3 즐겨찾기 — 항상 실시간 (DB 캐시 금지: 그 순간의 취향이어야 함)
 *   4) tier_entries 조회 → savedTier로 반환 (재방문자 복원)
 *   5) games에 없는 게임 → collect_queue 등록 (reason=user_favorite)
 * TODO(BMS): 구현. 로블록스 호출은 RobloxApiClient 사용.
 */
public class UserController {
}
