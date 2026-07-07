import { Compass, Flame, Loader2, Play, Sparkles } from 'lucide-react';
import { useRecommendPage } from '../../hooks/recommend/useRecommendPage';
import { TIER_META } from '../../constants/tierlist';
import RecommendationCard from '../../component/recommend/RecommendationCard';
import type { Recommendation } from '../../types/recommend';

export default function RecommendPage() {
  const { nickname, isFetching, isError, popular, discovery, totalCount, tierCounts, goToDetail } =
    useRecommendPage();

  if (isFetching) {
    return (
      <div className="flex flex-1 flex-col items-center justify-center gap-4 py-24">
        <Loader2 className="h-8 w-8 animate-spin text-accent" aria-hidden="true" />
        <p className="text-[14px] text-text-sub">티어표를 분석하고 있어요...</p>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="flex flex-1 items-center justify-center py-24 text-[14px] text-text-sub">
        추천 결과를 불러오지 못했어요. 잠시 후 다시 시도해주세요.
      </div>
    );
  }

  return (
    <div className="mx-auto w-full max-w-7xl px-6 py-10">
      <div className="flex flex-wrap items-start justify-between gap-6 border-b border-border pb-9">
        <div>
          <span className="flex items-center gap-2 text-[13px] font-medium text-accent">
            <Sparkles className="h-3.5 w-3.5" aria-hidden="true" /> AI 분석 완료
          </span>
          <h1 className="mt-3 text-[40px] leading-tight font-extrabold text-text">
            @{nickname}님을 위한
            <br />
            <span className="text-accent">맞춤 추천 게임</span>
          </h1>
          <p className="mt-4 text-[14.5px] text-text-sub">
            티어표 분석을 기반으로 {totalCount}개의 게임을 추천했어요. 게임을 클릭하면 자세한 정보와
            쇼츠를 볼 수 있어요.
          </p>
        </div>

        <div className="flex gap-3">
          {tierCounts.map(({ tier, count }) => (
            <div
              key={tier}
              className={`flex items-center gap-2.5 rounded-xl border border-border px-4 py-3 ${TIER_META[tier].bgSoft}`}
            >
              <span className={`text-[18px] font-extrabold ${TIER_META[tier].text}`}>{tier}</span>
              <div className="leading-tight">
                <p className="text-[13px] font-semibold text-text">{count}개</p>
                <p className="text-[11px] text-text-muted">{TIER_META[tier].label}</p>
              </div>
            </div>
          ))}
        </div>
      </div>

      {totalCount === 0 ? (
        <div className="flex flex-col items-center justify-center gap-2 py-24 text-center">
          <p className="text-[15px] font-semibold text-text">추천을 준비하고 있어요</p>
          <p className="text-[13.5px] text-text-sub">
            티어표에 담은 게임의 취향 데이터를 모으는 중이에요. 잠시 후 다시 확인해주세요.
          </p>
        </div>
      ) : (
        <>
          <Section
            icon={<Flame className="h-4 w-4 text-accent" aria-hidden="true" />}
            title="인기 게임"
            description="많은 유저가 함께 즐기는, 검증된 추천작이에요."
            items={popular}
            onSelect={goToDetail}
          />
          <Section
            icon={<Compass className="h-4 w-4 text-accent" aria-hidden="true" />}
            title="숨은 발견"
            description="아직 덜 알려졌지만 취향에 맞을 게임이에요."
            items={discovery}
            onSelect={goToDetail}
          />
        </>
      )}
    </div>
  );
}

type SectionProps = {
  icon: React.ReactNode;
  title: string;
  description: string;
  items: Recommendation[];
  onSelect: (gameId: string) => void;
};

function Section({ icon, title, description, items, onSelect }: SectionProps) {
  if (items.length === 0) return null;

  return (
    <section className="mt-12">
      <div className="mb-5 flex flex-wrap items-center justify-between gap-2">
        <div>
          <h2 className="flex items-center gap-2 text-[19px] font-bold text-text">
            {icon} {title}
            <span className="text-[14px] font-medium text-text-muted">{items.length}개</span>
          </h2>
          <p className="mt-1 text-[13px] text-text-sub">{description}</p>
        </div>
        <span className="flex items-center gap-1.5 text-[13px] text-text-muted">
          클릭하면 쇼츠 영상을 볼 수 있어요 <Play className="h-3 w-3" aria-hidden="true" />
        </span>
      </div>

      <div className="grid grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-3">
        {items.map((rec) => (
          <RecommendationCard
            key={`${rec.rank}-${rec.game.id}`}
            recommendation={rec}
            onClick={() => onSelect(rec.game.id)}
          />
        ))}
      </div>
    </section>
  );
}
