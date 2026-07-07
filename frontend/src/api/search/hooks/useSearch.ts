import { useMutation } from '@tanstack/react-query';
import { searchGames } from '..';
import type { Game } from '../../../types/game';
import type { ApiError } from '../../../types/common';

/**
 * 게임 검색 훅 — 엔터/버튼으로만 실행(타이핑마다 호출 금지)이라 useMutation.
 * mutate(검색어)로 실행, data에 결과 Game[]. 429(BUSY) 등 에러는 error로.
 */
export const useSearchGamesMutation = () =>
  useMutation<Game[], ApiError, string>({
    mutationFn: (query) => searchGames(query),
  });