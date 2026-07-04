package com.madfinder.server.controller;

/**
 * E7. GET /api/games/{universeId} — 게임 상세. (담당: BMS)
 *   games/game_media 조회 (미스·낡음 → B-1/B-2/B-3 호출 후 저장)
 *   스크린샷: image_id → F-2로 URL 변환 / 영상: video_asset_id → F-3로 매번 URL 발급(저장 금지)
 *
 * E8. GET /api/games/{universeId}/videos — 유튜브 폴백.
 *   game_videos 캐시 → 미스면 G-1 호출(하루 100회 제한!) → 저장 후 반환
 * TODO(BMS): 구현.
 */
public class GameController {
}
