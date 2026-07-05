import type { GameDetailResponse } from '../../types/game';
import type { BackendGameDetail, BackendVideo } from '../common/backendAdapter';
import { formatCount, themeFor, toApiError, votesToRating } from '../common/backendAdapter';

const BASE_URL = import.meta.env.VITE_API_BASE_URL;

/**
 * GET /api/games/{universeId} + GET /api/games/{universeId}/videos 를 합쳐
 * FE GameDetailResponse{game, relatedGames, videos} 로 변환.
 * 관련 게임 API는 백엔드에 아직 없어 빈 배열 (섹션은 자동으로 비어 보임).
 */
export async function getGameDetail(gameId: string): Promise<GameDetailResponse> {
  const [detailRes, videosRes] = await Promise.all([
    fetch(`${BASE_URL}/games/${encodeURIComponent(gameId)}`),
    fetch(`${BASE_URL}/games/${encodeURIComponent(gameId)}/videos`),
  ]);

  const detailData = await detailRes.json().catch(() => undefined);
  if (!detailRes.ok) {
    throw toApiError(
      detailRes.status,
      detailData,
      `/api/games/${gameId}`,
      '게임 조회 오류',
      '게임 정보를 불러오지 못했습니다.',
    );
  }
  const d = detailData as BackendGameDetail;

  // 영상은 부가 정보 — 실패해도 상세 페이지는 뜨게 한다
  const videosData = videosRes.ok
    ? ((await videosRes.json().catch(() => undefined)) as { videos: BackendVideo[] } | undefined)
    : undefined;

  const tags = [d.genreL1, d.genreL2]
    .filter((g): g is string => !!g)
    .map((g) => g.toLowerCase());

  return {
    game: {
      id: String(d.universeId),
      universeId: d.universeId,
      placeId: 0,
      name: d.name,
      genre: d.genreL1 ?? '',
      tags,
      playingCount: d.playing ?? 0,
      playingLabel: formatCount(d.playing),
      rating: votesToRating(d.upVotes, d.downVotes),
      releasedYear: 0,
      developerName: '',
      description: d.description ?? '',
      thumbnailTheme: themeFor(d.universeId),
    },
    relatedGames: [],
    videos: (videosData?.videos ?? []).map((v) => ({
      youtubeVideoId: v.youtubeVideoId,
      title: v.title,
      viewsLabel: 'YouTube',
    })),
  };
}
