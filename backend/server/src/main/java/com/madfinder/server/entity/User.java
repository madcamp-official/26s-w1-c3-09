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
 * users — 서비스를 이용한 로블록스 유저 (로그인 없음, 닉네임 → userId 확인 후 조회). (원본: docs/schema/db-schema.sql §9)
 * F-3/4/5: fav_fetched_at이 즐겨찾기 새로고침 우선순위 기준 (NULL=최초 미조회 → username 레인 대상).
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @Column(name = "user_id")
    private Long userId;                          // 로블록스 userId 그대로 (불변)

    @Column(name = "username", nullable = false, length = 50)
    private String username;                      // 마지막 확인 닉네임 (표시용 — 바뀔 수 있음)

    @Column(name = "display_name", length = 50)
    private String displayName;

    @Column(name = "fav_fetched_at")
    private LocalDateTime favFetchedAt;           // NULL=최초 미조회. 오래된 순 새로고침(F-5)

    @Column(name = "last_seen_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime lastSeenAt;             // DB ON UPDATE 자동갱신
}
