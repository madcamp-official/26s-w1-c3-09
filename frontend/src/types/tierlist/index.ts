/** DB scoring.json의 tierWeights 키와 1:1 — SSS(5.5)/A(3)/B(2)/C(1) */
export type Tier = 'SSS' | 'A' | 'B' | 'C';

/** tier_entries 테이블에 대응 — 티어별 게임 id 배열 (배열 내 순서 = position) */
export type TierBoard = Record<Tier, string[]>;

/** 서버 저장 단위 — tier_entries 한 행 */
export type TierEntry = { gameId: string; tier: Tier };

/** entries=보드 복원용(gameId+tier), games=티어 카드 렌더용 게임 데이터(이름·아이콘) */
export type TierListResponse = { entries: TierEntry[]; games: import('../game').Game[] };
