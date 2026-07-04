package com.madfinder.server.repository;

import com.madfinder.server.entity.Game;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * games 접근 계층.
 * 표시용 일괄조회(IN) + top-down 캐싱(F-2: favoritedCount 상위·오래된 순 갱신) 지원.
 */
public interface GameRepository extends JpaRepository<Game, Long> {

    /** 추천 결과·즐겨찾기 표시용 일괄 조회 */
    List<Game> findByUniverseIdIn(Collection<Long> universeIds);

    /** top-down 캐싱 우선순위: 즐겨찾기 수 상위부터 (F-2) */
    List<Game> findByOrderByFavoritedCountDesc(Pageable pageable);

    /** 갱신 대상 선별: 오래된 캐시부터 (F-2 신선도) */
    List<Game> findByUpdatedAtBeforeOrderByUpdatedAtAsc(LocalDateTime threshold, Pageable pageable);
}
