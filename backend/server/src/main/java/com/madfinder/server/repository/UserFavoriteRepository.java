package com.madfinder.server.repository;

import com.madfinder.server.entity.UserFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * user_favorites 접근 계층.
 * "게임 X의 팬" = findByFavUniverseId(X) 역조회 (D-2, game_fans 대체 — idx_user_favorites_game 사용).
 */
public interface UserFavoriteRepository extends JpaRepository<UserFavorite, UserFavorite.Pk> {

    List<UserFavorite> findByUserId(Long userId);

    /** 역조회: 이 게임을 즐겨찾기한 수집 유저 (팬 목록) */
    List<UserFavorite> findByFavUniverseId(Long favUniverseId);

    void deleteByUserId(Long userId);             // 새로고침 시 전체 교체 (F-3)
}
