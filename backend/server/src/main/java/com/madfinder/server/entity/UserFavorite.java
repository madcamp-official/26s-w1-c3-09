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
 * user_favorites — 유저별 즐겨찾기 풀 (원시, 크로스 게임 재사용). (원본: docs/KJH/db-schema.sql §4)
 * D-1: 조회한 유저의 즐겨찾기는 무조건 저장. "게임 X의 팬" = fav_universe_id=X 역조회 (D-2, game_fans 대체).
 */
@Entity
@Table(name = "user_favorites")
@IdClass(UserFavorite.Pk.class)
@Getter
@Setter
@NoArgsConstructor
public class UserFavorite {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "fav_universe_id")
    private Long favUniverseId;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt = LocalDateTime.now();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private Long userId;
        private Long favUniverseId;
    }
}
