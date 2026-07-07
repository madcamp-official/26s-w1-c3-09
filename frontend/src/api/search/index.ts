import type { Game } from '../../types/game';
import { formatCount, themeFor, toApiError } from '../common/backendAdapter';

const BASE_URL = import.meta.env.VITE_API_BASE_URL;

/** GET /api/search 결과 1건 (dto SearchResponse.Result) */
type BackendSearchResult = {
  universeId: number;
  name: string;
  playerCount: number | null;
  iconUrl: string | null;
};

/** 백엔드 검색 결과 → FE Game (검색 목록·티어 배치 재료) */
function searchResultToGame(r: BackendSearchResult): Game {
  return {
    id: String(r.universeId),
    universeId: r.universeId,
    placeId: 0,
    name: r.name,
    genre: '',
    tags: [],
    playingCount: r.playerCount ?? 0,
    playingLabel: formatCount(r.playerCount),
    rating: 0,
    releasedYear: 0,
    developerName: '',
    description: '',
    iconUrl: r.iconUrl,
    thumbnailTheme: themeFor(r.universeId),
  };
}

/** GET /api/search?q= — 게임 이름 검색 (상위 10). 빈 검색어는 호출 없이 빈 배열. */
export async function searchGames(query: string): Promise<Game[]> {
  const q = query.trim();
  if (!q) return [];

  const res = await fetch(`${BASE_URL}/search?q=${encodeURIComponent(q)}`);
  const data = await res.json().catch(() => undefined);
  if (!res.ok) {
    throw toApiError(res.status, data, '/api/search', '검색 오류', '검색에 실패했습니다.');
  }
  return ((data as { results: BackendSearchResult[] }).results ?? []).map(searchResultToGame);
}