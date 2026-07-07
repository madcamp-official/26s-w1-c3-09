import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getUserFavorites } from '..';
import { defaultQueryRetry } from '../../common/querySetting';
import type { FavoritesSearchResult } from '../../../types/favorites';
import type { ApiError } from '../../../types/common';

export const useUserFavoritesQuery = (nickname: string | null) =>
  useQuery<FavoritesSearchResult, ApiError>({
    queryKey: ['favorites', nickname],
    queryFn: () => getUserFavorites(nickname as string),
    enabled: !!nickname,
    retry: defaultQueryRetry,
    staleTime: 1000 * 60 * 5,
  });

/**
 * 즐겨찾기 새로고침 — refresh=true로 로블록스 재조회 후 목록 캐시를 갱신한다.
 * (버튼이 호출. 접속당 1회 제한·비활성화는 화면 쪽에서 관리)
 */
export const useRefreshFavoritesMutation = (nickname: string | null) => {
  const queryClient = useQueryClient();

  return useMutation<FavoritesSearchResult, ApiError>({
    mutationFn: () => getUserFavorites(nickname as string, true),
    onSuccess: (data) => {
      queryClient.setQueryData(['favorites', nickname], data);
    },
  });
};