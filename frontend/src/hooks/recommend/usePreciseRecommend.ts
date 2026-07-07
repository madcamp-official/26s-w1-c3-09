import { useCallback, useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  startPreciseRecommend,
  getRecommendStatus,
  cancelPreciseRecommend,
} from '../../api/recommend';
import type { ApiError } from '../../types/common';
import type { PreciseProgress, PreciseStatusResult, Recommendation } from '../../types/recommend';

export type PrecisePhase = 'starting' | 'running' | 'finalizing' | 'done' | 'error';

export type PreciseRecommendState = {
  phase: PrecisePhase;
  progress: PreciseProgress | null;
  popular: Recommendation[];
  discovery: Recommendation[];
  errorMessage: string | null;
  finalizingMessage: string | null; // "정리 중입니다.." — finalizing일 때 표시
  cancel: () => void; // 진행 중 중단 요청 (현재 게임 마무리 후 부분 결과)
  canCancel: boolean; // 중단 버튼 노출 여부 (running 중 + 아직 취소 안 함)
};

const EMPTY: { popular: Recommendation[]; discovery: Recommendation[] } = {
  popular: [],
  discovery: [],
};

const FINALIZING_TEXT = '정리 중입니다..';

/**
 * 정밀모드 구동 훅: 잡 1회 시작 → status를 2.5초 간격 폴링 → done/error에서 멈춤.
 * running: progress(가중 percent)를, finalizing: "정리 중" 문구를, done: 두 섹션 결과를 노출.
 * 중단(cancel): 서버에 취소 요청 + 즉시 finalizing으로 전환(현재 게임 마무리까지 ~1분).
 * 잡 시작 실패(409 등)·상태 조회 실패(404 등)는 phase='error'로 수렴 (호출측이 일반모드 폴백).
 */
export const usePreciseRecommend = (
  nickname: string | null,
  enabled: boolean,
): PreciseRecommendState => {
  const [jobId, setJobId] = useState<string | null>(null);
  const [startError, setStartError] = useState<string | null>(null);
  const [cancelling, setCancelling] = useState(false);
  const startedRef = useRef(false);

  // 잡 시작 — enabled가 처음 켜질 때 한 번만
  useEffect(() => {
    if (!enabled || !nickname || startedRef.current) return;
    startedRef.current = true;
    startPreciseRecommend(nickname)
      .then((res) => setJobId(res.jobId))
      .catch((e: ApiError) => setStartError(e?.detail ?? '정밀 분석을 시작하지 못했습니다.'));
  }, [enabled, nickname]);

  // 상태 폴링 — done/error면 interval 중단 (finalizing은 계속 폴링해서 done을 기다림)
  const statusQuery = useQuery<PreciseStatusResult, ApiError>({
    queryKey: ['recommend-status', jobId],
    queryFn: () => getRecommendStatus(jobId as string),
    enabled: !!jobId,
    refetchInterval: (query) => {
      const s = query.state.data?.status;
      return s === 'done' || s === 'error' ? false : 2500;
    },
  });

  const cancel = useCallback(() => {
    if (!jobId || cancelling) return;
    setCancelling(true); // 낙관적: 현재 게임 마무리 동안 즉시 "정리 중" 표시
    void cancelPreciseRecommend(jobId);
  }, [jobId, cancelling]);

  const base = { cancel, canCancel: false };

  if (startError) {
    return { phase: 'error', progress: null, ...EMPTY, errorMessage: startError, finalizingMessage: null, ...base };
  }
  if (statusQuery.isError) {
    return {
      phase: 'error',
      progress: null,
      ...EMPTY,
      errorMessage: statusQuery.error?.detail ?? '분석 상태를 확인하지 못했습니다.',
      finalizingMessage: null,
      ...base,
    };
  }

  const data = statusQuery.data;
  if (!jobId || !data) {
    return { phase: 'starting', progress: null, ...EMPTY, errorMessage: null, finalizingMessage: null, ...base };
  }
  if (data.status === 'error') {
    return {
      phase: 'error',
      progress: null,
      ...EMPTY,
      errorMessage: data.message ?? '분석 중 오류가 발생했어요.',
      finalizingMessage: null,
      ...base,
    };
  }
  if (data.status === 'done') {
    return {
      phase: 'done',
      progress: null,
      popular: data.popular,
      discovery: data.discovery,
      errorMessage: null,
      finalizingMessage: null,
      ...base,
    };
  }
  // finalizing(서버) 또는 취소 낙관적 전환 → "정리 중" 화면
  if (data.status === 'finalizing' || cancelling) {
    return {
      phase: 'finalizing',
      progress: null,
      ...EMPTY,
      errorMessage: null,
      finalizingMessage: data.message ?? FINALIZING_TEXT,
      cancel,
      canCancel: false,
    };
  }
  // running — 중단 버튼 노출
  return {
    phase: 'running',
    progress: data.progress,
    ...EMPTY,
    errorMessage: null,
    finalizingMessage: null,
    cancel,
    canCancel: true,
  };
};
