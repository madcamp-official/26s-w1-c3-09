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
 * game_cofavorite — 공통 즐겨찾기 집계 (추천 핵심 캐시). (원본: docs/KJH/db-schema.sql §5)
 * user_favorites(원시)에서 배치(b3)가 계산. overlap_count=1은 노이즈 컷으로 저장 안 함.
 * F-6: 실시간 추천은 이 테이블 depth1 탐색만 (rec API 실시간 호출 없음).
 */
@Entity
@Table(name = "game_cofavorite")
@IdClass(GameCofavorite.Pk.class)
@Getter
@Setter
@NoArgsConstructor
public class GameCofavorite {

    @Id
    @Column(name = "seed_universe_id")
    private Long seedUniverseId;                  // 기준 게임

    @Id
    @Column(name = "related_universe_id")
    private Long relatedUniverseId;               // 함께 즐겨찾기된 게임

    @Column(name = "overlap_count", nullable = false)
    private Integer overlapCount;                 // 겹친 유저 수 (가중치 원천)

    @Column(name = "sample_size", nullable = false)
    private Integer sampleSize;                   // 집계에 쓴 유저 수 (신뢰도)

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt = LocalDateTime.now();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private Long seedUniverseId;
        private Long relatedUniverseId;
    }
}
