import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { DragEndEvent } from '@dnd-kit/core';
import { useUserFavoritesQuery, useRefreshFavoritesMutation } from '../../api/favorites/hooks/useFavorites';
import { useTierListQuery, useSaveTierListMutation } from '../../api/tierlist/hooks/useTierList';
import { useNickname } from '../../store/hooks/useFavoritesStore';
import { useTierBoard, useTierlistActions, useSearchedGames } from '../../store/hooks/useTierlistStore';
import { boardToEntries } from '../../store/slices/tierlistSlice';
import toast from 'react-hot-toast';
import { TIER_ORDER, SSS_MAX_COUNT } from '../../constants/tierlist';
import type { Tier } from '../../types/tierlist';
import type { ApiError } from '../../types/common';
import type { RecommendMode } from '../../types/recommend';
import type { Game } from '../../types/game';

export const useTierlistPage = () => {
  const navigate = useNavigate();
  const nickname = useNickname();
  const board = useTierBoard();
  const { assign, unassign, addSearchedGame, hydrate, reset } = useTierlistActions();

  const { data: favData, isFetching: favLoading } = useUserFavoritesQuery(nickname);
  const { data: tierData, isFetching: tierLoading } = useTierListQuery(nickname);
  const { mutateAsync: saveTierList, isPending: isSaving } = useSaveTierListMutation(nickname);
  const { mutateAsync: refreshFavorites, isPending: isRefreshing } =
    useRefreshFavoritesMutation(nickname);
  const [favRefreshed, setFavRefreshed] = useState(false);

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
  const searchedGames = useSearchedGames();
  // 저장된 티어 게임(이름·아이콘 포함) — 검색게임이 메모리라 새로고침 시 사라지는 걸 보완(재방문 렌더·카운트)
  const tierGames = useMemo(() => tierData?.games ?? [], [tierData]);

  // 배치 가능한 게임 풀 = 즐겨찾기 + 저장된 티어 게임 + 검색으로 추가한 게임 (id 중복 제거)
  const pool = useMemo(() => {
    const byId = new Map<string, Game>();
    for (const g of favorites) byId.set(g.id, g);
    for (const g of tierGames) if (!byId.has(g.id)) byId.set(g.id, g);
    for (const g of searchedGames) if (!byId.has(g.id)) byId.set(g.id, g);
    return [...byId.values()];
  }, [favorites, tierGames, searchedGames]);

  const findGame = (gameId: string) => pool.find((g) => g.id === gameId) ?? null;

  const assignedIds = useMemo(() => TIER_ORDER.flatMap((tier) => board[tier]), [board]);
  const unassigned = useMemo(
    () => pool.filter((game) => !assignedIds.includes(game.id)),
    [pool, assignedIds],
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
  // 추천 모드 토글 — normal(즉시) / precise(즉석 수집 후 정밀). 이동 시 state로 넘긴다.
  const [mode, setMode] = useState<RecommendMode>('normal');
  const goToRecommend = async () => {
    setSaveError(null);
    try {
      await saveTierList(boardToEntries(board));
      navigate('/recommend', { state: { mode } });
    } catch (err) {
      // 백엔드 ApiError의 detail(예: SSS 개수 초과 사유)을 그대로 노출 — 원인 없는 실패 메시지는 디버깅을 막는다
      const detail = (err as ApiError | undefined)?.detail;
      setSaveError(detail ?? '티어표 저장에 실패했어요. 잠시 후 다시 시도해주세요.');
    }
  };

  // 즐겨찾기 새로고침 — 로블록스 재조회(무거움). 접속당 1회만, 성공하면 버튼 비활성화.
  const refreshFavoritesOnce = async () => {
    try {
      await refreshFavorites();
      setFavRefreshed(true);
    } catch (err) {
      const e = err as ApiError | undefined;
      toast.error(
        e?.status === 429
          ? (e?.detail ?? '지금은 요청이 많아요. 잠시 후 다시 시도해주세요.')
          : (e?.detail ?? '즐겨찾기를 새로고침하지 못했어요.'),
      );
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
    totalCount: pool.length,
    progress: pool.length > 0 ? assignedIds.length / pool.length : 0,
    handleDragEnd,
    unassign,
    reset,
    goToRecommend,
    mode,
    setMode,
    refreshFavoritesOnce,
    addSearchedGame,
    isRefreshing,
    favRefreshed,
    isSaving,
    saveError,
  };
};