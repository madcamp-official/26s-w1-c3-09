import Logo from './Logo';
import StepNav from './StepNav';
import { useNickname } from '../../store/hooks/useFavoritesStore';

export default function Header() {
  const nickname = useNickname();

  return (
    <header className="sticky top-0 z-50 flex items-center justify-between border-b border-border bg-bg/85 px-6 py-3.5 backdrop-blur-md">
      <div className="flex min-w-[180px] items-center">
        <Logo />
      </div>

      <StepNav />

      <div className="flex min-w-[180px] justify-end">
        {nickname && (
          <span className="flex items-center gap-2 rounded-full border border-border bg-surface px-3.5 py-1.5 text-[13px] text-text">
            <span className="h-[7px] w-[7px] rounded-full bg-emerald-400" aria-hidden="true" />@
            {nickname}
          </span>
        )}
      </div>
    </header>
  );
}
