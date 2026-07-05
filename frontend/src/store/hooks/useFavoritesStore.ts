import { useFavoritesStore } from '../favoritesStore';

export const useNickname = () => useFavoritesStore((s) => s.nickname);
export const useFavoritesActions = () => useFavoritesStore((s) => s.actions);
