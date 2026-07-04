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
 * group_cursors — 그룹별 멤버 조회 커서 (그룹당 1행). (원본: docs/KJH/db-schema.sql §6)
 * B-2: Asc(오래된순) 수집 확정 — 커서 밀림 없음. C-1: 200명 지점 커서 저장 → 2단계 이어받기.
 */
@Entity
@Table(name = "group_cursors")
@Getter
@Setter
@NoArgsConstructor
public class GroupCursor {

    @Id
    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "member_count")
    private Long memberCount;

    @Column(name = "sort_order", nullable = false, length = 4)
    private String sortOrder = "Asc";             // B-2 확정: 오래된순

    @Column(name = "anchor_cursor", columnDefinition = "TEXT")
    private String anchorCursor;

    @Column(name = "progress_cursor", columnDefinition = "TEXT")
    private String progressCursor;                // 중단 지점 이어받기

    @Column(name = "users_collected", nullable = false)
    private Integer usersCollected = 0;           // D-1: 무조건 저장 기준 수집 유저 수

    @Column(name = "collection_status", nullable = false, length = 16)
    private String collectionStatus = "idle";     // idle/in_progress/complete

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;              // DB ON UPDATE 자동갱신
}
