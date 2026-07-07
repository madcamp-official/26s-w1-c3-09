import type { TierEntryPayload, RecommendationsResponse } from '../../types/recommend';
import type { BackendRecommendItem } from '../common/backendAdapter';
import { formatCount, resolveUserId, themeFor, toApiError } from '../common/backendAdapter';

const BASE_URL = import.meta.env.VITE_API_BASE_URL;

/**
 * POST /api/recommend — 백엔드는 "서버에 저장된 티어표"를 근거로 계산한다 ({userId}만 전송).
 * entries 인자는 쿼리 캐시 키 용도로만 쓰인다 (티어표 페이지가 이동 전에 PUT으로 저장을 보장).
 * 응답 Item{rank, universeId, name, genreL1, score, playerCount, iconUrl} → FE Recommendation 변환.
 */

 /** 백엔드 추천 응답 — sections 두 갈래 (7/7 개편, dto RecommendResponse와 1:1) */
 type BackendRecommendResponse = {
   sections: {
     popular: BackendRecommendItem[];
     discovery: BackendRecommendItem[];
   };
 };

/** 백엔드 Item → FE Recommendation 1개 변환. maxScore는 매칭 % 환산 기준(섹션별 최고점) */
function toRecommendation(item: BackendRecommendItem, maxScore: number): Recommendation {
  return {
    rank: item.rank,
    // score → 매칭 % 환산: 섹션 최고점을 99%로 보고 60~99 구간에 선형 배치
    matchPercent: maxScore > 0 ? Math.min(99, Math.round(60 + (item.score / maxScore) * 39)) : 60,
    reason: item.genreL1
      ? `${item.genreL1} 장르를 좋아하는 분께 추천`
      : '회원님의 티어표와 취향이 비슷한 유저들이 즐겨찾기한 게임이에요',
    game: {
      id: String(item.universeId),
      universeId: item.universeId,
      placeId: 0,
      name: item.name,
      genre: item.genreL1 ?? '',
      tags: item.genreL1 ? [item.genreL1.toLowerCase()] : [],
      playingCount: item.playerCount ?? 0,
      playingLabel: formatCount(item.playerCount),
      rating: 0,
      releasedYear: 0,
      developerName: '',
      description: '',
      thumbnailTheme: themeFor(item.universeId),
    },
  };
}

/** 한 섹션(Item 배열)을 통째로 변환 — 매칭 %는 그 섹션 안에서의 상대값 */
function toSection(items: BackendRecommendItem[]): Recommendation[] {
  const maxScore = Math.max(...items.map((i) => i.score), 0);
  return items.map((item) => toRecommendation(item, maxScore));
}

export async function postRecommendations(
  nickname: string,
  _entries: TierEntryPayload[],
): Promise<RecommendationsResponse> {
  const userId = await resolveUserId(nickname);

  const res = await fetch(`${BASE_URL}/recommend`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ userId }),
  });
  const data = await res.json().catch(() => undefined);
  if (!res.ok) {
    throw toApiError(res.status, data, '/api/recommend', '추천 오류', '추천 결과를 불러오지 못했습니다.');
  }

  const sections = (data as BackendRecommendResponse).sections ??
{ popular: [], discovery: [] };
  return {
      popular: toSection(sections.popular ?? []),
      discovery: toSection(sections.discovery ?? []),
  };
}
