import type { Tier } from '../../types/tierlist';

export const TIER_ORDER: Tier[] = ['SSS', 'A', 'B', 'C'];

/** DB scoring.json sssMaxCount — SSS 티어는 최대 2개까지만 배치 가능 (백엔드가 400으로 거부) */
export const SSS_MAX_COUNT = 2;

/**
 * Tailwind는 소스에 "완전한 문자열"로 등장하는 클래스만 생성한다.
 * `` `bg-tier-${tier}/10` `` 처럼 런타임에 조합하면 JIT가 감지하지 못하므로,
 * 필요한 조합을 전부 정적 리터럴로 미리 나열해둔다.
 */
export const TIER_META: Record<Tier, { label: string; text: string; border: string; bgSoft: string }> = {
  SSS: { label: '최고', text: 'text-tier-sss', border: 'border-tier-sss', bgSoft: 'bg-tier-sss/10' },
  A: { label: '훌륭함', text: 'text-tier-a', border: 'border-tier-a', bgSoft: 'bg-tier-a/10' },
  B: { label: '보통', text: 'text-tier-b', border: 'border-tier-b', bgSoft: 'bg-tier-b/10' },
  C: { label: '별로', text: 'text-tier-c', border: 'border-tier-c', bgSoft: 'bg-tier-c/10' },
};

export const MIN_ENTRIES_FOR_RECOMMEND = 3;
