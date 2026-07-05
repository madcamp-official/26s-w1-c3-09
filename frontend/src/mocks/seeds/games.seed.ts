import { games, gameRelations, resetGames } from '../db/games.db';
import type { Game } from '../../types/game';

const RAW_GAMES: Game[] = [
  { id: 'brookhaven', universeId: 1000001, placeId: 2000001, name: 'Brookhaven RP', genre: 'Roleplay', tags: ['roleplay', 'social', 'town'], playingCount: 45000, playingLabel: '45K', rating: 4.5, releasedYear: 2020, developerName: 'Wolfpaq', description: '자유로운 도시에서 집과 차를 소유하고 다양한 직업 롤플레이를 즐기는 대표 라이프 시뮬레이션 게임입니다.', thumbnailTheme: { from: '#3B3B58', to: '#1E1E36' } },
  { id: 'mm2', universeId: 1000002, placeId: 2000002, name: 'Murder Mystery 2', genre: 'Mystery', tags: ['mystery', 'competitive', 'social'], playingCount: 22000, playingLabel: '22K', rating: 4.4, releasedYear: 2014, developerName: 'Nikilis', description: '무고한 시민, 보안관, 살인자로 나뉘어 추리와 생존을 겨루는 미스터리 게임입니다.', thumbnailTheme: { from: '#2A2A3E', to: '#101018' } },
  { id: 'bloxfruits', universeId: 1000003, placeId: 2000003, name: 'Blox Fruits', genre: 'Adventure', tags: ['action', 'adventure', 'open-world'], playingCount: 55000, playingLabel: '55K', rating: 4.6, releasedYear: 2019, developerName: 'Gamer Robot Inc', description: '악마의 열매를 먹고 강해지며 바다를 탐험하는 원피스 스타일의 오픈월드 액션 RPG입니다.', thumbnailTheme: { from: '#1E5F74', to: '#133B5C' } },
  { id: 'jailbreak', universeId: 1000004, placeId: 2000004, name: 'Jailbreak', genre: 'Action', tags: ['action', 'open-world', 'competitive'], playingCount: 32000, playingLabel: '32K', rating: 4.5, releasedYear: 2017, developerName: 'Badimo', description: '경찰과 범죄자로 나뉘어 탈옥, 강도, 추격전을 벌이는 오픈월드 액션 게임입니다.', thumbnailTheme: { from: '#6B2E2E', to: '#2B1414' } },
  { id: 'arsenal', universeId: 1000005, placeId: 2000005, name: 'Arsenal', genre: 'FPS', tags: ['fps', 'action', 'competitive'], playingCount: 15000, playingLabel: '15K', rating: 4.3, releasedYear: 2015, developerName: 'ROLVe', description: '킬을 할 때마다 무기가 바뀌는 빠른 템포의 FPS 게임입니다.', thumbnailTheme: { from: '#3D3D3D', to: '#161616' } },
  { id: 'piggy', universeId: 1000006, placeId: 2000006, name: 'Piggy', genre: 'Horror', tags: ['horror', 'survival', 'puzzle'], playingCount: 8000, playingLabel: '8K', rating: 4.2, releasedYear: 2020, developerName: 'MiniToon', description: '무서운 피기에게서 도망치며 퍼즐을 풀고 탈출하는 서바이벌 호러 게임입니다.', thumbnailTheme: { from: '#8A5A6B', to: '#3E2233' } },
  { id: 'royalehigh', universeId: 1000007, placeId: 2000007, name: 'Royale High', genre: 'Roleplay', tags: ['roleplay', 'social', 'fantasy'], playingCount: 28000, playingLabel: '28K', rating: 4.4, releasedYear: 2017, developerName: 'callmehbob', description: '판타지 왕국의 학교에 다니며 꾸미기와 롤플레이를 즐기는 게임입니다.', thumbnailTheme: { from: '#7A5A8C', to: '#3A2A4A' } },
  { id: 'doors', universeId: 1000008, placeId: 2000008, name: 'Doors', genre: 'Horror', tags: ['horror', 'survival', 'coop'], playingCount: 35000, playingLabel: '35K', rating: 4.7, releasedYear: 2022, developerName: 'LSPLASH', description: '기괴한 호텔에서 문을 열며 괴물을 피해 탈출하는 협동 호러 게임입니다.', thumbnailTheme: { from: '#4A4238', to: '#1C1812' } },
  { id: 'adoptme', universeId: 1000009, placeId: 2000009, name: 'Adopt Me!', genre: 'Simulation', tags: ['simulation', 'social', 'pets'], playingCount: 60000, playingLabel: '60K', rating: 4.5, releasedYear: 2017, developerName: 'Uplift Games', description: '펫을 입양하고 키우며 집을 꾸미는 대표적인 소셜 시뮬레이션 게임입니다.', thumbnailTheme: { from: '#7BB8D9', to: '#4A7A9B' } },
  { id: 'towerofhell', universeId: 1000010, placeId: 2000010, name: 'Tower of Hell', genre: 'Obby', tags: ['obby', 'competitive', 'parkour'], playingCount: 18000, playingLabel: '18K', rating: 4.3, releasedYear: 2018, developerName: 'YXCeptional', description: '체크포인트 없이 랜덤 생성되는 타워를 오르는 하드코어 점프맵입니다.', thumbnailTheme: { from: '#C4622D', to: '#7A3416' } },
  { id: 'petsimx', universeId: 1000011, placeId: 2000011, name: 'Pet Simulator X', genre: 'Simulation', tags: ['simulation', 'pets', 'grind'], playingCount: 25000, playingLabel: '25K', rating: 4.1, releasedYear: 2021, developerName: 'BIG Games', description: '펫을 모으고 강화하며 새로운 월드를 해금하는 수집형 시뮬레이션 게임입니다.', thumbnailTheme: { from: '#8C6BB8', to: '#4A3A6B' } },
  { id: 'beeswarm', universeId: 1000012, placeId: 2000012, name: 'Bee Swarm Simulator', genre: 'Simulation', tags: ['simulation', 'grind', 'adventure'], playingCount: 12000, playingLabel: '12K', rating: 4.6, releasedYear: 2018, developerName: 'Onett', description: '벌 군단을 키워 꿀을 모으고 퀘스트를 수행하는 힐링 시뮬레이션 게임입니다.', thumbnailTheme: { from: '#C4A22D', to: '#7A6416' } },
  { id: 'bladeball', universeId: 1000013, placeId: 2000013, name: 'Blade Ball', genre: 'Action', tags: ['action', 'competitive', 'reflex'], playingCount: 40000, playingLabel: '40K', rating: 4.4, releasedYear: 2023, developerName: 'Wiggity', description: '날아오는 공을 검으로 쳐내며 최후의 1인을 가리는 반사신경 액션 게임입니다.', thumbnailTheme: { from: '#B83A5A', to: '#5C1A2E' } },
  { id: 'dahood', universeId: 1000014, placeId: 2000014, name: 'Da Hood', genre: 'Action', tags: ['action', 'competitive', 'open-world'], playingCount: 25000, playingLabel: '25K', rating: 4.2, releasedYear: 2019, developerName: 'Da Hood Entertainment', description: '오픈 월드 도시를 배경으로 한 치열한 생존 액션 게임. 갱단을 이루거나 단독으로 도시를 지배하는 전략이 필요합니다.', thumbnailTheme: { from: '#2E3A5C', to: '#141A2E' } },
  { id: 'meepcity', universeId: 1000015, placeId: 2000015, name: 'MeepCity', genre: 'Social', tags: ['social', 'roleplay', 'simulation'], playingCount: 19000, playingLabel: '19K', rating: 4.0, releasedYear: 2016, developerName: 'alexnewtron', description: '친구들과 파티를 열고 밉을 키우며 어울리는 소셜 타운 게임입니다.', thumbnailTheme: { from: '#3A5C8C', to: '#1A2E4A' } },
  { id: 'funkyfriday', universeId: 1000016, placeId: 2000016, name: 'Funky Friday', genre: 'Rhythm', tags: ['competitive', 'rhythm', 'social'], playingCount: 8000, playingLabel: '8K', rating: 4.3, releasedYear: 2021, developerName: 'Lyte Interactive', description: '상대와 리듬 배틀을 벌이는 FNF 스타일의 리듬 게임입니다.', thumbnailTheme: { from: '#6B3AB8', to: '#2E1A5C' } },
  { id: 'evade', universeId: 1000017, placeId: 2000017, name: 'Evade', genre: 'Horror', tags: ['horror', 'survival', 'action'], playingCount: 21000, playingLabel: '21K', rating: 4.5, releasedYear: 2022, developerName: 'Hexagon Dev', description: '넥스트봇에게서 도망치며 팀원과 함께 생존하는 스릴 넘치는 회피 게임입니다.', thumbnailTheme: { from: '#3E3E52', to: '#18181F' } },
  { id: 'phantomforces', universeId: 1000018, placeId: 2000018, name: 'Phantom Forces', genre: 'FPS', tags: ['fps', 'action', 'competitive'], playingCount: 10000, playingLabel: '10K', rating: 4.6, releasedYear: 2015, developerName: 'StyLiS Studios', description: '정교한 건플레이와 다양한 무기 커스터마이징을 자랑하는 본격 FPS입니다.', thumbnailTheme: { from: '#2E4A3A', to: '#12211A' } },
  { id: 'bloxburg', universeId: 1000019, placeId: 2000019, name: 'Welcome to Bloxburg', genre: 'Roleplay', tags: ['roleplay', 'simulation', 'building'], playingCount: 30000, playingLabel: '30K', rating: 4.7, releasedYear: 2016, developerName: 'Coeptus', description: '집을 짓고 직업을 가지며 일상을 살아가는 정교한 라이프 시뮬레이션입니다.', thumbnailTheme: { from: '#5A8C6B', to: '#2A4A36' } },
  { id: 'naturaldisaster', universeId: 1000020, placeId: 2000020, name: 'Natural Disaster Survival', genre: 'Survival', tags: ['survival', 'social', 'casual'], playingCount: 7000, playingLabel: '7K', rating: 4.1, releasedYear: 2011, developerName: 'Stickmasterluke', description: '매 라운드 랜덤한 자연재해에서 살아남는 클래식 서바이벌 게임입니다.', thumbnailTheme: { from: '#8C5A3A', to: '#4A2E1A' } },
  { id: 'fleethefacility', universeId: 1000021, placeId: 2000021, name: 'Flee the Facility', genre: 'Horror', tags: ['horror', 'survival', 'mystery'], playingCount: 9000, playingLabel: '9K', rating: 4.4, releasedYear: 2017, developerName: 'MrWindy', description: '야수에게 잡히지 않고 컴퓨터를 해킹해 시설을 탈출하는 비대칭 서바이벌 게임입니다.', thumbnailTheme: { from: '#4A3A5C', to: '#211A2E' } },
  { id: 'rainbowfriends', universeId: 1000022, placeId: 2000022, name: 'Rainbow Friends', genre: 'Horror', tags: ['horror', 'survival', 'coop'], playingCount: 14000, playingLabel: '14K', rating: 4.2, releasedYear: 2022, developerName: 'Roy & Charcle', description: '밤마다 찾아오는 컬러풀한 괴물들을 피해 미션을 수행하는 호러 게임입니다.', thumbnailTheme: { from: '#3A6BB8', to: '#1A2E5C' } },
  { id: 'ttd', universeId: 1000023, placeId: 2000023, name: 'Toilet Tower Defense', genre: 'Strategy', tags: ['strategy', 'grind', 'casual'], playingCount: 16000, playingLabel: '16K', rating: 4.0, releasedYear: 2023, developerName: 'Telanthric', description: '유닛을 배치해 몰려오는 적을 막는 밈 기반 타워 디펜스 게임입니다.', thumbnailTheme: { from: '#3A8C8C', to: '#1A4A4A' } },
  { id: 'emergencyhamburg', universeId: 1000024, placeId: 2000024, name: 'Emergency Hamburg', genre: 'Roleplay', tags: ['roleplay', 'action', 'town'], playingCount: 11000, playingLabel: '11K', rating: 4.5, releasedYear: 2021, developerName: 'Emergency Hamburg', description: '함부르크를 배경으로 경찰, 소방관, 시민이 되어보는 독일식 직업 롤플레이 게임입니다.', thumbnailTheme: { from: '#5C3A3A', to: '#2E1A1A' } },
];

// "People Also Join" 6개 목록 — 추천 알고리즘의 depth 1/2 그래프 탐색에 사용된다.
const RAW_RELATIONS: Record<string, string[]> = {
  brookhaven: ['meepcity', 'bloxburg', 'adoptme', 'royalehigh', 'emergencyhamburg', 'dahood'],
  mm2: ['fleethefacility', 'piggy', 'evade', 'dahood', 'bladeball', 'phantomforces'],
  bloxfruits: ['bladeball', 'dahood', 'evade', 'arsenal', 'jailbreak', 'phantomforces'],
  jailbreak: ['dahood', 'emergencyhamburg', 'evade', 'arsenal', 'bloxfruits', 'phantomforces'],
  arsenal: ['phantomforces', 'dahood', 'evade', 'bladeball', 'mm2', 'bloxfruits'],
  piggy: ['rainbowfriends', 'doors', 'fleethefacility', 'evade', 'mm2', 'naturaldisaster'],
  royalehigh: ['meepcity', 'bloxburg', 'adoptme', 'brookhaven', 'funkyfriday', 'petsimx'],
  doors: ['rainbowfriends', 'evade', 'fleethefacility', 'piggy', 'mm2', 'ttd'],
  adoptme: ['petsimx', 'meepcity', 'bloxburg', 'royalehigh', 'brookhaven', 'beeswarm'],
  towerofhell: ['evade', 'bladeball', 'funkyfriday', 'fleethefacility', 'doors', 'arsenal'],
  petsimx: ['beeswarm', 'ttd', 'adoptme', 'bloxfruits', 'meepcity', 'bladeball'],
  beeswarm: ['petsimx', 'ttd', 'adoptme', 'bloxburg', 'meepcity', 'naturaldisaster'],
  bladeball: ['bloxfruits', 'dahood', 'phantomforces', 'arsenal', 'evade', 'funkyfriday'],
  dahood: ['evade', 'phantomforces', 'jailbreak', 'bladeball', 'mm2', 'arsenal'],
  meepcity: ['bloxburg', 'brookhaven', 'royalehigh', 'adoptme', 'funkyfriday', 'naturaldisaster'],
  funkyfriday: ['meepcity', 'bladeball', 'towerofhell', 'rainbowfriends', 'evade', 'dahood'],
  evade: ['dahood', 'phantomforces', 'fleethefacility', 'rainbowfriends', 'bladeball', 'doors'],
  phantomforces: ['arsenal', 'dahood', 'bladeball', 'evade', 'jailbreak', 'mm2'],
  bloxburg: ['meepcity', 'brookhaven', 'adoptme', 'royalehigh', 'naturaldisaster', 'beeswarm'],
  naturaldisaster: ['ttd', 'evade', 'doors', 'meepcity', 'beeswarm', 'fleethefacility'],
  fleethefacility: ['mm2', 'piggy', 'evade', 'doors', 'rainbowfriends', 'dahood'],
  rainbowfriends: ['doors', 'piggy', 'evade', 'fleethefacility', 'ttd', 'naturaldisaster'],
  ttd: ['petsimx', 'beeswarm', 'naturaldisaster', 'rainbowfriends', 'bladeball', 'doors'],
  emergencyhamburg: ['brookhaven', 'jailbreak', 'dahood', 'meepcity', 'bloxburg', 'naturaldisaster'],
};

export function seedGames() {
  resetGames();
  games.push(...RAW_GAMES);
  Object.assign(gameRelations, RAW_RELATIONS);
}
