package com.madfinder.server.repository;

import com.madfinder.server.entity.GameVideo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** game_videos 접근 계층. 유튜브 할당량(하루 100회) 절약 — 캐시 우선 조회. */
public interface GameVideoRepository extends JpaRepository<GameVideo, GameVideo.Pk> {

    List<GameVideo> findByUniverseIdOrderByDisplayOrderAsc(Long universeId);

    boolean existsByUniverseId(Long universeId);  // 이미 검색한 게임인지 (재검색 방지)
}
