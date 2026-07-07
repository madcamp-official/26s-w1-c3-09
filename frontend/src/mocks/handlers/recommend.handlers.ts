import { http, HttpResponse, delay } from 'msw';
import { computeRecommendations } from '../selectors/recommend.selectors';
import type { TierEntryPayload } from '../../types/recommend';

export const recommendHandlers = [
  // 실서버 경로는 POST /api/recommend (단수). 응답은 sections{popular, discovery}
  http.post('/api/recommend', async ({ request }) => {
    await delay(700); // AI 분석 중인 척 하는 지연

    const body = (await request.json()) as { entries?: TierEntryPayload[] };
    const entries = body?.entries ?? [];

    if (entries.length === 0) {
      return HttpResponse.json(
        { error: 'NO_TIER', message: '티어에 배치된 게임이 없습니다.' },
        { status: 404 },
      );
    }

    return HttpResponse.json({ sections: computeRecommendations(entries) });
  }),
];