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
 * games — 게임 메타데이터 캐시 (모든 테이블의 중심). (원본: docs/KJH/db-schema.sql §1)
 * updated_at은 로블록스 재조회(B-1) 시에만 코드가 명시적으로 갱신 (자동갱신 금지 — F-2 신선도 판단 기준).
 */
@Entity
@Table(name = "games")
@Getter
@Setter
@NoArgsConstructor
public class Game {

    @Id
    @Column(name = "universe_id")
    private Long universeId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "name", nullable = false, columnDefinition = "TEXT")
    private String name;                          // 이모지 포함 가능 → utf8mb4

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "genre_l1", length = 64)
    private String genreL1;

    @Column(name = "genre_l2", length = 64)
    private String genreL2;

    @Column(name = "playing")
    private Integer playing;                      // 동접 — 유명도 보정·섹션2 하한(E-3)

    @Column(name = "visits")
    private Long visits;

    @Column(name = "favorited_count")
    private Long favoritedCount;                  // top-down 캐싱 우선순위(F-2)

    @Column(name = "up_votes")
    private Integer upVotes;

    @Column(name = "down_votes")
    private Integer downVotes;

    @Column(name = "creator_type", length = 8)
    private String creatorType;                   // 'Group' / 'User'

    @Column(name = "creator_group_id")
    private Long creatorGroupId;

    @Column(name = "minimum_age")
    private Short minimumAge;

    @Column(name = "icon_url", columnDefinition = "TEXT")
    private String iconUrl;                       // CDN URL 만료 가능 → 주기 갱신

    @Column(name = "fan_cacheable")
    private Boolean fanCacheable;                 // NULL=미판정

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
