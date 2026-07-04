package com.madfinder.server.dto;

import java.util.List;

/** PUT /api/tiers 요청. 유저당 1세트 전체 덮어쓰기. */
public record TierSaveRequest(
        Long userId,
        List<TierEntryDto> entries
) {
}
