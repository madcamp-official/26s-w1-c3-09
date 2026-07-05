import type { Game } from '../../types/game';

export const games: Game[] = [];

/** game_relations 테이블 역할 — 게임 id -> "함께 즐기는 게임" 6개 id 목록 */
export const gameRelations: Record<string, string[]> = {};

export function resetGames() {
  games.length = 0;
  Object.keys(gameRelations).forEach((key) => delete gameRelations[key]);
}
