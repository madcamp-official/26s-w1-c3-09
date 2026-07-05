import type { TierEntry } from '../../types/tierlist';

/** key = 소문자 정규화 닉네임, value = ACTIVE 티어표의 entries */
export const tierListsByNickname: Record<string, TierEntry[]> = {};

export function resetTierLists() {
  Object.keys(tierListsByNickname).forEach((key) => delete tierListsByNickname[key]);
}
