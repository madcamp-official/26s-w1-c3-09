import { ChevronRight, Loader2 } from 'lucide-react';
import { COMMON_INPUT_CLASSNAME, PRIMARY_BTN } from '../../constants/common';
import { DEMO_NICKNAMES } from '../../constants/favorites';

type NicknameSearchFormProps = {
  value: string;
  onChange: (value: string) => void;
  onSubmit: () => void;
  isLoading: boolean;
  errorMessage: string | null;
};

export default function NicknameSearchForm({
  value,
  onChange,
  onSubmit,
  isLoading,
  errorMessage,
}: NicknameSearchFormProps) {
  return (
    <div className="w-full rounded-2xl border border-border bg-panel/90 p-6 text-left">
      <label htmlFor="nickname" className="mb-2.5 flex gap-2 text-[12px] tracking-wide text-text-sub">
        <span>로블록스</span>
        <span>닉네임</span>
      </label>

      <input
        id="nickname"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={(e) => e.key === 'Enter' && !isLoading && onSubmit()}
        placeholder="닉네임을 입력하세요"
        autoComplete="off"
        className={`w-full border-[1.5px] border-accent/50 bg-bg px-4 py-3.5 text-[15px] text-text placeholder:text-text-muted focus-visible:ring-accent/40 ${COMMON_INPUT_CLASSNAME}`}
      />

      {errorMessage && <p className="mt-2.5 text-[13px] text-red-400">{errorMessage}</p>}

      <button onClick={onSubmit} disabled={isLoading} className={`mt-4 flex w-full items-center justify-center gap-2 ${PRIMARY_BTN}`}>
        {isLoading ? (
          <Loader2 className="h-[17px] w-[17px] animate-spin" aria-hidden="true" />
        ) : (
          <>
            티어표 만들기 시작 <ChevronRight className="h-[17px] w-[17px]" aria-hidden="true" />
          </>
        )}
      </button>

      <p className="mt-4 text-center text-[12px] text-text-muted">
        예시 닉네임: {DEMO_NICKNAMES.join(' · ')}
      </p>
    </div>
  );
}
