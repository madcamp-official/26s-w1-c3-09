package com.madfinder.server.entity;

/**
 * chart_snapshot 테이블 매핑. (원본: docs/KJH/db-schema.sql)
 * 컬럼: sort_id+universe_id(복합PK), chart_rank, snapshot_at
 * 인기 차트. 덮어쓰기(이력 없음). sort_id 예: rolimons, most-popular
 * TODO(KJH): @Entity/@Table/@Id 및 필드 작성. 복합 PK는 @IdClass 사용.
 */
public class ChartSnapshot {
}
