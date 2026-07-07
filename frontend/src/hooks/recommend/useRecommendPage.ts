import { useEffect, useMemo } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useRecommendationsQuery } from '../../api/recommend/hooks/useRecommendations';
import { usePreciseRecommend } from './usePreciseRecommend';
import { useTierListQuery } from '../../api/tierlist/hooks/useTierList';
import { useNickname } from '../../store/hooks/useFavoritesStore';
import { TIER_ORDER } from '../../constants/tierlist';
import type { Tier } from '../../types/tierlist';
import type { RecommendMode } from '../../types/recommend';

export const useRecommendPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const nickname = useNickname();

  // 모드는 티어표 페이지에서 navigate state로 넘어온다 (없으면 일반모드).
  const mode: RecommendMode =
    (location.state as { mode?: RecommendMode } | null)?.mode === 'precise' ? 'precise' : 'normal';
  const isPrecise = mode === 'precise';

  // 추천은 "서버에 저장된 티어표"를 근거로 돌린다 (스토어가 아니라 DB가 진실).
  const { data: tierData, isFetching: tierLoading } = useTierListQuery(nickname);
  const entries = useMemo(() => tierData?.entries ?? [], [tierData]);

  // 닉네임 없이 직접 진입 → 첫 화면 / 저장된 티어표가 비어있으면 티어표로 되돌린다.
  useEffect(() => {
    if (!nickname) navigate('/', { replace: true });
    else if (!tierLoading && entries.length === 0) navigate('/tierlist', { replace: true });
  }, [nickname, tierLoading, entries.length, navigate]);

  // 두 모드를 동시에 걸어두되 mode로 한쪽만 활성화 (훅은 조건부 호출 불가)
  const normal = useRecommendationsQuery(nickname, entries, !isPrecise);
  const precise = usePreciseRecommend(nickname, isPrecise && entries.length > 0);

  // 모드별 화면 상태 통일
  const view = isPrecise
    ? {
        // finalizing(정리·계산 중)도 로딩 화면 유지 — 결과 대신 "정리 중" 표시
        isFetching:
          precise.phase === 'starting' ||
          precise.phase === 'running' ||
          precise.phase === 'finalizing',
        isError: precise.phase === 'error',
        errorMessage: precise.errorMessage,
        progress: precise.progress,
        popular: precise.popular,
        discovery: precise.discovery,
        finalizingMessage: precise.phase === 'finalizing' ? precise.finalizingMessage : null,
        cancel: precise.cancel,
        canCancel: precise.canCancel,
      }
    : {
        isFetching: tierLoading || normal.isFetching,
        isError: normal.isError,
        errorMessage: null,
        progress: null,
        popular: normal.data?.popular ?? [],
        discovery: normal.data?.discovery ?? [],
        finalizingMessage: null,
        cancel: () => {},
        canCancel: false,
      };

  // 티어별 개수 — 저장된 entries에서 집계
  const tierCounts = useMemo(() => {
    const counts: Record<Tier, number> = { SSS: 0, A: 0, B: 0, C: 0 };
    for (const e of entries) counts[e.tier] += 1;
    return TIER_ORDER.map((tier) => ({ tier, count: counts[tier] }));
  }, [entries]);

  return {
    nickname,
    mode,
    isFetching: view.isFetching,
    isError: view.isError,
    errorMessage: view.errorMessage,
    progress: view.progress,
    finalizingMessage: view.finalizingMessage,
    cancelPrecise: view.cancel,
    canCancelPrecise: view.canCancel,
    popular: view.popular,
    discovery: view.discovery,
    totalCount: view.popular.length + view.discovery.length,
    tierCounts,
    goToDetail: (gameId: string) => navigate(`/games/${gameId}`),
  };
};
