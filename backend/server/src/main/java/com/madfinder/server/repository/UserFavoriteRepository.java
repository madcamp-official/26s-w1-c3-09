package com.madfinder.server.repository;

/**
 * user_favorites 접근 계층. (담당: KJH — 쿼리 전부 여기에)
 * 주요: user_id IN → GROUP BY fav_universe_id 집계(@Query, cofavorite 계산), recorded_at 1년 초과 삭제
 * TODO(KJH): JpaRepository 상속으로 전환 후 쿼리 메서드/@Query 작성.
 */
public interface UserFavoriteRepository {
}
