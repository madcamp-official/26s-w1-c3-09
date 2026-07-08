import type { ApiError } from '../../types/common';
import type { Game, GameThumbnailTheme } from '../../types/game';
import type { Tier, TierEntry } from '../../types/tierlist';

/**
 * 백엔드(madfinder Spring Boot) 실제 응답 ↔ FE 타입 변환 계층.
 * 백엔드는 userId(로블록스 숫자 ID) 기준으로 동작하고, FE는 닉네임 기준으로 동작한다.
 * GET /api/users/{username}/favorites 응답의 userId를 세션 캐시에 담아 두고
 * 티어 저장(PUT /api/tiers)·추천(POST /api/recommend)에 사용한다.
 */

const BASE_URL = import.meta.env.VITE_API_BASE_URL;

/* ---------- 백엔드 DTO (docs/specs/백엔드-api-명세.md 와 1:1) ---------- */

export type BackendFavoriteGame = { universeId: number; name: string | null; iconUrl: string | null };

export type BackendTierEntry = {
  universeId: number;
  tier: Tier;
  position: number | null;
  name?: string | null;
  iconUrl?: string | null;
};

export type BackendUserFavorites = {
  userId: number;
  username: string;
  favorites: BackendFavoriteGame[];
  favoritesEmpty: boolean;
  savedTier: BackendTierEntry[] | null;
};

export type BackendRecommendItem = {
  rank: number;
  universeId: number;
  name: string;
  genreL1: string | null;
  genreL2: string | null;
  score: number;
  playerCount: number | null;
  iconUrl: string | null;
};

export type BackendGameDetail = {
  universeId: number;
  name: string;
  description: string | null;
  genreL1: string | null;
  genreL2: string | null;
  playing: number | null;
  visits: number | null;
  upVotes: number | null;
  downVotes: number | null;
  minimumAge: number | null;
  releasedYear: number | null;
  screenshots: string[] | null;
  iconUrl: string | null;
  videoUrl: string | null;
  robloxUrl: string | null;
};

export type BackendVideo = { youtubeVideoId: string; title: string; thumbnailUrl: string | null };

/* ---------- 에러 변환: 백엔드 {error, message} → FE ApiError ---------- */

export function toApiError(
  status: number,
  data: unknown,
  instance: string,
  fallbackTitle: string,
  fallbackDetail: string,
): ApiError {
  const d = data as { error?: string; message?: string; title?: string; detail?: string } | undefined;
  return {
    status,
    title: d?.title ?? d?.error ?? fallbackTitle,
    detail: d?.detail ?? d?.message ?? fallbackDetail,
    instance,
  };
}

/* ---------- 표시용 포맷 ---------- */

export function formatCount(n: number | null | undefined): string {
  const v = n ?? 0;
  if (v >= 1_000_000) return `${(v / 1_000_000).toFixed(1).replace(/\.0$/, '')}M`;
  if (v >= 1_000) return `${Math.round(v / 1_000)}K`;
  return String(v);
}

/** 로블록스 votes → 별점 5점 만점 환산 (소수 1자리) */
export function votesToRating(up: number | null | undefined, down: number | null | undefined): number {
  const u = up ?? 0;
  const d = down ?? 0;
  if (u + d === 0) return 0;
  return Math.round((u / (u + d)) * 5 * 10) / 10;
}

/** 실제 썸네일 URL이 오기 전까지 쓰는 그라데이션 — universeId로 결정적으로 선택 */
const THEME_PALETTE: GameThumbnailTheme[] = [
  { from: '#3B3B58', to: '#1E1E36' },
  { from: '#1E5F74', to: '#133B5C' },
  { from: '#6B2E2E', to: '#2B1414' },
  { from: '#7A5A8C', to: '#3A2A4A' },
  { from: '#4A4238', to: '#1C1812' },
  { from: '#7BB8D9', to: '#4A7A9B' },
  { from: '#C4622D', to: '#7A3416' },
  { from: '#8C6BB8', to: '#4A3A6B' },
  { from: '#2E3A5C', to: '#141A2E' },
  { from: '#3A5C8C', to: '#1A2E4A' },
  { from: '#2E4A3A', to: '#12211A' },
  { from: '#B83A5A', to: '#5C1A2E' },
];

export function themeFor(universeId: number): GameThumbnailTheme {
  return THEME_PALETTE[Math.abs(universeId) % THEME_PALETTE.length];
}

/* ---------- 닉네임 → userId 세션 캐시 ---------- */

const userIdByNickname = new Map<string, number>();

/**
 * refresh=true는 로블록스 재조회(F-3 새로고침) — 닉네임 검색 시에만 사용한다.
 * (games 테이블이 배치로 채워지기 전에는 캐시 응답의 name이 null이라, 검색 시 실조회로 이름을 확보)
 */
export async function fetchUserFavoritesRaw(
  nickname: string,
  refresh = false,
): Promise<BackendUserFavorites> {
  const path = `/users/${encodeURIComponent(nickname)}/favorites${refresh ? '?refresh=true' : ''}`;
  const res = await fetch(BASE_URL + path);
  const data = await res.json().catch(() => undefined);
  if (!res.ok) {
    throw toApiError(
      res.status,
      data,
      `/api${path}`,
      '닉네임 조회 오류',
      '입력하신 닉네임의 사용자를 찾을 수 없습니다.',
    );
  }
  const raw = data as BackendUserFavorites;
  userIdByNickname.set(nickname, raw.userId);
  return raw;
}

/** userId 확보 — 캐시에 없으면 favorites 조회로 해석 (닉네임 검색을 거치면 항상 캐시됨) */
export async function resolveUserId(nickname: string): Promise<number> {
  const cached = userIdByNickname.get(nickname);
  if (cached != null) return cached;
  const raw = await fetchUserFavoritesRaw(nickname);
  return raw.userId;
}

/* ---------- 백엔드 → FE Game 변환 ---------- */

export function favoriteToGame(dto: BackendFavoriteGame): Game {
  return {
    id: String(dto.universeId),
    universeId: dto.universeId,
    placeId: 0,
    name: dto.name ?? `게임 #${dto.universeId}`,
    genre: '',
    tags: [],
    playingCount: 0,
    playingLabel: '',
    rating: 0,
    releasedYear: 0,
    developerName: '',
    description: '',
    iconUrl: dto.iconUrl,
    thumbnailTheme: themeFor(dto.universeId),
  };
}

/** 백엔드 savedTier → FE TierEntry[] (position 순 정렬) */
export function savedTierToEntries(savedTier: BackendTierEntry[] | null): TierEntry[] {
  return [...(savedTier ?? [])]
    .sort((a, b) => (a.position ?? 0) - (b.position ?? 0))
    .map((e) => ({ gameId: String(e.universeId), tier: e.tier }));
}

/**
 * 백엔드 savedTier → FE Game[] (배치 풀 재료).
 * 검색으로 담은 게임은 메모리라 새로고침 시 사라지므로, 저장된 티어 게임의 이름·아이콘을
 * 여기서 풀에 공급해 재방문 시에도 티어 카드가 렌더되고 개수도 맞게 한다.
 */
export function savedTierToGames(savedTier: BackendTierEntry[] | null): Game[] {
  return (savedTier ?? []).map((e) => ({
    id: String(e.universeId),
    universeId: e.universeId,
    placeId: 0,
    name: e.name ?? `게임 #${e.universeId}`,
    genre: '',
    tags: [],
    playingCount: 0,
    playingLabel: '',
    rating: 0,
    releasedYear: 0,
    developerName: '',
    description: '',
    iconUrl: e.iconUrl,
    thumbnailTheme: themeFor(e.universeId),
  }));
}
