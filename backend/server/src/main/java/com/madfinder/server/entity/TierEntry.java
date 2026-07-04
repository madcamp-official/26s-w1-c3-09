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
 * tier_entries — 유저의 마지막 티어표 (유저당 1세트, 덮어쓰기). (원본: docs/KJH/db-schema.sql §10)
 * tier ∈ SSS/A/B/C. SSS 최대 2개 제한은 서비스 계층에서 검증 (스키마 강제 아님).
 * 가중치(SSS=5.5 등)는 backend/config/scoring.json — 여기엔 등급만 저장 (F-7).
 */
@Entity
@Table(name = "tier_entries")
@IdClass(TierEntry.Pk.class)
@Getter
@Setter
@NoArgsConstructor
public class TierEntry {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "universe_id")
    private Long universeId;

    @Column(name = "tier", nullable = false, length = 4)
    private String tier;                          // 'SSS'/'A'/'B'/'C'

    @Column(name = "position", nullable = false)
    private Short position = 0;                   // 티어 내 표시 순서

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;              // DB ON UPDATE 자동갱신

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private Long userId;
        private Long universeId;
    }
}
