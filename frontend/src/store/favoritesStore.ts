import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { createFavoritesSlice } from './slices/favoritesSlice';
import type { FavoritesSlice } from './slices/favoritesSlice';

/**
 * 닉네임을 localStorage에 저장 → 새로고침해도 유지 (첫 화면으로 튕기지 않음).
 * actions(함수)는 저장 대상에서 제외하고 nickname만 보관한다.
 */
export const useFavoritesStore = create<FavoritesSlice>()(
  persist(createFavoritesSlice, {
    name: 'favorites', // localStorage 키
    partialize: (state) => ({ nickname: state.nickname }),
  }),
);
