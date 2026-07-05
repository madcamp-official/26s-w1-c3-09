import { http, HttpResponse, delay } from 'msw';
import { computeRecommendations } from '../selectors/recommend.selectors';
import type { TierEntryPayload } from '../../types/recommend';

export const recommendHandlers = [
  http.post('/api/recommendations', async ({ request }) => {
    await delay(700); // AI 분석 중인 척 하는 지연

    const body = (await request.json()) as { entries?: TierEntryPayload[] };
    const entries = body?.entries ?? [];

    if (entries.length === 0) {
      return HttpResponse.json(
        { title: '추천 오류', detail: '티어에 배치된 게임이 없습니다.', instance: '/api/recommendations' },
        { status: 400 },
      );
    }

    return HttpResponse.json({ recommendations: computeRecommendations(entries) });
  }),
];
