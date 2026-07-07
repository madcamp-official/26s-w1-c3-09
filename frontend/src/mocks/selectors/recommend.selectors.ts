import { getGameById, getRelatedGameIds } from './games.selectors';
import type { TierEntryPayload, Recommendation, RecommendationsResponse } from '../../types/recommend';
import type { Tier } from '../../types/tierlist';

/**
 * 목업 추천 알고리즘 (백엔드 연동 전 임시).
 * 실제 백엔드는 SQL 스키마의 "팬 공통 즐겨찾기(game_cofavorite)" 기반으로 산출한다.
 * 여기서는 관련 게임(People Also Join)을 depth 1/2로 타며 티어 가중치를 합산해
 * matchPercent를 흉내낸다. 근거 칩(sources)은 제거되어 응답엔 담지 않는다.
 */
const TIER_WEIGHT: Record<Tier, number> = { SSS: 1.0, A: 0.7, B: 0.4, C: 0.1 };
const DEPTH_WEIGHT: Record<1 | 2, number> = { 1: 0.6, 2: 0.3 };
const MAX_RESULTS = 9;

type Acc = { total: number; tagScore: Map<string, number> };

export function computeRecommendations(entries: TierEntryPayload[]): RecommendationsResponse {
  const seedTier = new Map<string, Tier>(entries.map((e) => [e.gameId, e.tier]));
  const acc = new Map<string, Acc>();

  const addScore = (gameId: string, seedId: string, depth: 1 | 2) => {
    if (seedTier.has(gameId)) return; // 이미 티어표에 있는 게임은 추천 제외
    const tier = seedTier.get(seedId)!;
    const seed = getGameById(seedId);
    if (!seed || !getGameById(gameId)) return;

    const contribution = TIER_WEIGHT[tier] * DEPTH_WEIGHT[depth];
    const cur = acc.get(gameId) ?? { total: 0, tagScore: new Map<string, number>() };
    cur.total += contribution;
    // 추천 이유(reason) 문구 생성을 위해 시드 게임 태그에 기여도를 누적
    for (const tag of seed.tags) {
      cur.tagScore.set(tag, (cur.tagScore.get(tag) ?? 0) + contribution);
    }
    acc.set(gameId, cur);
  };

  // BFS: 시드 -> depth1(관련 6개) -> depth2(관련의 관련)
  for (const { gameId: seedId } of entries) {
    for (const d1 of getRelatedGameIds(seedId)) {
      addScore(d1, seedId, 1);
      for (const d2 of getRelatedGameIds(d1)) addScore(d2, seedId, 2);
    }
  }

  const maxScore = Math.max(...[...acc.values()].map((v) => v.total), 0.0001);

  const ranked = [...acc.entries()]
    .map(([gameId, v]) => {
      const topTags = [...v.tagScore.entries()]
        .sort((a, b) => b[1] - a[1])
        .slice(0, 2)
        .map(([t]) => t);

      return {
        game: getGameById(gameId)!,
        total: v.total,
        matchPercent: Math.min(99, Math.round(60 + (v.total / maxScore) * 39)),
        reason: `${topTags.join(', ')} 장르를 좋아하는 분께 추천`,
      };
    })
    .sort((a, b) => b.total - a.total)
    .slice(0, MAX_RESULTS)
    .map(({ total, ...rec }, i): Recommendation => ({ ...rec, rank: i + 1 }));

  // 실서버는 popular/discovery를 알고리즘으로 나누지만, 목업은 상위 절반=popular, 하위 절반=discovery로 흉내
  const half = Math.ceil(ranked.length / 2);
  return {
    popular: ranked.slice(0, half).map((r, i) => ({ ...r, rank: i + 1 })),
    discovery: ranked.slice(half).map((r, i) => ({ ...r, rank: i + 1 })),
  };
}
