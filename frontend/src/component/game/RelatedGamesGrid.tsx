import { Gamepad2, Users } from 'lucide-react';
import type { Game } from '../../types/game';

type RelatedGamesGridProps = {
  games: Game[];
  onSelect: (gameId: string) => void;
};

export default function RelatedGamesGrid({ games, onSelect }: RelatedGamesGridProps) {
  return (
    <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
      {games.slice(0, 4).map((game) => (
        <button
          key={game.id}
          onClick={() => onSelect(game.id)}
          className="cursor-pointer overflow-hidden rounded-xl border border-border bg-surface text-left transition-transform hover:-translate-y-0.5"
        >
          <div
            className="relative flex aspect-[16/10] items-center justify-center"
            style={{
              background: `linear-gradient(135deg, ${game.thumbnailTheme.from}, ${game.thumbnailTheme.to})`,
            }}
          >
            <Gamepad2 className="h-8 w-8 text-white/50" aria-hidden="true" />
            <span className="absolute top-2 right-2 flex items-center gap-1 rounded-full bg-black/60 px-2 py-0.5 text-[10px] font-medium text-white">
              <Users className="h-2.5 w-2.5" aria-hidden="true" /> {game.playingLabel}
            </span>
          </div>
          <div className="px-3 py-2.5">
            <p className="truncate text-[12.5px] font-semibold text-text">{game.name}</p>
            <p className="text-[11px] text-text-muted">{game.genre}</p>
          </div>
        </button>
      ))}
    </div>
  );
}
