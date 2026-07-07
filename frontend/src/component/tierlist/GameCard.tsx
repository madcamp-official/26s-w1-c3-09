import { useDraggable } from '@dnd-kit/core';
import { CSS } from '@dnd-kit/utilities';
import { X } from 'lucide-react';
import type { Game } from '../../types/game';
import GameThumbnail from '../common/GameThumbnail';

type GameCardProps = {
  game: Game;
  onRemove?: () => void;
};

export default function GameCard({ game, onRemove }: GameCardProps) {
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({
    id: game.id,
  });

  const style = transform ? { transform: CSS.Translate.toString(transform) } : undefined;

  return (
    <div
      ref={setNodeRef}
      style={style}
      {...listeners}
      {...attributes}
      className={`group relative flex w-full cursor-grab touch-none items-center gap-3 overflow-hidden rounded-xl border border-border bg-surface pr-2 active:cursor-grabbing ${
        isDragging ? 'opacity-40' : ''
      }`}
    >
      <div
        className="flex h-[62px] w-[92px] shrink-0 items-center justify-center"
        style={{
          background: `linear-gradient(135deg, ${game.thumbnailTheme.from}, ${game.thumbnailTheme.to})`,
        }}
      >
      <GameThumbnail
        game={game}
        className="flex h-[62px] w-[92px] shrink-0 items-center justify-center"
        iconClassName="h-7 w-7 text-white/60"
      />
      </div>

      <div className="min-w-0 flex-1 py-1.5">
        <p className="truncate text-[13.5px] font-semibold text-text">{game.name}</p>
        <p className="text-[11.5px] text-text-muted">{game.genre}</p>
      </div>

      {onRemove && (
        <button
          onClick={(e) => {
            e.stopPropagation();
            onRemove();
          }}
          onPointerDown={(e) => e.stopPropagation()}
          className="rounded-full p-1 text-text-muted opacity-0 transition-opacity group-hover:opacity-100"
          aria-label={`${game.name} 티어에서 제거`}
        >
          <X className="h-3.5 w-3.5" aria-hidden="true" />
        </button>
      )}
    </div>
  );
}
