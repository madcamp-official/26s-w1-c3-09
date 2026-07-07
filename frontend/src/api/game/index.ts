import type { GameDetailResponse, Game } from '../../types/game';
import type { BackendGameDetail, BackendVideo } from '../common/backendAdapter';
import { formatCount, themeFor, toApiError, votesToRating } from '../common/backendAdapter';

const BASE_URL = import.meta.env.VITE_API_BASE_URL;

/** GET /api/games/{id}/similar 응답 1건 (dto SimilarGamesResponse.Item) */
type BackendSimilar = {
  universeId: number;
  name: string;
  genreL1: string | null;
  playerCount: number | null;
  iconUrl: string | null;
};

/** 백엔드 similar 항목 → FE Game (관련 게임 그리드 재료) */
function similarToGame(s: BackendSimilar): Game {
  return {
    id: String(s.universeId),
    universeId: s.universeId,
    placeId: 0,
    name: s.name,
    genre: s.genreL1 ?? '',
    tags: s.genreL1 ? [s.genreL1.toLowerCase()] : [],
    playingCount: s.playerCount ?? 0,
    playingLabel: formatCount(s.playerCount),
    rating: 0,
    releasedYear: 0,
    developerName: '',
    description: '',
    iconUrl: s.iconUrl,
    thumbnailTheme: themeFor(s.universeId),
  };
}

/**
 * GET /api/games/{universeId} (+ /videos, /similar) 를 합쳐
 * FE GameDetailResponse{game, screenshots, relatedGames, videos} 로 변환.
 * screenshots는 상세의 실제 URL 배열, relatedGames는 /similar(함께 즐겨찾기된 게임 최대 6).
 * 영상·유사게임은 부가 정보 — 실패해도 상세 페이지는 뜨게 한다.
 */
export async function getGameDetail(gameId: string): Promise<GameDetailResponse> {
  const [detailRes, videosRes, similarRes] = await Promise.all([
    fetch(`${BASE_URL}/games/${encodeURIComponent(gameId)}`),
    fetch(`${BASE_URL}/games/${encodeURIComponent(gameId)}/videos`),
    fetch(`${BASE_URL}/games/${encodeURIComponent(gameId)}/similar`),
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

  const videosData = videosRes.ok
    ? ((await videosRes.json().catch(() => undefined)) as { videos: BackendVideo[] } | undefined)
    : undefined;

  const similarData = similarRes.ok
    ? ((await similarRes.json().catch(() => undefined)) as { similar: BackendSimilar[] } | undefined)
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
      releasedYear: d.releasedYear ?? 0,
      developerName: '',
      description: d.description ?? '',
      iconUrl: d.iconUrl,
      thumbnailTheme: themeFor(d.universeId),
    },
    screenshots: d.screenshots ?? [],
    relatedGames: (similarData?.similar ?? []).map(similarToGame),
    videos: (videosData?.videos ?? []).map((v) => ({
      youtubeVideoId: v.youtubeVideoId,
      title: v.title,
      viewsLabel: 'YouTube',
    })),
  };
}
