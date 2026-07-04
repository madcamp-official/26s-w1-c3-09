package com.madfinder.server.repository;

import com.madfinder.server.entity.GameCofavorite;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** game_cofavorite 접근 계층. 추천 계산의 depth1 탐색(F-6)·비슷한 게임. */
public interface GameCofavoriteRepository extends JpaRepository<GameCofavorite, GameCofavorite.Pk> {

    /** 티어표 게임들의 cofavorite 일괄 로드 (F-7 가중합산 입력) */
    List<GameCofavorite> findBySeedUniverseIdIn(Collection<Long> seedUniverseIds);

    /** 한 게임의 연관 상위 N (비슷한 게임 6개 등) */
    List<GameCofavorite> findBySeedUniverseIdOrderByOverlapCountDesc(Long seedUniverseId, Pageable pageable);
}
