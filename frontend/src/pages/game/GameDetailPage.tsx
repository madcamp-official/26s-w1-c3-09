import { ArrowLeft, Calendar, Info, Loader2, Star, Tag, Users } from 'lucide-react';
import { useGameDetailPage } from '../../hooks/game/useGameDetailPage';
import ShortsPanel from '../../component/game/ShortsPanel';
import RelatedGamesGrid from '../../component/game/RelatedGamesGrid';
import ScreenshotCarousel from '../../component/game/ScreenshotCarousel';

export default function GameDetailPage() {
  const { isFetching, isError, detail, recommendation, goToGame, goBack } = useGameDetailPage();

  if (isFetching) {
    return (
      <div className="flex flex-1 items-center justify-center py-24">
        <Loader2 className="h-8 w-8 animate-spin text-accent" aria-hidden="true" />
      </div>
    );
  }

  if (isError || !detail) {
    return (
      <div className="flex flex-1 items-center justify-center py-24 text-[14px] text-text-sub">
        게임 정보를 불러오지 못했어요.
      </div>
    );
  }

  const { game, screenshots, relatedGames, videos } = detail;

  return (
    <div className="mx-auto flex w-full max-w-7xl flex-col gap-10 px-6 py-8 lg:flex-row">
      <div className="min-w-0 flex-1">
        <button
          onClick={goBack}
          className="mb-4 flex items-center gap-1.5 text-[13px] font-medium text-text-sub transition-colors hover:text-text"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden="true" /> 추천 목록으로
        </button>

        {/* 히어로 — 실제 스크린샷 캐러셀 (없으면 그라데이션 폴백). game.id로 keying해 게임 이동 시 인덱스 초기화 */}
        <ScreenshotCarousel
          key={game.id}
          screenshots={screenshots}
          theme={game.thumbnailTheme}
          iconUrl={game.iconUrl}
          gameName={game.name}
          playingLabel={game.playingLabel}
        />

        {/* 타이틀 + 메타 + 매칭 점수 */}
        <div className="mt-7 flex flex-wrap items-start justify-between gap-5">
          <div>
            <h1 className="text-[40px] font-extrabold tracking-tight text-text">{game.name}</h1>
            <div className="mt-3.5 flex flex-wrap items-center gap-4 text-[13.5px] text-text-sub">
              <span className="rounded-full border border-accent/35 bg-accent/10 px-3.5 py-1.5 font-semibold text-accent">
                {game.genre}
              </span>
              <span className="flex items-center gap-1.5">
                <Users className="h-[15px] w-[15px]" aria-hidden="true" /> {game.playingLabel} 플레이 중
              </span>
              {game.releasedYear > 0 && (
                <span className="flex items-center gap-1.5">
                  <Calendar className="h-[15px] w-[15px]" aria-hidden="true" /> {game.releasedYear}년
                </span>
              )}
              {game.rating > 0 && (
                <span className="flex items-center gap-1.5 text-tier-s">
                  <Star className="h-[15px] w-[15px] fill-tier-s" aria-hidden="true" /> {game.rating}
                </span>
              )}
            </div>
          </div>

          {recommendation && (
            <div className="rounded-2xl border border-border bg-panel px-7 py-4 text-center">
              <p className="text-[32px] font-extrabold text-accent">
                {recommendation.matchPercent}%
              </p>
              <p className="mt-0.5 flex gap-2 text-[12px] text-text-muted">
                <span>매칭</span>
                <span>점수</span>
              </p>
            </div>
          )}
        </div>

        {/* 추천 이유 — recommendation_sources 기반 */}
        {recommendation && (
          <div className="mt-7 rounded-2xl border border-border bg-panel p-6">
            <p className="mb-4 flex gap-2 text-[13px] tracking-wide text-text-sub">
              <span>추천</span>
              <span>이유</span>
            </p>
            <div className="flex items-center gap-4">
              <div className="h-2 flex-1 overflow-hidden rounded-full bg-surface">
                <div
                  className="h-full rounded-full bg-gradient-to-r from-accent to-amber-400"
                  style={{ width: `${recommendation.matchPercent}%` }}
                />
              </div>
              <span className="text-[15px] font-bold text-text">
                {recommendation.matchPercent}%
              </span>
            </div>
            <p className="mt-4 text-[14px] text-text-sub italic">{recommendation.reason}</p>
          </div>
        )}

        {/* 게임 소개 */}
        <div className="mt-8">
          <h2 className="mb-3.5 flex items-center gap-2 text-[17px] font-bold text-text">
            <Info className="h-[17px] w-[17px] text-text-sub" aria-hidden="true" /> 게임 소개
          </h2>
          <p className="text-[15px] leading-relaxed text-text-sub">{game.description}</p>
          {game.developerName && (
            <p className="mt-3 font-mono text-[13px] text-text-muted">개발: {game.developerName}</p>
          )}
        </div>

        {/* 장르 태그 */}
        <div className="mt-8">
          <h2 className="mb-3.5 flex items-center gap-2 text-[17px] font-bold text-text">
            <Tag className="h-[17px] w-[17px] text-text-sub" aria-hidden="true" /> 장르 태그
          </h2>
          <div className="flex flex-wrap gap-2.5">
            {game.tags.map((tag) => (
              <span
                key={tag}
                className="rounded-lg border border-border-strong px-3.5 py-2 font-mono text-[13px] text-text-sub"
              >
                # {tag}
              </span>
            ))}
          </div>
        </div>

        {/* 관련 게임 — game_relations (People Also Join) */}
        <div className="mt-10">
          <h2 className="mb-4 text-[17px] font-bold text-text">이 게임과 함께 즐기는 게임</h2>
          <RelatedGamesGrid games={relatedGames} onSelect={goToGame} />
        </div>
      </div>

      <ShortsPanel gameName={game.name} videos={videos} />
    </div>
  );
}
