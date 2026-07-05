import { useQuery } from '@tanstack/react-query';
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
