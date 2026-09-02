import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// CSR 개발 서버. /api 요청은 Spring 백엔드로 프록시 → CORS 회피.
// 로컬 백엔드 기본 포트는 8081(application.yml 과 맞춤 — 8080 은 다른 프로젝트가 자주 쓴다).
// 다른 포트로 띄웠으면 frontend/.env 에 VITE_API_TARGET=http://localhost:9090 처럼 적는다.
// ⚠️ loadEnv 로 읽어야 한다 — Vite 는 .env 를 process.env 에 넣지 않으므로
//    process.env.VITE_API_TARGET 은 항상 undefined 였고, 조용히 엉뚱한 포트로 프록시됐다.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const target = env.VITE_API_TARGET || 'http://localhost:8081';

  return {
    plugins: [react()],
    server: {
      port: 5173,
      // 디자인 킷이 프런트 루트 밖(프로젝트 루트)에 있다 — 개발 서버가 거기까지 읽게 허용
      fs: { allow: ['..'] },
      proxy: {
        '/api': { target, changeOrigin: true },
      },
    },
  };
});
