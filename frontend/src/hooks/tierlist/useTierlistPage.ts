import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { DragEndEvent } from '@dnd-kit/core';
import { useUserFavoritesQuery } from '../../api/favorites/hooks/useFavorites';
import { useTierListQuery, useSaveTierListMutation } from '../../api/tierlist/hooks/useTierList';
import { useNickname } from '../../store/hooks/useFavoritesStore';
import { useTierBoard, useTierlistActions } from '../../store/hooks/useTierlistStore';
import { boardToEntries } from '../../store/slices/tierlistSlice';
import toast from 'react-hot-toast';
import { TIER_ORDER, SSS_MAX_COUNT } from '../../constants/tierlist';
import type { Tier } from '../../types/tierlist';

export const useTierlistPage = () => {
  const navigate = useNavigate();
  const nickname = useNickname();
  const board = useTierBoard();
  const { assign, unassign, hydrate, reset } = useTierlistActions();

  const { data: favData, isFetching: favLoading } = useUserFavoritesQuery(nickname);
  const { data: tierData, isFetching: tierLoading } = useTierListQuery(nickname);
  const { mutateAsync: saveTierList, isPending: isSaving } = useSaveTierListMutation(nickname);

  // 닉네임 없이 직접 진입 → 첫 화면으로
  useEffect(() => {
    if (!nickname) navigate('/', { replace: true });
  }, [nickname, navigate]);

  // 서버에 저장돼 있던 티어표를 최초 1회 복원 (재방문 시 이전 배치 유지)
  const hydratedRef = useRef(false);
  useEffect(() => {
    if (!hydratedRef.current && tierData) {
      hydrate(tierData.entries);
      hydratedRef.current = true;
    }
  }, [tierData, hydrate]);

  const favorites = useMemo(() => favData?.favorites ?? [], [favData]);
  const findGame = (gameId: string) => favorites.find((g) => g.id === gameId) ?? null;

  const assignedIds = useMemo(() => TIER_ORDER.flatMap((tier) => board[tier]), [board]);
  const unassigned = useMemo(
    () => favorites.filter((game) => !assignedIds.includes(game.id)),
    [favorites, assignedIds],
  );

  const handleDragEnd = (event: DragEndEvent) => {
    const gameId = event.active.id as string;
    const tier = event.over?.id as Tier | undefined;
    if (!tier || !TIER_ORDER.includes(tier)) return;
    // DB 정책(scoring.json sssMaxCount): SSS는 최대 2개 — 초과 배치는 서버가 400으로 거부한다
    if (tier === 'SSS' && !board.SSS.includes(gameId) && board.SSS.length >= SSS_MAX_COUNT) {
      toast.error(`SSS 티어는 최대 ${SSS_MAX_COUNT}개까지만 배치할 수 있어요.`);
      return;
    }
    assign(gameId, tier);
  };

  // 추천받기: 현재 티어표를 서버에 저장(PUT)한 뒤, 저장이 끝나면 추천 페이지로 이동.
  // 저장 → 이동 → 추천(POST) 순서를 보장해, 백엔드가 "저장된 티어표"를 근거로 계산하게 한다.
  const [saveError, setSaveError] = useState<string | null>(null);
  const goToRecommend = async () => {
    setSaveError(null);
    try {
      await saveTierList(boardToEntries(board));
      navigate('/recommend');
    } catch {
      setSaveError('티어표 저장에 실패했어요. 잠시 후 다시 시도해주세요.');
    }
  };

  return {
    isLoading: favLoading || tierLoading,
    // 조회는 됐지만 즐겨찾기가 0개 → 안내 문구 표시 (Q2 정책)
    isEmptyFavorites: !favLoading && !!favData && favorites.length === 0,
    favorites,
    findGame,
    unassigned,
    board,
    assignedCount: assignedIds.length,
    totalCount: favorites.length,
    progress: favorites.length > 0 ? assignedIds.length / favorites.length : 0,
    handleDragEnd,
    unassign,
    reset,
    goToRecommend,
    isSaving,
    saveError,
  };
};
