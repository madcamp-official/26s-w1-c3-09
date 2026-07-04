package com.madfinder.server.entity;

/**
 * group_cursors 테이블 매핑. (원본: docs/KJH/db-schema.sql)
 * 컬럼: group_id(PK), member_count, sort_order, anchor_cursor, progress_cursor, fans_collected, collection_status, updated_at
 * 그룹당 1행. anchor=정착유저 시작점 / progress=중단 지점(이어받기)
 * TODO(KJH): @Entity/@Table/@Id 및 필드 작성. 복합 PK는 @IdClass 사용.
 */
public class GroupCursor {
}
