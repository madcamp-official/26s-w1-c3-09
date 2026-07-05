import type { TierListResponse, TierEntry } from '../../types/tierlist';
import {
  fetchUserFavoritesRaw,
  resolveUserId,
  savedTierToEntries,
  toApiError,
} from '../common/backendAdapter';

const BASE_URL = import.meta.env.VITE_API_BASE_URL;

/**
 * 백엔드에는 티어표 단독 GET이 없고, GET /api/users/{username}/favorites 응답의
 * savedTier 로 함께 내려온다 (닉네임당 1세트).
 */
export async function getTierList(nickname: string): Promise<TierListResponse> {
  const raw = await fetchUserFavoritesRaw(nickname);
  return { entries: savedTierToEntries(raw.savedTier) };
}

/**
 * PUT /api/tiers — {userId, entries:[{universeId, tier, position}]} 전체 덮어쓰기.
 * position은 티어 내 왼쪽부터 1 (DB tier_entries.position 규약).
 */
export async function putTierList(
  nickname: string,
  entries: TierEntry[],
): Promise<TierListResponse> {
  const userId = await resolveUserId(nickname);

  const positionInTier = new Map<string, number>();
  const body = {
    userId,
    entries: entries.map((e) => {
      const next = (positionInTier.get(e.tier) ?? 0) + 1;
      positionInTier.set(e.tier, next);
      return { universeId: Number(e.gameId), tier: e.tier, position: next };
    }),
  };

  const res = await fetch(`${BASE_URL}/tiers`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  const data = await res.json().catch(() => undefined);
  if (!res.ok) {
    throw toApiError(res.status, data, '/api/tiers', '티어표 오류', '티어표를 저장하지 못했습니다.');
  }
  // 백엔드는 {ok, saved}만 반환 — 방금 저장한 entries를 그대로 돌려줘 캐시에 반영한다
  return { entries };
}
