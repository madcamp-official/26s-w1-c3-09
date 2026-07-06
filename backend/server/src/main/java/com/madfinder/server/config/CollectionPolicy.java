package com.madfinder.server.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * backend/config/collection.json 매핑 — 수집 정책 (원 소비자는 배치 b4).
 * 서버는 정밀모드(G-1)의 "팬수집 자격 필터"(동접 하한·신생 기준·probe·표본)에 같은 값을 쓴다
 * — 배치와 자격 기준이 어긋나지 않게 진실의 원천 하나.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CollectionPolicy(FanCollection fanCollection) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FanCollection(
            Integer playingFloor,           // 동접 하한
            Double fanCacheableThreshold,   // 보유율 임계 (미만이면 부실 → 중단)
            Integer fanCacheableProbe,      // 판정 관찰 인원
            Integer minOverlap,             // cofavorite 최소 겹침
            Integer maxGamesPerRun,
            Integer preciseFavWorkers,      // 정밀모드 fav 병렬 스레드 수
            List<Stage> expansionLadder
    ) {
        /** 사다리 중 가장 넓은 나이 범위(년) — 정밀모드 자격의 신생 기준 */
        public double maxAgeYears() {
            return expansionLadder == null ? 3.0 : expansionLadder.stream()
                    .map(Stage::maxAgeYears).filter(a -> a != null)
                    .max(Double::compare).orElse(3.0);
        }

        /** 1단계 표본 크기 — 정밀모드 게임당 수집 인원 */
        public int sampleSize() {
            return expansionLadder == null || expansionLadder.isEmpty()
                    ? 200 : expansionLadder.get(0).sampleSize();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Stage(Integer stage, Double maxAgeYears, Integer sampleSize, String mode) {
    }
}
