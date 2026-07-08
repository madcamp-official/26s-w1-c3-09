package com.madfinder.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * collect_queue — 게임 수집 대기열 (lazy population). (원본: docs/schema/db-schema.sql §8)
 * 등록 경로: ①마이너 게임 티어 배치 ②유저 즐겨찾기 중 DB에 없는 게임 ③연쇄 추천 미보유 ④수집 중단.
 * (유저 fav 새로고침 우선순위는 users.fav_fetched_at 소관 — 여기 아님)
 */
@Entity
@Table(name = "collect_queue")
@Getter
@Setter
@NoArgsConstructor
public class CollectQueue {

    @Id
    @Column(name = "universe_id")
    private Long universeId;

    @Column(name = "reason", nullable = false, length = 32)
    private String reason;                        // 'user_tier'/'user_favorite'/'recommendation'/'timeout'

    @Column(name = "status", nullable = false, length = 16)
    private String status = "pending";            // pending/partial/done/failed

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;              // DB ON UPDATE 자동갱신
}
