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

/** 정밀모드 진행률 — "collectingName 수집 중 (current/total)" */
export type PreciseProgress = { current: number; total: number; collectingName: string | null };

/**
 * GET /api/recommend/status/{jobId} 를 FE 형태로 변환한 결과.
 * running: progress만 / done: popular·discovery만 / error: message만 유효.
 */
export type PreciseStatusResult = {
  status: 'running' | 'done' | 'error';
  progress: PreciseProgress | null;
  popular: Recommendation[];
  discovery: Recommendation[];
  message: string | null;
};
