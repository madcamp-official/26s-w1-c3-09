import { Link } from 'react-router-dom';

export default function Logo() {
  return (
    <Link to="/" className="flex items-center gap-2.5">
      {/* MadFinder 마크 — 로블록스 글리프(각진 사각 + 중앙 구멍)를 렌즈로 쓴 돋보기 */}
      <svg
        width="34"
        height="34"
        viewBox="0 0 512 512"
        aria-hidden="true"
        className="shrink-0"
      >
        <defs>
          <linearGradient id="mfLens" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stopColor="#6bd6ff" />
            <stop offset="0.5" stopColor="#00a2ff" />
            <stop offset="1" stopColor="#0068d6" />
          </linearGradient>
        </defs>
        <rect x="16" y="16" width="480" height="480" rx="116" fill="#0b0d12" />
        <g transform="rotate(20 222 222)">
          <line
            x1="300"
            y1="300"
            x2="410"
            y2="410"
            stroke="url(#mfLens)"
            strokeWidth="46"
            strokeLinecap="round"
          />
          <path
            fillRule="evenodd"
            fill="url(#mfLens)"
            d="M162 134 h120 a28 28 0 0 1 28 28 v120 a28 28 0 0 1 -28 28 h-120
               a28 28 0 0 1 -28 -28 v-120 a28 28 0 0 1 28 -28 z
               M205 195 h34 a10 10 0 0 1 10 10 v34 a10 10 0 0 1 -10 10 h-34
               a10 10 0 0 1 -10 -10 v-34 a10 10 0 0 1 10 -10 z"
          />
        </g>
      </svg>
      <span className="text-[15px] font-bold tracking-wide text-text">MadFinder</span>
    </Link>
  );
}
