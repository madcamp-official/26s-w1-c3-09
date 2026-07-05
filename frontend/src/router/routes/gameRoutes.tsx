import { RouteObject } from 'react-router-dom';
import GameDetailPage from '../../pages/game/GameDetailPage';

export const gameRoutes: RouteObject[] = [{ path: 'games/:gameId', element: <GameDetailPage /> }];
