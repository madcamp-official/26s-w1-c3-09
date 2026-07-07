import { Trophy, Users } from 'lucide-react';
import type { Recommendation } from '../../types/recommend';
import GameThumbnail from '../common/GameThumbnail';

const RANK_BADGE: Record<number, string> = {
  1: 'bg-tier-s text-black',
  2: 'bg-zinc-300 text-black',
  3: 'bg-amber-600 text-black',
};

type RecommendationCardProps = {
  recommendation: Recommendation;
  onClick: () => void;
};

/**
 * 컴팩트 카드 (Roblox 홈 스타일) — 가로 캐러셀용.
 * 썸네일 + 게임명 + 장르 L1·L2 + 매칭% 배지. 상세(추천이유/태그/스크린샷)는 클릭 후 상세페이지에서.
 */
export default function RecommendationCard({ recommendation, onClick }: RecommendationCardProps) {
  const { game, rank, matchPercent } = recommendation;
  const genres = game.tags.length > 0 ? game.tags.join(' · ') : game.genre;

  return (
    <button
      onClick={onClick}
      className="flex w-full cursor-pointer flex-col overflow-hidden rounded-xl border border-border bg-panel text-left transition-transform hover:-translate-y-1 focus-visible:ring-2 focus-visible:ring-accent/50 focus-visible:outline-none"
    >
      <GameThumbnail
        game={game}
        className="flex aspect-video items-center justify-center"
        iconClassName="h-10 w-10 text-white/40"
      >
        {rank <= 3 && (
          <span
            className={`absolute top-2 left-2 flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-bold ${RANK_BADGE[rank]}`}
          >
            <Trophy className="h-2.5 w-2.5" aria-hidden="true" /> #{rank}
          </span>
        )}
        <span className="absolute top-2 right-2 rounded-full bg-black/65 px-2 py-0.5 text-[11px] font-bold text-white backdrop-blur-sm">
          {matchPercent}% 매칭
        </span>
      </GameThumbnail>

      <div className="flex flex-col gap-1 p-2.5">
        <p className="truncate text-[13.5px] font-bold text-text" title={game.name}>
          {game.name}
        </p>
        <p className="flex items-center gap-1.5 truncate text-[11.5px] text-text-sub">
          <span className="truncate">{genres}</span>
          {game.playingLabel && (
            <>
              <span className="text-text-muted">·</span>
              <Users className="h-3 w-3 shrink-0" aria-hidden="true" />
              <span className="shrink-0">{game.playingLabel}</span>
            </>
          )}
        </p>
      </div>
    </button>
  );
}
