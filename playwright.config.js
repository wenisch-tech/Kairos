import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './src/test/browser',
  fullyParallel: false,
  workers: 1,
  timeout: 30_000,
  use: {
    baseURL: 'http://127.0.0.1:18080',
    viewport: { width: 1440, height: 1000 },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure'
  },
  webServer: {
    command: 'node scripts/start-ui-test.mjs',
    url: 'http://127.0.0.1:18080/login',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000
  }
});
