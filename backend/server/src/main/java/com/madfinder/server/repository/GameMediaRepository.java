package com.madfinder.server.repository;

import com.madfinder.server.entity.GameMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** game_media 접근 계층. 게임 상세 페이지 스크린샷·영상 조회. */
public interface GameMediaRepository extends JpaRepository<GameMedia, GameMedia.Pk> {

    List<GameMedia> findByUniverseIdOrderBySortOrderAsc(Long universeId);

    void deleteByUniverseId(Long universeId);     // 재수집 시 전체 교체
}
