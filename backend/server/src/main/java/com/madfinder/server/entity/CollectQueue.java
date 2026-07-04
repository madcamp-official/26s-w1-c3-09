package com.madfinder.server.entity;

/**
 * collect_queue 테이블 매핑. (원본: docs/KJH/db-schema.sql)
 * 컬럼: universe_id(PK), reason, status(pending/partial/done/failed), requested_at, updated_at
 * lazy population 대기열 — 배치 B2가 소비
 * TODO(KJH): @Entity/@Table/@Id 및 필드 작성. 복합 PK는 @IdClass 사용.
 */
public class CollectQueue {
}
