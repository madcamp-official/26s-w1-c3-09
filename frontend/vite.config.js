import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // 프론트엔드에서 /api로 시작하는 요청은 백엔드로 주소를 바꿔서 보냅니다.
      '/api': {
        target: 'http://localhost:8080', // Spring Boot 기본 포트
        changeOrigin: true,
        secure: false,
      }
    }
  }
})
