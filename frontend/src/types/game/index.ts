/**
 * ERD의 games 테이블에 대응하는 프론트엔드 모델.
 * 실제 썸네일 이미지 대신 그라데이션 테마로 목업한다 (Roblox 썸네일 API 연동 전).
 */
export type GameThumbnailTheme = {
  from: string;
  to: string;
};

export type Game = {
  id: string;
  universeId: number;
  placeId: number;
  name: string;
  genre: string;
  tags: string[];
  playingCount: number;
  playingLabel: string;
  rating: number;
  releasedYear: number;
  developerName: string;
  description: string;
  thumbnailTheme: GameThumbnailTheme;
};

/** ERD의 game_videos 테이블에 대응 — YouTube 쇼츠 캐시 */
export type GameVideo = {
  youtubeVideoId: string;
  title: string;
  viewsLabel: string;
};

export type GameDetailResponse = {
  game: Game;
  screenshots: string[]; // 실제 이미지 URL (캐러셀). 없으면 빈 배열 → 히어로는 그라데이션 폴백
  relatedGames: Game[];
  videos: GameVideo[];
};
