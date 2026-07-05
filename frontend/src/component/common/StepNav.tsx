import { Check, ChevronRight } from 'lucide-react';
import { useLocation } from 'react-router-dom';

const STEPS = [
  { step: 1, label: '닉네임' },
  { step: 2, label: '티어표' },
  { step: 3, label: '추천' },
];

function getCurrentStep(pathname: string): number {
  if (pathname.startsWith('/tierlist')) return 2;
  if (pathname.startsWith('/recommend') || pathname.startsWith('/games')) return 3;
  return 1;
}

export default function StepNav() {
  const { pathname } = useLocation();
  const currentStep = getCurrentStep(pathname);

  return (
    <div className="hidden items-center gap-1.5 md:flex">
      {STEPS.map((s, i) => {
        const done = s.step < currentStep;
        const active = s.step === currentStep;

        return (
          <div key={s.step} className="flex items-center gap-1.5">
            <div
              className={`flex items-center gap-2 rounded-full px-3 py-1.5 ${
                active ? 'border border-accent/40 bg-accent/10' : 'border border-transparent'
              }`}
            >
              <span
                className={`flex h-5 w-5 items-center justify-center rounded-full text-[11px] font-semibold ${
                  done || active
                    ? 'bg-accent text-white'
                    : 'border-[1.5px] border-text-muted text-text-muted'
                }`}
              >
                {done ? <Check className="h-3 w-3" aria-hidden="true" /> : s.step}
              </span>
              <span
                className={`text-[13px] ${
                  active ? 'font-semibold text-accent' : done ? 'text-text-sub' : 'text-text-muted'
                }`}
              >
                {s.label}
              </span>
            </div>
            {i < STEPS.length - 1 && (
              <ChevronRight className="h-3.5 w-3.5 text-text-muted" aria-hidden="true" />
            )}
          </div>
        );
      })}
    </div>
  );
}
