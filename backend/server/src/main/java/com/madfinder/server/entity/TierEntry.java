package com.madfinder.server.entity;

/**
 * tier_entries 테이블 매핑. (원본: docs/KJH/db-schema.sql)
 * 컬럼: user_id+universe_id(복합PK), tier(SSS/A/B/C), position, updated_at
 * 유저당 1세트 덮어쓰기. position=티어 내 순서(왼쪽=선호, 가중치에 사용). SSS 최대 2개는 코드 검증
 * TODO(KJH): @Entity/@Table/@Id 및 필드 작성. 복합 PK는 @IdClass 사용.
 */
public class TierEntry {
}
