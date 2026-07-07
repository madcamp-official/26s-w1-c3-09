import { getGameById, getGamesByIds, getRelatedGameIds } from './games.selectors';
import type { GameDetailResponse, GameVideo } from '../../types/game';

/**
 * YouTube Data API 연동 전 데모용 영상.
 * 게임과 무관하지만 삭제될 가능성이 매우 낮은 유명 공개 영상의 videoId를 사용한다.
 * 백엔드가 game_videos 테이블을 채우면 이 목록은 제거된다.
 */
const DEMO_VIDEO_IDS = ['dQw4w9WgXcQ', 'jNQXAC9IVRw', '9bZkp7q19f0'];

function buildDemoVideos(gameName: string): GameVideo[] {
  const titles = [
    `${gameName} 숨겨진 꿀팁 TOP 5 🔥`,
    `${gameName} 초보자 가이드`,
    `${gameName} 레전드 순간 모음`,
  ];
  const views = ['1.7M 조회', '890K 조회', '2.3M 조회'];
  return DEMO_VIDEO_IDS.map((youtubeVideoId, i) => ({
    youtubeVideoId,
    title: titles[i],
    viewsLabel: views[i],
  }));
}

type GameDetailResult = { ok: true; data: GameDetailResponse } | { ok: false; error: 'NOT_FOUND' };

export function getGameDetail(gameId: string): GameDetailResult {
  const game = getGameById(gameId);
  if (!game) return { ok: false, error: 'NOT_FOUND' };

  return {
    ok: true,
    data: {
      game,
      screenshots: [], // 목업은 실제 스크린샷 URL이 없어 빈 배열 → 히어로는 그라데이션 폴백
      relatedGames: getGamesByIds(getRelatedGameIds(gameId)),
      videos: buildDemoVideos(game.name),
    },
  };
}
