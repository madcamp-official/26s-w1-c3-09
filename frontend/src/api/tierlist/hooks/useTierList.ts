import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getTierList, putTierList } from '..';
import { defaultQueryRetry } from '../../common/querySetting';
import type { TierEntry, TierListResponse } from '../../../types/tierlist';
import type { ApiError } from '../../../types/common';

export const useTierListQuery = (nickname: string | null) =>
  useQuery<TierListResponse, ApiError>({
    queryKey: ['tierList', nickname],
    queryFn: () => getTierList(nickname as string),
    enabled: !!nickname,
    retry: defaultQueryRetry,
    staleTime: Infinity,
  });

export const useSaveTierListMutation = (nickname: string | null) => {
  const queryClient = useQueryClient();

  return useMutation<TierListResponse, ApiError, TierEntry[]>({
    mutationFn: (entries) => putTierList(nickname as string, entries),
    // 저장 성공 시, 방금 저장한 결과를 tierList 캐시에 즉시 반영한다.
    // 이게 없으면 추천 페이지가 옛날(빈) 캐시를 보고 티어표로 되돌려버린다.
    onSuccess: (data) => {
      queryClient.setQueryData(['tierList', nickname], data);
    },
  });
};
