import { useTierlistStore } from '../tierlistStore';

export const useTierBoard = () => useTierlistStore((s) => s.board);
export const useTierlistActions = () => useTierlistStore((s) => s.actions);
export const useSearchedGames = () => useTierlistStore((s) => s.searchedGames);