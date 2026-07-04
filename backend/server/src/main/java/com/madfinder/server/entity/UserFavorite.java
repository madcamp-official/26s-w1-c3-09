package com.madfinder.server.entity;

/**
 * user_favorites 테이블 매핑. (원본: docs/KJH/db-schema.sql)
 * 컬럼: user_id+fav_universe_id(복합PK), recorded_at
 * 수집된 팬의 즐겨찾기 원시 풀(크로스 게임 재사용). 1년 초과분은 배치가 삭제
 * TODO(KJH): @Entity/@Table/@Id 및 필드 작성. 복합 PK는 @IdClass 사용.
 */
public class UserFavorite {
}
