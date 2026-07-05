import { favoritesHandlers } from './handlers/favorites.handlers';
import { tierlistHandlers } from './handlers/tierlist.handlers';
import { recommendHandlers } from './handlers/recommend.handlers';
import { gamesHandlers } from './handlers/games.handlers';

export const handlers = [
  ...favoritesHandlers,
  ...tierlistHandlers,
  ...recommendHandlers,
  ...gamesHandlers,
];
