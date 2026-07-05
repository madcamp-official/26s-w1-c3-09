import { create } from 'zustand';
import { createTierlistSlice } from './slices/tierlistSlice';
import type { TierlistSlice } from './slices/tierlistSlice';

export const useTierlistStore = create<TierlistSlice>(createTierlistSlice);
