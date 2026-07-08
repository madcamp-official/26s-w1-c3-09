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
 * game_media — 스크린샷 + 개발자 등록 영상. (원본: docs/schema/db-schema.sql §2)
 * video_asset_id는 로블록스 에셋 ID(유튜브 아님). CDN URL은 만료 토큰 포함 → ID만 저장, 재생 시 재발급.
 */
@Entity
@Table(name = "game_media")
@IdClass(GameMedia.Pk.class)
@Getter
@Setter
@NoArgsConstructor
public class GameMedia {

    @Id
    @Column(name = "universe_id")
    private Long universeId;

    @Id
    @Column(name = "sort_order")
    private Short sortOrder;                      // 응답 배열 순서

    @Column(name = "asset_type", nullable = false, length = 32)
    private String assetType;                     // 'Image' / 'GamePreviewVideo'

    @Column(name = "image_id")
    private Long imageId;

    @Column(name = "video_asset_id")
    private Long videoAssetId;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt = LocalDateTime.now();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private Long universeId;
        private Short sortOrder;
    }
}
