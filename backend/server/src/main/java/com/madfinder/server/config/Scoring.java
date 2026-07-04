package com.madfinder.server.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * backend/config/scoring.json 매핑 — 추천 점수 튜닝값 (F-7).
 * 코드에 박지 않고 여기서 조정: raw(c) = Σ 티어가중치(g) × cofav(g, c), 이후 cofav/visits^alpha 두 섹션.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Scoring(
        Map<String, Double> tierWeights,          // SSS=5.5, A=3, B=2, C=1
        double unplacedFavoriteWeight,            // 미배치 즐겨찾기 = 0.3 (약한 양의 신호)
        int sssMaxCount,                          // SSS 특별 자리 2개
        boolean excludeUserFavoritesFromCandidates, // 즐겨찾기 전부 후보 제외 (미배치 포함)
        Map<String, Section> sections,            // popular(약보정)/discovery(강보정)
        int playingFloor,                         // 섹션2 동접 하한 (E-3)
        int minOverlap,                           // cofavorite 최소 겹침
        int topN                                  // 응답 상위 N
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Section(double alpha) {         // 유명도 보정 강도 (E-4: 튜닝 예정)
    }
}
