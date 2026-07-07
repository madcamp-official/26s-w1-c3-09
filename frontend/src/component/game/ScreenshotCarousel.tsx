import { useState } from 'react';
import { ChevronLeft, ChevronRight, Gamepad2, Users } from 'lucide-react';
import type { GameThumbnailTheme } from '../../types/game';

type ScreenshotCarouselProps = {
  screenshots: string[];
  theme: GameThumbnailTheme; // 스크린샷이 없을 때 폴백 그라데이션
  gameName: string;
  playingLabel: string;
};

/**
 * 상세 페이지 히어로 — 실제 스크린샷 URL 캐러셀.
 * 스크린샷 0장이면 그라데이션 폴백, 1장이면 화살표·점 없이 이미지만, 2장 이상이면 넘기기 UI.
 */
export default function ScreenshotCarousel({
  screenshots,
  theme,
  gameName,
  playingLabel,
}: ScreenshotCarouselProps) {
  const [index, setIndex] = useState(0);
  const count = screenshots.length;
  const safeIndex = index < count ? index : 0;

  const prev = () => setIndex((i) => (i - 1 + count) % count);
  const next = () => setIndex((i) => (i + 1) % count);

  return (
    <div className="relative flex aspect-[16/8] items-center justify-center overflow-hidden rounded-2xl">
      {count > 0 ? (
        <img
          src={screenshots[safeIndex]}
          alt={`${gameName} 스크린샷 ${safeIndex + 1}`}
          className="h-full w-full object-cover"
        />
      ) : (
        <div
          className="flex h-full w-full items-center justify-center"
          style={{ background: `linear-gradient(135deg, ${theme.from}, ${theme.to})` }}
        >
          <Gamepad2 className="h-16 w-16 text-white/30" aria-hidden="true" />
        </div>
      )}

      <span className="absolute top-2.5 right-2.5 flex items-center gap-1 rounded-full bg-black/60 px-3 py-1 text-[11px] font-medium text-white backdrop-blur-sm">
        <Users className="h-3 w-3" aria-hidden="true" /> {playingLabel}
      </span>

      {count > 1 && (
        <>
          <button
            onClick={prev}
            aria-label="이전 스크린샷"
            className="absolute top-1/2 left-2 -translate-y-1/2 rounded-full bg-black/50 p-2 text-white backdrop-blur-sm hover:bg-black/70"
          >
            <ChevronLeft className="h-5 w-5" aria-hidden="true" />
          </button>
          <button
            onClick={next}
            aria-label="다음 스크린샷"
            className="absolute top-1/2 right-2 -translate-y-1/2 rounded-full bg-black/50 p-2 text-white backdrop-blur-sm hover:bg-black/70"
          >
            <ChevronRight className="h-5 w-5" aria-hidden="true" />
          </button>
          <div className="absolute inset-x-0 bottom-3 flex justify-center gap-1.5">
            {screenshots.map((src, i) => (
              <button
                key={src}
                onClick={() => setIndex(i)}
                aria-label={`스크린샷 ${i + 1}`}
                className={`h-1.5 rounded-full transition-all ${
                  i === safeIndex ? 'w-5 bg-white' : 'w-1.5 bg-white/50'
                }`}
              />
            ))}
          </div>
        </>
      )}
    </div>
  );
}
