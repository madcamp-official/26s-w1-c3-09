import GameCard from './GameCard';
import type { Game } from '../../types/game';

type UnassignedGameListProps = {
  games: Game[];
};

export default function UnassignedGameList({ games }: UnassignedGameListProps) {
  if (games.length === 0) {
    return (
      <div className="rounded-xl border border-dashed border-border-strong p-6 text-center text-[13px] text-text-muted">
        모든 게임을 배치했어요.
        <br />
        이제 추천을 받아보세요!
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-3">
      {games.map((game) => (
        <GameCard key={game.id} game={game} />
      ))}
    </div>
  );
}
