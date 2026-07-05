import type { StateCreator } from 'zustand';

export type FavoritesSlice = {
  nickname: string | null;
  actions: {
    setNickname: (nickname: string) => void;
    reset: () => void;
  };
};

export const createFavoritesSlice: StateCreator<FavoritesSlice> = (set) => ({
  nickname: null,
  actions: {
    setNickname: (nickname) => set({ nickname }),
    reset: () => set({ nickname: null }),
  },
});
