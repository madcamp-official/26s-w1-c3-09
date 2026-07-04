package com.madfinder.server.entity;

/**
 * user_recommendations 테이블 매핑. (원본: docs/KJH/db-schema.sql)
 * 컬럼: user_id+universe_id(복합PK), score, rec_rank, created_at
 * 추천 결과 저장(뒤로가기 복원·재방문 열람). 재계산 시 전체 덮어쓰기
 * TODO(KJH): @Entity/@Table/@Id 및 필드 작성. 복합 PK는 @IdClass 사용.
 */
public class UserRecommendation {
}
