import { Gamepad2 } from 'lucide-react';
import type { ReactNode } from 'react';
import type { Game } from '../../types/game';

type GameThumbnailProps = {
  game: Pick<Game, 'name' | 'iconUrl' | 'thumbnailTheme'>;
  className?: string;
  iconClassName?: string;
  children?: ReactNode;
};

/**
 * 게임 썸네일 — iconUrl 있으면 실제 이미지, 없으면 그라데이션 + 게임패드 아이콘 폴백.
 * 뱃지 등 오버레이는 children으로 (컨테이너가 relative).
 */
export default function GameThumbnail({
  game,
  className = '',
  iconClassName = 'h-10 w-10 text-white/50',
  children,
}: GameThumbnailProps) {
  return (
    <div className={`relative overflow-hidden ${className}`}>
      {game.iconUrl ? (
        <img src={game.iconUrl} alt={game.name} className="h-full w-full object-cover" />
      ) : (
        <div
          className="flex h-full w-full items-center justify-center"
          style={{
            background: `linear-gradient(135deg, ${game.thumbnailTheme.from}, ${game.thumbnailTheme.to})`,
          }}
        >
          <Gamepad2 className={iconClassName} aria-hidden="true" />
        </div>
      )}
      {children}
    </div>
  );
}