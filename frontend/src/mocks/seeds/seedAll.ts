import { seedGames } from './games.seed';
import { seedFavorites } from './favorites.seed';
import { resetTierLists } from '../db/tierlists.db';

export function seedAll() {
  seedGames();
  seedFavorites();
  resetTierLists();
}
