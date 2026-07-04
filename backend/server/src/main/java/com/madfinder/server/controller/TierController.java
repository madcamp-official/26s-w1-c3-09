package com.madfinder.server.controller;

/**
 * E4. PUT /api/tiers — 티어표 저장 (유저당 1세트 전체 덮어쓰기). (담당: BMS)
 * 검증: tier ∈ {SSS,A,B,C}, SSS 최대 2개, entries 비면 400
 * 저장: 트랜잭션으로 DELETE(해당 user 전체) + INSERT (TierEntryRepository)
 * 미보유 게임 → collect_queue 등록 (reason=user_tier)
 * TODO(BMS): 구현.
 */
public class TierController {
}
