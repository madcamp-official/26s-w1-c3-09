package com.madfinder.server.dto;

/** PUT /api/tiers 응답. */
public record TierSaveResponse(
        boolean ok,
        int saved
) {
}
