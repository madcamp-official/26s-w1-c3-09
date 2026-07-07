import { useState } from 'react';
import {
  DndContext,
  DragOverlay,
  PointerSensor,
  useSensor,
  useSensors,
} from '@dnd-kit/core';
import type { DragEndEvent, DragStartEvent } from '@dnd-kit/core';
import { Loader2, RotateCcw, rotateCw Sparkles } from 'lucide-react';
import { useTierlistPage } from '../../hooks/tierlist/useTierlistPage';
import { MIN_ENTRIES_FOR_RECOMMEND, TIER_META, TIER_ORDER } from '../../constants/tierlist';
import { PRIMARY_BTN } from '../../constants/common';
import TierDropZone from '../../component/tierlist/TierDropZone';
import UnassignedGameList from '../../component/tierlist/UnassignedGameList';
import GameCard from '../../component/tierlist/GameCard';
import type { Game } from '../../types/game';

export default function TierlistPage() {
  const {
    isLoading,
    isEmptyFavorites,
    findGame,
    unassigned,
    board,
    assignedCount,
    totalCount,
    progress,
    handleDragEnd,
    unassign,
    reset,
    goToRecommend,
    mode,
    setMode,
    isSaving,
    saveError,
    refreshFavoritesOnce,
    isRefreshing,
    favRefreshed,
  } = useTierlistPage();

  const [activeGame, setActiveGame] = useState<Game | null>(null);
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 6 } }));
  const canRecommend = assignedCount >= MIN_ENTRIES_FOR_RECOMMEND;

  const onDragStart = (event: DragStartEvent) => {
    setActiveGame(findGame(event.active.id as string));
  };

  const onDragEnd = (event: DragEndEvent) => {
    setActiveGame(null);
    handleDragEnd(event);
  };

  if (isLoading) {
    return (
      <div className="flex flex-1 items-center justify-center py-24">
        <Loader2 className="h-8 w-8 animate-spin text-accent" aria-hidden="true" />
      </div>
    );
  }

  return (
    <DndContext sensors={sensors} onDragStart={onDragStart} onDragEnd={onDragEnd}>
      <div className="mx-auto w-full max-w-7xl px-6 py-8">
        <div className="mb-7 flex flex-wrap items-end justify-between gap-4">
          <div>
            <h1 className="text-[30px] font-extrabold text-text">나의 게임 티어표</h1>
            <p className="mt-1.5 text-[14px] text-text-sub">게임을 드래그해서 티어를 정하세요</p>
          </div>

          <div className="flex items-center gap-3">
            <div className="flex items-center gap-3 rounded-xl border border-border bg-panel px-4 py-2.5">
              <span className="text-[13px] text-text-sub">진행도</span>
              <div className="h-1.5 w-36 overflow-hidden rounded-full bg-surface">
                <div
                  className="h-full rounded-full bg-accent transition-all"
                  style={{ width: `${progress * 100}%` }}
                />
              </div>
              <span className="text-[13px] font-semibold text-text">
                {assignedCount}/{totalCount}
              </span>
            </div>

            <button
              onClick={reset}
              className="rounded-xl border border-border p-2.5 text-text-sub"
              aria-label="티어표 초기화"
            >
              <RotateCcw className="h-4 w-4" aria-hidden="true" />
            </button>

            {/* 추천 모드 토글 — 일반(즉시) / 정밀(즉석 수집 후 계산) */}
            <div className="flex items-center gap-1 rounded-xl border border-border bg-panel p-1">
              {(['normal', 'precise'] as const).map((m) => (
                <button
                  key={m}
                  onClick={() => setMode(m)}
                  className={`rounded-lg px-3 py-1.5 text-[13px] font-semibold transition-colors ${
                    mode === m ? 'bg-accent text-black' : 'text-text-sub hover:text-text'
                  }`}
                  title={
                    m === 'precise'
                      ? '수집 안 된 게임을 즉석에서 모아 정밀 계산 (오래 걸림)'
                      : 'DB에 있는 데이터로 즉시 계산'
                  }
                >
                  {m === 'normal' ? '일반' : '정밀'}
                </button>
              ))}
            </div>

            <button
              onClick={goToRecommend}
              disabled={!canRecommend || isSaving}
              className={`flex items-center gap-2 ${PRIMARY_BTN}`}
              title={
                canRecommend ? undefined : `${MIN_ENTRIES_FOR_RECOMMEND}개 이상 배치하면 추천을 받을 수 있어요`
              }
            >
              {isSaving ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> 저장 중...
                </>
              ) : (
                <>
                  <Sparkles className="h-4 w-4" aria-hidden="true" /> 게임 추천받기
                </>
              )}
            </button>
          </div>
        </div>

        {saveError && <p className="mb-4 text-right text-[13px] text-red-400">{saveError}</p>}

        <div className="flex flex-col gap-6 lg:flex-row">
          <aside className="w-full shrink-0 border-border pb-6 lg:w-80 lg:border-r lg:pr-6 lg:pb-0">
                        <div className="mb-4 flex items-center justify-between">
                          <span className="flex gap-2 text-[14px] font-semibold text-text">
                            <span>즐겨찾기</span>
                            <span>게임</span>
                          </span>
                          <div className="flex items-center gap-2">
                            <span className="text-[13px] text-text-muted">{unassigned.length}개</span>
                            <button
                              onClick={refreshFavoritesOnce}
                              disabled={isRefreshing || favRefreshed}
                              aria-label="즐겨찾기 새로고침"
                              title={
                                favRefreshed
                                  ? '이번 접속에서 이미 새로고침했어요'
                                  : '로블록스에서 즐겨찾기를 다시 가져와요'
                              }
                              className="rounded-lg border border-border p-1.5 text-text-sub transition-colors hover:text-text disabled:opacity-40"
                            >
                              <RotateCw
                                className={`h-3.5 w-3.5 ${isRefreshing ? 'animate-spin' : ''}`}
                                aria-hidden="true"
                              />
                            </button>
                          </div>
                        </div>
            {isEmptyFavorites ? (
              <div className="rounded-xl border border-dashed border-border-strong p-6 text-center text-[13px] leading-relaxed text-text-muted">
                즐겨찾기 목록이 존재하지 않아
                <br />
                조회할 수 없습니다.
              </div>
            ) : (
              <UnassignedGameList games={unassigned} />
            )}
          </aside>

          <main className="flex flex-1 flex-col gap-4">
            {TIER_ORDER.map((tier) => (
              <TierDropZone
                key={tier}
                tier={tier}
                games={board[tier].map(findGame).filter((g): g is Game => Boolean(g))}
                onRemove={unassign}
              />
            ))}

            <div className="mt-1 flex flex-wrap gap-6">
              {TIER_ORDER.map((tier) => (
                <span key={tier} className="flex items-center gap-2 text-[13px] text-text-sub">
                  <span className={`font-bold ${TIER_META[tier].text}`}>{tier}</span> — {TIER_META[tier].label}
                </span>
              ))}
            </div>
          </main>
        </div>
      </div>

      <DragOverlay>{activeGame ? <GameCard game={activeGame} /> : null}</DragOverlay>
    </DndContext>
  );
}
