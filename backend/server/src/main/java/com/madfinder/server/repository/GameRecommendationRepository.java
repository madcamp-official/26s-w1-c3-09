package com.madfinder.server.repository;

import com.madfinder.server.entity.GameRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** game_recommendations 접근 계층. 게임별 "비슷한 게임 6개"(depth1, F-6). */
public interface GameRecommendationRepository extends JpaRepository<GameRecommendation, GameRecommendation.Pk> {

    List<GameRecommendation> findByFromUniverseIdOrderByRecRankAsc(Long fromUniverseId);

    List<GameRecommendation> findByFromUniverseIdIn(Collection<Long> fromUniverseIds);
}
