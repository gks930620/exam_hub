import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// 테스트 전용 설정. vite.config.ts 와 분리한 이유는 dev 프록시 설정이 테스트에 필요 없고,
// 섞으면 loadEnv 가 테스트 실행 시에도 돌아 불필요한 의존이 생기기 때문이다.
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
  },
});
