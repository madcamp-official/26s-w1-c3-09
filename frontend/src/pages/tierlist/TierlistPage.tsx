import { useState } from 'react';
import {
  DndContext,
  DragOverlay,
  PointerSensor,
  useSensor,
  useSensors,
} from '@dnd-kit/core';
import type { DragEndEvent, DragStartEvent } from '@dnd-kit/core';
import { Loader2, RotateCcw, Sparkles } from 'lucide-react';
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
    isSaving,
    saveError,
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
              <span className="text-[13px] text-text-muted">{unassigned.length}개</span>
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
