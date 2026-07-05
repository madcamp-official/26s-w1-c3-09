import { http, HttpResponse, delay } from 'msw';
import { tierListsByNickname } from '../db/tierlists.db';
import type { TierEntry } from '../../types/tierlist';

const key = (nickname: string) => nickname.trim().toLowerCase();

export const tierlistHandlers = [
  http.get('/api/users/:nickname/tier-list', async ({ params }) => {
    await delay(150);
    const entries = tierListsByNickname[key(params.nickname as string)] ?? [];
    return HttpResponse.json({ entries });
  }),

  http.put('/api/users/:nickname/tier-list', async ({ params, request }) => {
    await delay(150);
    const body = (await request.json()) as { entries?: TierEntry[] };
    const entries = body?.entries ?? [];
    tierListsByNickname[key(params.nickname as string)] = entries;
    return HttpResponse.json({ entries });
  }),
];
