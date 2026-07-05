import { http, HttpResponse, delay } from 'msw';
import { getGameDetail } from '../selectors/gameDetail.selectors';

export const gamesHandlers = [
  http.get('/api/games/:gameId', async ({ params }) => {
    await delay(300);

    const gameId = params.gameId as string;
    const result = getGameDetail(gameId);

    if (!result.ok) {
      return HttpResponse.json(
        { title: '게임 조회 오류', detail: '해당 게임을 찾을 수 없습니다.', instance: `/api/games/${gameId}` },
        { status: 404 },
      );
    }

    return HttpResponse.json(result.data);
  }),
];
