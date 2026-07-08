package com.madfinder.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * user_recommendations — 유저별 추천 결과 (유저당 1세트, 덮어쓰기). (원본: docs/schema/db-schema.sql §11)
 * 상세 갔다 돌아오기·재방문 시 재계산 없이 복원. 새로 돌리면 해당 유저 전체 삭제 후 재삽입.
 */
@Entity
@Table(name = "user_recommendations")
@IdClass(UserRecommendation.Pk.class)
@Getter
@Setter
@NoArgsConstructor
public class UserRecommendation {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "universe_id")
    private Long universeId;                      // 추천된 게임

    @Id
    @Column(name = "section")
    private String section;                       // 'popular' / 'discovery' — 중복 허용이라 PK에 포함

    @Column(name = "score", nullable = false)
    private Double score;                         // F-7 최종 점수

    @Column(name = "rec_rank", nullable = false)
    private Short recRank;                        // 표시 순위 1,2,3...

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private Long userId;
        private Long universeId;
        private String section;
    }
}
