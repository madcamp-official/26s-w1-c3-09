package com.madfinder.server.entity;

/**
 * game_videos 테이블 매핑. (원본: docs/KJH/db-schema.sql)
 * 컬럼: universe_id+youtube_video_id(복합PK), title, thumbnail_url, display_order, fetched_at
 * 유튜브 검색 캐시(하루 100회 할당량 보호). 영상 1개=1행
 * TODO(KJH): @Entity/@Table/@Id 및 필드 작성. 복합 PK는 @IdClass 사용.
 */
public class GameVideo {
}
