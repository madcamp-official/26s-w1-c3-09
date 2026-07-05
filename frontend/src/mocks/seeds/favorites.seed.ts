import { favoritesByNickname, resetFavorites } from '../db/favorites.db';

const KIMCHI_FAVORITES = [
  'brookhaven', 'mm2', 'bloxfruits', 'jailbreak', 'arsenal', 'piggy',
  'royalehigh', 'doors', 'adoptme', 'towerofhell', 'petsimx', 'beeswarm',
];

export function seedFavorites() {
  resetFavorites();

  favoritesByNickname['김치'.toLowerCase()] = {
    nickname: '김치',
    displayName: '김치',
    gameIds: KIMCHI_FAVORITES,
  };

  favoritesByNickname['robloxlover'.toLowerCase()] = {
    nickname: 'robloxlover',
    displayName: 'RobloxLover',
    gameIds: ['adoptme', 'bloxfruits', 'doors', 'bladeball', 'evade', 'dahood', 'arsenal', 'mm2'],
  };

  favoritesByNickname['tester'.toLowerCase()] = {
    nickname: 'tester',
    displayName: 'Tester',
    gameIds: ['brookhaven', 'royalehigh', 'adoptme'],
  };

  // 즐겨찾기가 하나도 없는 사용자 — 빈 보드 + 안내 문구 UX 확인용
  favoritesByNickname['newbie'.toLowerCase()] = {
    nickname: 'newbie',
    displayName: 'Newbie',
    gameIds: [],
  };
}
