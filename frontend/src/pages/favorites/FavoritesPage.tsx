import { Star, Zap } from 'lucide-react';
import FloatingGameCards from '../../component/favorites/FloatingGameCards';
import NicknameSearchForm from '../../component/favorites/NicknameSearchForm';
import { useNicknameSearch } from '../../hooks/favorites/useNicknameSearch';

export default function FavoritesPage() {
  const { inputValue, onChange, onSubmit, isLoading, errorMessage } = useNicknameSearch();

  return (
    <div className="relative flex flex-1 flex-col items-center justify-center overflow-hidden px-4 py-16">
      <div
        className="absolute inset-0"
        style={{
          backgroundImage:
            'linear-gradient(var(--color-border) 1px, transparent 1px), linear-gradient(90deg, var(--color-border) 1px, transparent 1px)',
          backgroundSize: '48px 48px',
          opacity: 0.35,
        }}
        aria-hidden="true"
      />

      <FloatingGameCards />

      <div className="relative z-10 flex w-full max-w-xl flex-col items-center text-center">
        <span className="mb-8 flex items-center gap-2 rounded-full border border-accent/45 bg-accent/10 px-4 py-2 text-[13px] font-medium text-accent">
          <Zap className="h-3.5 w-3.5" aria-hidden="true" />
          알고리즘 기반 게임 추천 시스템
        </span>

        <h1 className="text-[56px] leading-tight font-extrabold tracking-tight text-text sm:text-[64px]">
          나만의
          <br />
          <span className="text-accent">티어표</span>
          <br />
          만들기
        </h1>

        <p className="mt-7 text-[16px] leading-relaxed text-text-sub">
          즐겨 하는 로블록스 게임을 SSS/A/B/C 티어로 정리하고,
          <br />
          취향에 딱 맞는 새 게임을 추천받아보세요.
        </p>

        <div className="mt-10 w-full">
          <NicknameSearchForm
            value={inputValue}
            onChange={onChange}
            onSubmit={onSubmit}
            isLoading={isLoading}
            errorMessage={errorMessage}
          />
        </div>

        <div className="mt-9 flex flex-wrap items-center justify-center gap-7 text-[13px] text-text-muted">
          <span className="flex items-center gap-1.5">
            <Star className="h-3.5 w-3.5" aria-hidden="true" /> 즐겨찾기 게임 자동 조회
          </span>
          <span className="flex items-center gap-1.5">
            <Star className="h-3.5 w-3.5" aria-hidden="true" /> SSS · A · B · C 티어
          </span>
          <span className="flex items-center gap-1.5">
            <Star className="h-3.5 w-3.5" aria-hidden="true" /> 알고리즘 기반 추천
          </span>
        </div>
      </div>
    </div>
  );
}
