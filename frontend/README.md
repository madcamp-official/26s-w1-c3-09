# RBX Tierlist FE

로블록스 즐겨찾기 게임을 S/A/B/C 티어표로 정리하고, 티어 배치를 기반으로 새 게임을 추천받는 서비스의 프론트엔드입니다.

`zup-zup-fe` 프로젝트의 폴더 구조와 기술 스택을 그대로 재사용하되, 도메인 내용은 완전히 새로 구성했습니다.

## 스택

Vite + React 19 + TypeScript(strict) + Tailwind v4(CSS 기반 테마) + Zustand + TanStack Query + React Router v7 + MSW + Vitest

## 시작하기

```bash
npm install
npx msw init public/ --save   # 최초 1회, public/mockServiceWorker.js 생성
npm run dev
```

백엔드가 없으므로 `src/main.tsx`가 개발 모드에서 MSW 워커를 먼저 기동한 뒤 앱을 렌더링합니다. `src/mocks/seeds`에 시드된 데모 닉네임(`김치`, `robloxlover`, `tester`)으로 즉시 조회를 테스트할 수 있습니다.

## 폴더 구조 & 레이어링

```
types/{도메인}       요청·응답 타입
constants/{도메인}    문자열·클래스 상수
api/{도메인}          fetch 함수 (index.ts) + useQuery 훅 (hooks/)
store/slices          zustand 슬라이스
store/hooks           슬라이스 셀렉터 훅
hooks/{도메인}         라우팅·조회·상태를 조합하는 컨테이너 훅
component/{도메인}     프레젠테이션 컴포넌트
pages/{도메인}         라우트 컨테이너 (훅을 컴포넌트에 연결)
router/routes          도메인별 RouteObject[]
mocks/db               인메모리 "테이블" (배열/맵)
mocks/seeds             db에 초기 데이터를 채우는 함수
mocks/selectors         db를 조회하는 순수 함수
mocks/handlers           selectors를 HTTP 라우트로 연결
```

## 설계 결정 (2025-xx-xx 논의 기준)

- **완전히 새 프로젝트**로 시작 — zup-zup-fe와 도메인은 무관하며 구조·스택만 재사용
- **닉네임 검색(`/`) / 티어표(`/tierlist`) 페이지는 독립 라우트**로 분리 (Wizard로 묶지 않음). 상태 공유가 필요한 부분(`nickname`)은 Zustand `store/favoritesStore`가 담당하고, 서버 데이터(즐겨찾기 목록)는 TanStack Query 캐시가 `['favorites', nickname]` 키로 보관 — 같은 닉네임이면 페이지를 이동해도 재요청 없이 캐시를 재사용합니다.
- **MSW로 목업 API 서버 구성** — `mocks/db`(인메모리 테이블) → `mocks/seeds`(초기 데이터) → `mocks/selectors`(조회 로직) → `mocks/handlers`(HTTP 라우트) 계층을 zup-zup-fe와 동일하게 유지

## 지금까지 구현된 것 (1단계: 공통 인프라 + page1)

- 전역 셸: `RootLayout`(헤더 + Suspense), `Header`(로고 + 스텝 내비 + 닉네임 배지)
- `favorites` 도메인: 닉네임으로 즐겨찾기 조회 (`GET /api/users/:nickname/favorites`), 404 처리, 조회 성공 시 `/tierlist`로 자동 이동
- 게임 카탈로그 24종 + "People Also Join" 연관 관계를 `mocks/seeds/games.seed.ts`에 시드 (추후 티어표·추천 도메인이 재사용)

## 다음 단계 (2단계 예정)

- `tierlist` 도메인: 즐겨찾기 목록을 S/A/B/C 티어로 드래그앤드롭 배치
- `recommend` 도메인: 티어 가중치 × depth 가중치 기반 추천 알고리즘
- `game` 도메인: 게임 상세 + 관련 게임 + (추후) YouTube 쇼츠 연동
