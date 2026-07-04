package com.madfinder.server.entity;

/**
 * game_cofavorite 테이블 매핑. (원본: docs/KJH/db-schema.sql)
 * 컬럼: seed_universe_id+related_universe_id(복합PK), overlap_count, sample_size, computed_at
 * 팬 공통 즐겨찾기 집계 캐시(배치가 계산). 겹침 1명은 저장 안 함
 * TODO(KJH): @Entity/@Table/@Id 및 필드 작성. 복합 PK는 @IdClass 사용.
 */
public class GameCofavorite {
}
