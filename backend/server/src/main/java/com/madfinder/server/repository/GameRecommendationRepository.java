package com.madfinder.server.repository;

/**
 * game_recommendations 접근 계층. (담당: KJH — 쿼리 전부 여기에)
 * 주요: from_universe_id IN 조회(depth 확장), fetched_at 오래된 것(B3 재수집)
 * TODO(KJH): JpaRepository 상속으로 전환 후 쿼리 메서드/@Query 작성.
 */
public interface GameRecommendationRepository {
}
