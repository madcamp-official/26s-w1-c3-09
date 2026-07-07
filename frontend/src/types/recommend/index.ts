import type { TierEntry } from '../tierlist';
import type { Game } from '../game';

/** 요청 body — tier_entries 테이블에 대응 (tierlist 도메인의 TierEntry와 동일 형태) */
export type TierEntryPayload = TierEntry;

export type RecommendationsRequest = { nickname: string; entries: TierEntryPayload[] };

/**
 * user_recommendations 테이블에 대응.
 * matchPercent는 백엔드가 알고리즘(팬 공통 즐겨찾기 + 하위 게임 가중치)으로 산출한
 * score를 %로 환산한 값. reason은 추천 이유 한 줄 설명.
 */
export type Recommendation = {
  game: Game;
  rank: number;
  matchPercent: number;
  reason: string;
};

/*  popular: 인기 게임 / discovery: 숨은 발견. 같은 게임이 양쪽에 있어도 정상. */
export type RecommendationsResponse = {
    popular: Recommendation[];
    discovery: Recommendation[];
    };

/** 추천 모드 — normal: 즉시(DB만) / precise: 즉석 수집 후 계산 (잡+폴링) */
export type RecommendMode = 'normal' | 'precise';

/** 정밀모드 진행률 — percent는 티어 중요도 가중(SSS 큰 게임일수록 많이 참). current/total은 개수 표시. */
export type PreciseProgress = {
  current: number;
  total: number;
  collectingName: string | null;
  percent: number;
  /** 남은 예상 시간(초). 접속량·부하로 수집이 느려지면 매 폴링마다 커진다. 초반(percent 낮음)엔 null. */
  etaSeconds?: number | null;
};

/**
 * GET /api/recommend/status/{jobId} 를 FE 형태로 변환한 결과.
 * running: progress만 / finalizing: message만(정리·계산 중) / done: popular·discovery만 / error: message만.
 */
export type PreciseStatusResult = {
  status: 'running' | 'finalizing' | 'done' | 'error';
  progress: PreciseProgress | null;
  popular: Recommendation[];
  discovery: Recommendation[];
  message: string | null;
};
