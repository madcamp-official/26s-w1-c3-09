import { useQuery } from '@tanstack/react-query';
import { getGameDetail } from '..';
import { defaultQueryRetry } from '../../common/querySetting';
import type { GameDetailResponse } from '../../../types/game';
import type { ApiError } from '../../../types/common';

export const useGameDetailQuery = (gameId: string | undefined) =>
  useQuery<GameDetailResponse, ApiError>({
    queryKey: ['gameDetail', gameId],
    queryFn: () => getGameDetail(gameId as string),
    enabled: !!gameId,
    retry: defaultQueryRetry,
    staleTime: 1000 * 60 * 5,
  });
