import { useEffect, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useRecommendationsQuery } from '../../api/recommend/hooks/useRecommendations';
import { useTierListQuery } from '../../api/tierlist/hooks/useTierList';
import { useNickname } from '../../store/hooks/useFavoritesStore';
import { TIER_ORDER } from '../../constants/tierlist';
import type { Tier } from '../../types/tierlist';

export const useRecommendPage = () => {
  const navigate = useNavigate();
  const nickname = useNickname();

  // 추천은 "서버에 저장된 티어표"를 근거로 돌린다 (스토어가 아니라 DB가 진실).
  // 백엔드 연동 시 서버가 저장된 tier_entries를 읽어 계산하는 흐름과 동일하다.
  const { data: tierData, isFetching: tierLoading } = useTierListQuery(nickname);
  const entries = useMemo(() => tierData?.entries ?? [], [tierData]);

  // 닉네임 없이 직접 진입 → 첫 화면 / 저장된 티어표가 비어있으면 티어표로 되돌린다.
  useEffect(() => {
    if (!nickname) navigate('/', { replace: true });
    else if (!tierLoading && entries.length === 0) navigate('/tierlist', { replace: true });
  }, [nickname, tierLoading, entries.length, navigate]);

  const { data, isFetching, isError } = useRecommendationsQuery(nickname, entries);

  // 티어별 개수 — 저장된 entries에서 집계
  const tierCounts = useMemo(() => {
    const counts: Record<Tier, number> = { SSS: 0, A: 0, B: 0, C: 0 };
    for (const e of entries) counts[e.tier] += 1;
    return TIER_ORDER.map((tier) => ({ tier, count: counts[tier] }));
  }, [entries]);

  return {
    nickname,
    isFetching: tierLoading || isFetching,
    isError,
    recommendations: data?.recommendations ?? [],
    tierCounts,
    goToDetail: (gameId: string) => navigate(`/games/${gameId}`),
  };
};
