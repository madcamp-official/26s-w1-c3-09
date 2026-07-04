package com.madfinder.server.entity;

/**
 * games 테이블 매핑. (원본: docs/KJH/db-schema.sql)
 * 컬럼: universe_id(PK,BIGINT), place_id, name, description, genre_l1/l2, playing, visits, favorited_count, up/down_votes, creator_type, creator_group_id, minimum_age, icon_url, fan_cacheable, updated_at
 * updated_at은 로블록스 재조회 시에만 코드가 갱신 (자동갱신 금지)
 * TODO(KJH): @Entity/@Table/@Id 및 필드 작성. 복합 PK는 @IdClass 사용.
 */
public class Game {
}
