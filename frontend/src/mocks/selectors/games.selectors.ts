import { games, gameRelations } from '../db/games.db';
import type { Game } from '../../types/game';

export function getGameById(id: string): Game | undefined {
  return games.find((g) => g.id === id);
}

export function getGamesByIds(ids: string[]): Game[] {
  return ids.map((id) => getGameById(id)).filter((g): g is Game => Boolean(g));
}

export function getRelatedGameIds(id: string): string[] {
  return gameRelations[id] ?? [];
}
