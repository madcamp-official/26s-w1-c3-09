import { createBrowserRouter } from 'react-router-dom';
import RootLayout from '../layouts/RootLayout';
import { favoritesRoutes } from './routes/favoritesRoutes';
import { tierlistRoutes } from './routes/tierlistRoutes';
import { recommendRoutes } from './routes/recommendRoutes';
import { gameRoutes } from './routes/gameRoutes';
import { notFoundRoutes } from './routes/notFoundRoutes';

export const router = createBrowserRouter([
  {
    element: <RootLayout />,
    children: [
      ...favoritesRoutes,
      ...tierlistRoutes,
      ...recommendRoutes,
      ...gameRoutes,
      ...notFoundRoutes,
    ],
  },
]);
