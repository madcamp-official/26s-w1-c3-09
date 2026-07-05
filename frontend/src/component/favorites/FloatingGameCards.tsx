import { Gamepad2 } from 'lucide-react';

type ShowcaseGame = {
  name: string;
  from: string;
  to: string;
  className: string;
};

// 실제 조회 데이터와 무관하게 랜딩 화면 분위기를 위해 하드코딩한 장식용 카드
const SHOWCASE_GAMES: ShowcaseGame[] = [
  { name: 'Adopt Me!', from: '#7BB8D9', to: '#4A7A9B', className: 'left-[3%] top-[8%] -rotate-6' },
  { name: 'Brookhaven RP', from: '#3B3B58', to: '#1E1E36', className: 'right-[3%] top-[6%] rotate-6' },
  { name: 'Blox Fruits', from: '#1E5F74', to: '#133B5C', className: 'left-[1.5%] top-[42%] rotate-3' },
  { name: 'Pet Simulator X', from: '#8C6BB8', to: '#4A3A6B', className: 'right-[2%] top-[44%] -rotate-3' },
  { name: 'Tower of Hell', from: '#C4622D', to: '#7A3416', className: 'bottom-[7%] left-[6%] rotate-6' },
  { name: 'Murder Mystery 2', from: '#2A2A3E', to: '#101018', className: 'bottom-[5%] right-[6%] -rotate-6' },
];

export default function FloatingGameCards() {
  return (
    <div className="pointer-events-none absolute inset-0 hidden lg:block" aria-hidden="true">
      {SHOWCASE_GAMES.map((g) => (
        <div key={g.name} className={`absolute w-[195px] opacity-30 ${g.className}`}>
          <div
            className="flex aspect-[16/10] items-center justify-center rounded-2xl"
            style={{ background: `linear-gradient(135deg, ${g.from}, ${g.to})` }}
          >
            <Gamepad2 className="h-9 w-9 text-white/70" />
          </div>
          <p className="mt-2 text-[13px] font-semibold text-text-sub">{g.name}</p>
        </div>
      ))}
    </div>
  );
}
