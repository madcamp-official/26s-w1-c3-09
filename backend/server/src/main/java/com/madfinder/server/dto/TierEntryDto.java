package com.madfinder.server.dto;

/** 티어표 항목 1개. tier ∈ SSS/A/B/C, position은 티어 내 왼쪽부터 1. */
public record TierEntryDto(
        Long universeId,
        String tier,
        Integer position
) {
}
