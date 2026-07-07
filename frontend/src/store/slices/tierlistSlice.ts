import type { StateCreator } from 'zustand';
import type { Tier, TierBoard, TierEntry } from '../../types/tierlist';
import type { Game } from '../../types/game';
import { TIER_ORDER } from '../../constants/tierlist';

const EMPTY_BOARD: TierBoard = { SSS: [], A: [], B: [], C: [] };

function removeFromAllTiers(board: TierBoard, gameId: string): TierBoard {
  return {
    SSS: board.SSS.filter((id) => id !== gameId),
    A: board.A.filter((id) => id !== gameId),
    B: board.B.filter((id) => id !== gameId),
    C: board.C.filter((id) => id !== gameId),
  };
}

/** 보드(Record<Tier, string[]>) → 서버 저장 형태([{gameId, tier}]) */
export function boardToEntries(board: TierBoard): TierEntry[] {
  return TIER_ORDER.flatMap((tier) => board[tier].map((gameId) => ({ gameId, tier })));
}

/** 서버에서 불러온 entries → 보드 */
export function entriesToBoard(entries: TierEntry[]): TierBoard {
  const board: TierBoard = { SSS: [], A: [], B: [], C: [] };
  for (const { gameId, tier } of entries) {
    if (TIER_ORDER.includes(tier)) board[tier].push(gameId);
  }
  return board;
}

export type TierlistSlice = {
  board: TierBoard;
  searchedGames: Game[];
  actions: {
    assign: (gameId: string, tier: Tier) => void;
    unassign: (gameId: string) => void;
    addSearchedGame: (game: Game) => void;
    hydrate: (entries: TierEntry[]) => void;
    reset: () => void;
  };
};

export const createTierlistSlice: StateCreator<TierlistSlice> = (set, get) => ({
  board: EMPTY_BOARD,
  searchedGames: [],
  actions: {
    assign: (gameId, tier) => {
      const cleared = removeFromAllTiers(get().board, gameId);
      set({ board: { ...cleared, [tier]: [...cleared[tier], gameId] } });
    },
    unassign: (gameId) => {
      set({ board: removeFromAllTiers(get().board, gameId) });
    },
    // 검색으로 찾은 게임을 배치 풀에 추가 (id 중복이면 무시)
    addSearchedGame: (game) => {
      if (get().searchedGames.some((g) => g.id === game.id)) return;
      set({ searchedGames: [...get().searchedGames, game] });
    },
    hydrate: (entries) => set({ board: entriesToBoard(entries) }),
    reset: () => set({ board: EMPTY_BOARD, searchedGames: [] }),
  },
});
