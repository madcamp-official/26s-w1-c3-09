package com.madfinder.server.repository;

import com.madfinder.server.entity.UserRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** user_recommendations 접근 계층. 유저당 1세트 — 재계산 시 전체 삭제 후 재삽입. */
public interface UserRecommendationRepository extends JpaRepository<UserRecommendation, UserRecommendation.Pk> {

    List<UserRecommendation> findByUserIdOrderByRecRankAsc(Long userId);

    void deleteByUserId(Long userId);
}
