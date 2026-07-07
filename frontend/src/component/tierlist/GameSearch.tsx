import { useState, type FormEvent } from 'react';
import { Loader2, Plus, Search } from 'lucide-react';
import toast from 'react-hot-toast';
import { useSearchGamesMutation } from '../../api/search/hooks/useSearch';
import type { Game } from '../../types/game';
import type { ApiError } from '../../types/common';

type GameSearchProps = {
  onAdd: (game: Game) => void;
};

/**
 * 게임 이름 검색 → 결과에서 골라 티어표 풀에 추가.
 * 타이핑마다 부르지 않고 엔터/검색 버튼으로만 호출 (로블록스 실시간 검색이라 무거움).
 */
export default function GameSearch({ onAdd }: GameSearchProps) {
  const [term, setTerm] = useState('');
  const { mutate, data: results, isPending, reset } = useSearchGamesMutation();

  const submit = (e: FormEvent) => {
    e.preventDefault();
    /* 결과가 이미 열려 있을 때, 다시 누르면 접기(검색바만 남김) */
    if (results) {
        reset();
        return;
    }
    const q = term.trim();
    if (!q) return;
    mutate(q, {
      onError: (err: ApiError) =>
        toast.error(
          err?.status === 429
            ? '지금은 요청이 많아요. 잠시 후 다시 시도해주세요.'
            : (err?.detail ?? '검색에 실패했어요.'),
        ),
    });
  };

  const handleAdd = (game: Game) => {
    onAdd(game);
    toast.success(`"${game.name}" 추가됨`);
  };

  return (
    <div className="mb-4">
      <form onSubmit={submit} className="flex gap-2">
        <div className="relative flex-1">
          <Search
            className="pointer-events-none absolute top-1/2 left-2.5 h-3.5 w-3.5 -translate-y-1/2 text-text-muted"
            aria-hidden="true"
          />
          <input
            value={term}
            onChange={(e) => setTerm(e.target.value)}
            placeholder="게임 이름으로 검색"
            className="w-full rounded-lg border border-border bg-surface py-2 pr-2 pl-8 text-[13px] text-text placeholder:text-text-muted focus:border-accent focus:outline-none"
          />
        </div>
        <button
          type="submit"
          disabled={isPending || (!results && !term.trim())}
          className="flex items-center rounded-lg bg-accent px-3 text-[13px] font-semibold text-black disabled:opacity-40"
        >
          {isPending ? (<Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />) :
              results ?
              ('닫기') : ('검색')
              }
        </button>
      </form>

      {results && (
        <div className="mt-2 max-h-60 space-y-1 overflow-y-auto">
          {results.length === 0 ? (
            <p className="px-1 py-2 text-[12.5px] text-text-muted">검색 결과가 없어요.</p>
          ) : (
            results.map((game) => (
              <button
                key={game.id}
                onClick={() => handleAdd(game)}
                className="flex w-full items-center justify-between gap-2 rounded-lg border border-border px-3 py-2 text-left text-[13px] text-text transition-colors hover:border-accent"
              >
                <span className="min-w-0 truncate">{game.name}</span>
                <Plus className="h-3.5 w-3.5 shrink-0 text-accent" aria-hidden="true" />
              </button>
            ))
          )}
        </div>
      )}
    </div>
  );
}