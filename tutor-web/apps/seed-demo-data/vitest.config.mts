import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  resolve: {
    alias: {
      '@tutor-web/e2e-support': fileURLToPath(
        new URL('../../libs/e2e-support/src/index.ts', import.meta.url),
      ),
    },
  },
  test: {
    include: ['src/**/*.spec.ts'],
    coverage: {
      enabled: true,
      provider: 'v8',
      include: ['src/api-client.ts', 'src/demo-seed.ts'],
      reportsDirectory: '../../coverage/seed-demo-data',
      reporter: ['lcov', 'text-summary'],
      thresholds: { statements: 90, lines: 90, branches: 80 },
    },
  },
});
