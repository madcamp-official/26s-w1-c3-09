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
        Map<String, Section> sections,            // popular(약보정)/discovery(강보정) — alpha·개수
        int playingFloor,                         // 섹션2 동접 하한 (E-3)
        int minOverlap,                           // cofavorite 최소 겹침
        int similarCount,                         // 게임 상세 "비슷한 게임" 개수
        double derivedTierFactor,                 // 정밀 덤 게임(연쇄추천) 감쇠 계수 (원 티어 가중치 × 이 값, 예:0.15)
        int searchResultLimit,                    // 검색 결과 상위 N (명세: 10)
        double progressPositionRange,             // 정밀모드 진행률 위치 가중 폭(±) — 같은 등급 왼쪽일수록 큼
        int candidateBackfillLimit,               // 추천 후보 중 미보유 게임 즉석 채움 상한
        int shortsPerGame,                        // 유튜브 쇼츠 게임당 검색 수 (쿼터 관리)
        AgePenalty agePenalty                     // 나이 보정 (G-5: 점 보간)
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Section(double alpha, int count) {  // 유명도 보정 강도(E-4: 튜닝 예정)·섹션 표시 개수
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AgePenalty(java.util.List<java.util.List<Double>> points) {

        /** 게임 나이(년) → 점수 배수. 점 사이 선형보간, 범위 밖은 양끝 값. */
        public double factor(double years) {
            if (points == null || points.isEmpty()) {
                return 1.0;
            }
            if (years <= points.get(0).get(0)) {
                return points.get(0).get(1);
            }
            var last = points.get(points.size() - 1);
            if (years >= last.get(0)) {
                return last.get(1);
            }
            for (int i = 0; i < points.size() - 1; i++) {
                double x0 = points.get(i).get(0), y0 = points.get(i).get(1);
                double x1 = points.get(i + 1).get(0), y1 = points.get(i + 1).get(1);
                if (years >= x0 && years <= x1) {
                    return y0 + (y1 - y0) * (years - x0) / (x1 - x0);
                }
            }
            return 1.0;
        }
    }
}
