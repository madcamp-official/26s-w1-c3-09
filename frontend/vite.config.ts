import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import svgr from 'vite-plugin-svgr';

export default defineConfig({
  plugins: [
    svgr({
      svgrOptions: {
        icon: true,
      },
    }),
    react(),
    tailwindcss(),
  ],
  resolve: { alias: { '@': '/src' } },
  server: {
    port: 5173,
    // /api는 로컬 Spring Boot로 프록시 (README: FE :5173 → BE :8080)
    proxy: { '/api': 'http://localhost:8080' },
  },
  build: { outDir: 'dist', assetsDir: 'assets', emptyOutDir: true, sourcemap: true },
});
