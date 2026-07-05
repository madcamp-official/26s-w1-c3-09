import { create } from 'zustand';
import { createFavoritesSlice } from './slices/favoritesSlice';
import type { FavoritesSlice } from './slices/favoritesSlice';

export const useFavoritesStore = create<FavoritesSlice>(createFavoritesSlice);
