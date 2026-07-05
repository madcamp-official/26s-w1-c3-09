import { useState } from 'react';
import { Play, Youtube } from 'lucide-react';
import type { GameVideo } from '../../types/game';

type ShortsPanelProps = {
  gameName: string;
  videos: GameVideo[];
};

export default function ShortsPanel({ gameName, videos }: ShortsPanelProps) {
  const [activeVideoId, setActiveVideoId] = useState(videos[0]?.youtubeVideoId ?? null);
  const searchUrl = `https://www.youtube.com/results?search_query=${encodeURIComponent(`roblox ${gameName} shorts`)}`;

  return (
    <aside className="w-full shrink-0 lg:w-96">
      {/* 쇼츠 플레이어 — youtube-nocookie 도메인 iframe 임베드 */}
      <div className="overflow-hidden rounded-2xl border border-border bg-surface">
        {activeVideoId ? (
          <iframe
            key={activeVideoId}
            src={`https://www.youtube-nocookie.com/embed/${activeVideoId}`}
            title={`${gameName} 관련 영상`}
            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
            allowFullScreen
            className="aspect-[9/14] w-full"
          />
        ) : (
          <div className="flex aspect-[9/14] items-center justify-center text-[13px] text-text-muted">
            재생할 영상이 없어요
          </div>
        )}
      </div>

      <a
        href={searchUrl}
        target="_blank"
        rel="noreferrer"
        className="mt-4 flex w-full items-center justify-center gap-2.5 rounded-xl bg-accent py-3.5 text-[14.5px] font-bold text-white transition hover:bg-accent-strong"
      >
        <Youtube className="h-[18px] w-[18px]" aria-hidden="true" /> YouTube에서 쇼츠 검색
      </a>

      <p className="mt-7 mb-3 flex gap-2 text-[12.5px] tracking-wide text-text-sub">
        <span>관련</span>
        <span>영상</span>
        <span>미리보기</span>
      </p>

      <div className="flex flex-col gap-3">
        {videos.map((video) => {
          const isActive = video.youtubeVideoId === activeVideoId;
          return (
            <button
              key={video.youtubeVideoId}
              onClick={() => setActiveVideoId(video.youtubeVideoId)}
              className={`flex cursor-pointer items-center gap-3.5 rounded-xl border p-2.5 text-left transition ${
                isActive ? 'border-accent/50 bg-accent/10' : 'border-border bg-panel'
              }`}
            >
              {/* 유튜브가 videoId 기반으로 제공하는 정적 썸네일 이미지 */}
              <div className="relative h-[84px] w-[58px] shrink-0 overflow-hidden rounded-lg">
                <img
                  src={`https://i.ytimg.com/vi/${video.youtubeVideoId}/mqdefault.jpg`}
                  alt=""
                  className="h-full w-full object-cover"
                />
                <span className="absolute inset-0 flex items-center justify-center bg-black/30">
                  <Play className="h-4 w-4 text-white/85" aria-hidden="true" />
                </span>
              </div>
              <div className="min-w-0">
                <p className="text-[13px] leading-snug font-semibold text-text">{video.title}</p>
                <p className="mt-1.5 text-[11.5px] text-text-muted">{video.viewsLabel} · Shorts</p>
              </div>
            </button>
          );
        })}
      </div>
    </aside>
  );
}
