package com.madfinder.server.service;

/**
 * 추천 점수 계산 (알고리즘 핵심). (담당: KJH)
 * 공식: 시스템-설계서 §3 — score = Σ(tier_w × pos_w × depth_w) × 겹침보정 + cofavorite − 유명도보정
 * 파라미터(tier_w, depth_w, 겹침 bonus 등)는 코드 상수 — 튜닝 대상.
 * 입력: 후보 게임 목록(도달 경로 포함) + 티어표 / 출력: (게임, 점수) 목록
 * TODO(KJH): 구현 + Korea Army 실데이터로 검증. 시그니처 확정 후 BMS에 공지.
 */
public class RecommendService {
}
