import { useState } from 'react';
import { ChevronLeft, ChevronRight, Gamepad2, Users } from 'lucide-react';
import type { GameThumbnailTheme } from '../../types/game';

type ScreenshotCarouselProps = {
  screenshots: string[];
  theme: GameThumbnailTheme; // 스크린샷·아이콘 모두 없을 때 최종 폴백 그라데이션
  iconUrl?: string | null; // 스크린샷 없을 때 폴백: 게임 아이콘 (목록 카드와 동일 이미지)
  gameName: string;
  playingLabel: string;
};

/**
 * 상세 페이지 히어로 — 실제 스크린샷 URL 캐러셀.
 * 스크린샷 있으면 캐러셀(1장=이미지만, 2장+=넘기기 UI),
 * 없고 아이콘만 있으면 흐린 아이콘 배경 + 가운데 선명한 아이콘, 둘 다 없으면 그라데이션.
 */
export default function ScreenshotCarousel({
  screenshots,
  theme,
  iconUrl,
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
      ) : iconUrl ? (
        <>
          {/* 스크린샷 미수집 게임: 아이콘을 흐리게 깔고 위에 선명하게 (16:8 히어로에 맞춤) */}
          <img
            src={iconUrl}
            aria-hidden="true"
            className="absolute inset-0 h-full w-full scale-125 object-cover opacity-50 blur-2xl"
          />
          <img
            src={iconUrl}
            alt={gameName}
            className="relative h-full w-auto max-w-full object-contain"
          />
        </>
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
