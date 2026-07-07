import { useMemo } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useGameDetailQuery } from '../../api/game/hooks/useGameDetail';
import { useRecommendationsQuery } from '../../api/recommend/hooks/useRecommendations';
import { useTierListQuery } from '../../api/tierlist/hooks/useTierList';
import { useNickname } from '../../store/hooks/useFavoritesStore';

export const useGameDetailPage = () => {
  const navigate = useNavigate();
  const { gameId } = useParams<{ gameId: string }>();
  const nickname = useNickname();

  const { data: detail, isFetching, isError } = useGameDetailQuery(gameId);

  // page3와 동일하게 "저장된 티어표"로 추천 쿼리 키를 맞춰, 추천 결과를 캐시에서 재사용한다.
  const { data: tierData } = useTierListQuery(nickname);
  const entries = useMemo(() => tierData?.entries ?? [], [tierData]);
  const { data: recData } = useRecommendationsQuery(nickname, entries);

  // 추천을 거치지 않고 직접 URL로 들어온 게임이면 undefined → 매칭 섹션을 숨긴다.
  // 응답이 popular/discovery 두 섹션으로 개편되어(7/7) 양쪽 모두에서 찾는다.
  const recommendation = [...(recData?.popular ?? []), ...(recData?.discovery ?? [])].find(
    (r) => r.game.id === gameId,
  );

  return {
    isFetching,
    isError,
    detail,
    recommendation,
    goToGame: (id: string) => navigate(`/games/${id}`),
    goBack: () => navigate('/recommend'),
  };
};
