// @ts-check
const { defineConfig, devices } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './tests/e2e',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  // Un seul worker (VT-210b) : tous les scenarios font reset -> import contre le meme backend et la
  // meme base. Sans cela, plusieurs fichiers *.spec.js s'executent en parallele et se marchent dessus
  // (StaleObjectStateException sur bank_import, budget par defaut relu a la place du dataset).
  // Ne pas augmenter tant que l'etat n'est pas isole par worker.
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'tests/e2e/report' }]],
  use: {
    baseURL: 'http://localhost:3000',
    headless: true,
    viewport: { width: 1280, height: 900 },
    ignoreHTTPSErrors: true,
    trace: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: undefined,
});
