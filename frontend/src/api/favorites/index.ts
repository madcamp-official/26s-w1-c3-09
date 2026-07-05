import type { FavoritesSearchResult } from '../../types/favorites';
import { fetchUserFavoritesRaw, favoriteToGame } from '../common/backendAdapter';

/**
 * GET /api/users/{username}/favorites (백엔드 실제 계약)
 * → FE FavoritesSearchResult 로 변환. userId는 어댑터가 세션 캐시에 저장한다.
 */
export async function getUserFavorites(nickname: string): Promise<FavoritesSearchResult> {
  const raw = await fetchUserFavoritesRaw(nickname, true);
  return {
    nickname: raw.username,
    displayName: raw.username,
    favorites: raw.favorites.map(favoriteToGame),
  };
}
