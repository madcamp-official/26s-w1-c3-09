import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './index.css';
import App from './App';

/**
 * 기본은 실제 백엔드(/api → :8080 프록시)와 통신한다.
 * 백엔드 없이 목업으로 개발할 때만 .env에 VITE_ENABLE_MSW=true를 켠다.
 * 워커가 등록되기 전에 첫 API 요청이 나가면 목업이 가로채지 못하므로 순서를 지킨다.
 */
async function enableMocking() {
  if (!import.meta.env.DEV || import.meta.env.VITE_ENABLE_MSW !== 'true') return;
  const { worker } = await import('./mocks/browser');
  await worker.start({ onUnhandledRequest: 'bypass', quiet: true });
}

enableMocking().then(() => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <App />
    </StrictMode>,
  );
});
