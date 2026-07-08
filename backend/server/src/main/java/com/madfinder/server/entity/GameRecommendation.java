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
 * game_recommendations — 연쇄 추천(People Also Join) 캐시, 게임당 최대 6개. (원본: docs/schema/db-schema.sql §3)
 * to_universe_id는 games에 아직 없을 수 있음 → FK 없음 (collect_queue로 채움).
 */
@Entity
@Table(name = "game_recommendations")
@IdClass(GameRecommendation.Pk.class)
@Getter
@Setter
@NoArgsConstructor
public class GameRecommendation {

    @Id
    @Column(name = "from_universe_id")
    private Long fromUniverseId;

    @Id
    @Column(name = "to_universe_id")
    private Long toUniverseId;

    @Column(name = "rec_rank", nullable = false)
    private Short recRank;                        // 1~6 (연관 강도)

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt = LocalDateTime.now();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private Long fromUniverseId;
        private Long toUniverseId;
    }
}
