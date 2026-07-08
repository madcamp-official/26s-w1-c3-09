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
 * chart_snapshot — 인기 차트 (덮어쓰기, 이력 없음). (원본: docs/schema/db-schema.sql §7)
 * B-4: explore-api 매일(우선) + Rolimons 주1회(보강).
 */
@Entity
@Table(name = "chart_snapshot")
@IdClass(ChartSnapshot.Pk.class)
@Getter
@Setter
@NoArgsConstructor
public class ChartSnapshot {

    @Id
    @Column(name = "sort_id", length = 64)
    private String sortId;                        // 'rolimons' / 'most-popular' 등

    @Id
    @Column(name = "universe_id")
    private Long universeId;

    @Column(name = "chart_rank", nullable = false)
    private Integer chartRank;

    @Column(name = "snapshot_at", nullable = false)
    private LocalDateTime snapshotAt = LocalDateTime.now();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private String sortId;
        private Long universeId;
    }
}
