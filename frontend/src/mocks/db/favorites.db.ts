export type FavoritesRecord = {
  nickname: string;
  displayName: string;
  gameIds: string[];
};

/** key는 소문자로 정규화한 닉네임 */
export const favoritesByNickname: Record<string, FavoritesRecord> = {};

export function resetFavorites() {
  Object.keys(favoritesByNickname).forEach((key) => delete favoritesByNickname[key]);
}
