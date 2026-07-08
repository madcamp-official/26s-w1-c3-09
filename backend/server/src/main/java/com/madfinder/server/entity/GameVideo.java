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
 * game_videos — 유튜브 영상 캐시 (개발자 영상 없을 때 폴백). (원본: docs/schema/db-schema.sql §12)
 * 유튜브 G-1은 하루 100회 한도 → 한 번 검색한 게임은 여기서만 조회.
 */
@Entity
@Table(name = "game_videos")
@IdClass(GameVideo.Pk.class)
@Getter
@Setter
@NoArgsConstructor
public class GameVideo {

    @Id
    @Column(name = "universe_id")
    private Long universeId;

    @Id
    @Column(name = "youtube_video_id", length = 20)
    private String youtubeVideoId;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    @Column(name = "display_order", nullable = false)
    private Short displayOrder = 0;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt = LocalDateTime.now();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private Long universeId;
        private String youtubeVideoId;
    }
}
