import { useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { startPreciseRecommend, getRecommendStatus } from '../../api/recommend';
import type { ApiError } from '../../types/common';
import type { PreciseProgress, Recommendation } from '../../types/recommend';

export type PrecisePhase = 'starting' | 'running' | 'done' | 'error';

export type PreciseRecommendState = {
  phase: PrecisePhase;
  progress: PreciseProgress | null;
  popular: Recommendation[];
  discovery: Recommendation[];
  errorMessage: string | null;
};

const EMPTY = { popular: [], discovery: [] } as const;

/**
 * 정밀모드 구동 훅: 잡 1회 시작 → status를 2.5초 간격 폴링 → done/error에서 멈춤.
 * 진행 중에는 progress(current/total/collectingName)를, 완료 시 두 섹션 결과를 노출.
 * 잡 시작 실패(409 등)·상태 조회 실패(404 등)는 phase='error'로 수렴 (호출측이 일반모드 폴백).
 */
export const usePreciseRecommend = (
  nickname: string | null,
  enabled: boolean,
): PreciseRecommendState => {
  const [jobId, setJobId] = useState<string | null>(null);
  const [startError, setStartError] = useState<string | null>(null);
  const startedRef = useRef(false);

  // 잡 시작 — enabled가 처음 켜질 때 한 번만
  useEffect(() => {
    if (!enabled || !nickname || startedRef.current) return;
    startedRef.current = true;
    startPreciseRecommend(nickname)
      .then((res) => setJobId(res.jobId))
      .catch((e: ApiError) => setStartError(e?.detail ?? '정밀 분석을 시작하지 못했습니다.'));
  }, [enabled, nickname]);

  // 상태 폴링 — done/error면 interval 중단
  const statusQuery = useQuery({
    queryKey: ['recommend-status', jobId],
    queryFn: () => getRecommendStatus(jobId as string),
    enabled: !!jobId,
    refetchInterval: (query) => {
      const s = query.state.data?.status;
      return s === 'done' || s === 'error' ? false : 2500;
    },
  });

  if (startError) {
    return { phase: 'error', progress: null, ...EMPTY, errorMessage: startError };
  }
  if (statusQuery.isError) {
    const e = statusQuery.error as ApiError;
    return {
      phase: 'error',
      progress: null,
      ...EMPTY,
      errorMessage: e?.detail ?? '분석 상태를 확인하지 못했습니다.',
    };
  }

  const data = statusQuery.data;
  if (!jobId || !data) {
    return { phase: 'starting', progress: null, ...EMPTY, errorMessage: null };
  }
  if (data.status === 'error') {
    return {
      phase: 'error',
      progress: null,
      ...EMPTY,
      errorMessage: data.message ?? '분석 중 오류가 발생했어요.',
    };
  }
  if (data.status === 'done') {
    return {
      phase: 'done',
      progress: null,
      popular: data.popular,
      discovery: data.discovery,
      errorMessage: null,
    };
  }
  return { phase: 'running', progress: data.progress, ...EMPTY, errorMessage: null };
};
