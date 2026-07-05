import { useDroppable } from '@dnd-kit/core';
import GameCard from './GameCard';
import { TIER_META } from '../../constants/tierlist';
import type { Tier } from '../../types/tierlist';
import type { Game } from '../../types/game';

type TierDropZoneProps = {
  tier: Tier;
  games: Game[];
  onRemove: (gameId: string) => void;
};

export default function TierDropZone({ tier, games, onRemove }: TierDropZoneProps) {
  const { setNodeRef, isOver } = useDroppable({ id: tier });
  const meta = TIER_META[tier];

  return (
    <div
      ref={setNodeRef}
      className={`flex min-h-[128px] overflow-hidden rounded-2xl border transition-colors ${
        isOver ? `${meta.border} ${meta.bgSoft}` : 'border-border bg-panel'
      }`}
    >
      <div className={`flex w-28 shrink-0 flex-col items-center justify-center gap-1 border-r border-border ${meta.bgSoft}`}>
        <span className={`text-[34px] font-extrabold ${meta.text}`}>{tier}</span>
        <span className={`text-[12px] opacity-85 ${meta.text}`}>{meta.label}</span>
      </div>

      <div className="flex flex-1 flex-wrap content-center items-center gap-3 p-3">
        {games.length === 0 && !isOver && (
          <span className="px-3 text-[13px] text-text-muted">게임을 이곳으로 드래그하세요</span>
        )}
        {games.map((game) => (
          <div key={game.id} className="w-[260px]">
            <GameCard game={game} onRemove={() => onRemove(game.id)} />
          </div>
        ))}
      </div>
    </div>
  );
}
