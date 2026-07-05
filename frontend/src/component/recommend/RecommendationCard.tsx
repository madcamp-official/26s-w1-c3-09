import { ChevronRight, Gamepad2, Trophy, Users } from 'lucide-react';
import type { Recommendation } from '../../types/recommend';

const RANK_BADGE: Record<number, string> = {
  1: 'bg-tier-s text-black',
  2: 'bg-zinc-300 text-black',
  3: 'bg-amber-600 text-black',
};

type RecommendationCardProps = {
  recommendation: Recommendation;
  onClick: () => void;
};

export default function RecommendationCard({ recommendation, onClick }: RecommendationCardProps) {
  const { game, rank, matchPercent, reason } = recommendation;

  return (
    <button
      onClick={onClick}
      className="flex cursor-pointer flex-col overflow-hidden rounded-2xl border border-border bg-panel text-left transition-transform hover:-translate-y-1 focus-visible:ring-2 focus-visible:ring-accent/50 focus-visible:outline-none"
    >
      <div
        className="relative flex aspect-video items-center justify-center"
        style={{
          background: `linear-gradient(135deg, ${game.thumbnailTheme.from}, ${game.thumbnailTheme.to})`,
        }}
      >
        <Gamepad2 className="h-12 w-12 text-white/40" aria-hidden="true" />

        {rank <= 3 && (
          <span
            className={`absolute top-2.5 left-2.5 flex items-center gap-1.5 rounded-full px-3 py-1 text-[12px] font-bold ${RANK_BADGE[rank]}`}
          >
            <Trophy className="h-3 w-3" aria-hidden="true" /> #{rank} 추천
          </span>
        )}
        <span className="absolute top-2.5 right-2.5 rounded-full bg-black/65 px-3 py-1 text-[12px] font-bold text-white backdrop-blur-sm">
          {matchPercent}% 매칭
        </span>

        <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/75 to-transparent p-4">
          <p className="text-[18px] font-bold text-white">{game.name}</p>
          <p className="mt-0.5 flex items-center gap-2 text-[12.5px] text-white/75">
            {game.genre} · <Users className="h-3 w-3" aria-hidden="true" /> {game.playingLabel}
          </p>
        </div>
      </div>

      <div className="flex flex-1 flex-col p-4">
        <div className="flex items-center gap-3">
          <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-surface">
            <div
              className="h-full rounded-full bg-gradient-to-r from-accent to-amber-400"
              style={{ width: `${matchPercent}%` }}
            />
          </div>
          <span className="text-[13px] font-semibold text-text">{matchPercent}%</span>
        </div>

        <p className="mt-3 text-[12.5px] text-text-sub italic">{reason}</p>

        <div className="mt-3 flex flex-wrap gap-2">
          {game.tags.map((tag) => (
            <span key={tag} className="rounded-md border border-border px-2.5 py-1 font-mono text-[11px] text-text-sub">
              {tag}
            </span>
          ))}
        </div>

        <span className="mt-4 flex items-center justify-between border-t border-border pt-3 text-[13px] font-semibold text-accent">
          자세히 보기 <ChevronRight className="h-3.5 w-3.5" aria-hidden="true" />
        </span>
      </div>
    </button>
  );
}
