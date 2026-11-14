import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    include: ['src/**/*.spec.ts'],
    environment: 'node',
    coverage: {
      enabled: true,
      provider: 'v8',
      include: ['src/**/*.ts'],
      exclude: ['src/**/*.spec.ts', 'src/index.ts'],
      reportsDirectory: '../../coverage/e2e-support',
      reporter: ['lcov', 'text-summary'],
      thresholds: { statements: 90, lines: 90, branches: 80 },
    },
  },
});
