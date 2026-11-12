import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './tests',
  testMatch: '*.spec.js',
  fullyParallel: false,
  workers: 1,
  timeout: 45000,
  use: { baseURL: 'http://127.0.0.1:18765', trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  webServer: {
    command:
      (process.platform === 'win32' ? '../../../gradlew.bat' : '../../../gradlew') +
      ' -p ../../.. -PincludeTestProjects=false --console=plain :meteor-console:runBrowserFixture',
    wait: { stdout: /METEOR_CONSOLE_READY/ },
    timeout: 120000,
    gracefulShutdown: { signal: 'SIGTERM', timeout: 10000 }
  }
});
