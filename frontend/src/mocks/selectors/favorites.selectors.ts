import { favoritesByNickname } from '../db/favorites.db';
import { getGamesByIds } from './games.selectors';
import type { FavoritesSearchResult } from '../../types/favorites';

type FavoritesSelectorResult =
  | { ok: true; data: FavoritesSearchResult }
  | { ok: false; error: 'NOT_FOUND' };

export function getFavoritesByNickname(nicknameRaw: string): FavoritesSelectorResult {
  const key = nicknameRaw.trim().toLowerCase();
  const record = favoritesByNickname[key];

  if (!record) return { ok: false, error: 'NOT_FOUND' };

  return {
    ok: true,
    data: {
      nickname: record.nickname,
      displayName: record.displayName,
      favorites: getGamesByIds(record.gameIds),
    },
  };
}
