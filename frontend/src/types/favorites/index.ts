import type { Game } from '../game';

export type FavoritesSearchResult = {
  nickname: string;
  displayName: string;
  favorites: Game[];
};
