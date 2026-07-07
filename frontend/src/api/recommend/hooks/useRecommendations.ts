import { useQuery } from '@tanstack/react-query';
import { postRecommendations } from '..';
import { defaultQueryRetry } from '../../common/querySetting';
import type { TierEntryPayload, RecommendationsResponse } from '../../../types/recommend';
import type { ApiError } from '../../../types/common';

/**
 * POST지만 "조회" 성격이므로 useQuery를 쓴다.
 * 닉네임 + 티어 배치(entries)를 캐시 키에 직렬화해 넣어서,
 * 같은 배치로 다시 방문하면 재요청 없이 캐시를 반환하고
 * 배치가 바뀌면 키가 달라져 자동으로 새로 계산한다.
 */
export const useRecommendationsQuery = (
  nickname: string | null,
  entries: TierEntryPayload[],
  enabled = true,
) =>
  useQuery<RecommendationsResponse, ApiError>({
    queryKey: ['recommendations', nickname, JSON.stringify(entries)],
    queryFn: () => postRecommendations(nickname as string, entries),
    enabled: enabled && !!nickname && entries.length > 0,
    retry: defaultQueryRetry,
    staleTime: Infinity,
  });
