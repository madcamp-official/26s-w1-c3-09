package com.madfinder.server.entity;

/**
 * game_media 테이블 매핑. (원본: docs/KJH/db-schema.sql)
 * 컬럼: universe_id+sort_order(복합PK), asset_type, image_id, video_asset_id, fetched_at
 * video_asset_id는 로블록스 영상 에셋(유튜브 아님) — 재생 URL은 매번 assetdelivery로 발급
 * TODO(KJH): @Entity/@Table/@Id 및 필드 작성. 복합 PK는 @IdClass 사용.
 */
public class GameMedia {
}
