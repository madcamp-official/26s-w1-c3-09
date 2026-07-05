import { http, HttpResponse, delay } from 'msw';
import { getFavoritesByNickname } from '../selectors/favorites.selectors';

export const favoritesHandlers = [
  http.get('/api/users/:nickname/favorites', async ({ params }) => {
    await delay(500); // 실제 조회를 흉내내기 위한 인위적 지연 (로딩 스피너 확인용)

    const nickname = params.nickname as string;
    const result = getFavoritesByNickname(nickname);

    if (!result.ok) {
      return HttpResponse.json(
        {
          title: '닉네임 조회 오류',
          detail: '입력하신 닉네임의 로블록스 사용자를 찾을 수 없습니다.',
          instance: `/api/users/${nickname}/favorites`,
        },
        { status: 404 },
      );
    }

    return HttpResponse.json(result.data);
  }),
];
