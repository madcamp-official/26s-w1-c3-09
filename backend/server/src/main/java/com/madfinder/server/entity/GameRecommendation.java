package com.madfinder.server.entity;

/**
 * game_recommendations 테이블 매핑. (원본: docs/KJH/db-schema.sql)
 * 컬럼: from_universe_id+to_universe_id(복합PK), rec_rank(1~6), fetched_at
 * People Also Join 엣지. depth는 저장하지 않음(조회 횟수로 구현)
 * TODO(KJH): @Entity/@Table/@Id 및 필드 작성. 복합 PK는 @IdClass 사용.
 */
public class GameRecommendation {
}
