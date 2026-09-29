'use strict';

// VT-200 : mode Playwright sans fallback JS + contexte vierge.
// Verifie le mecanisme (override reserve aux tests), pas un comportement metier.

const { test, expect } = require('@playwright/test');
const {
  FRONT,
  resetToReferenceState,
  waitForReactMount,
  disableJsFallback,
  readJsFallbackFlag,
  gotoAndExpectBackend,
  expectFreshBrowserState,
} = require('./helpers');

test.describe('Mode sans fallback JS (VT-200)', () => {
  test.beforeEach(async ({ request }) => {
    await resetToReferenceState(request);
  });

  test('sans override, DISABLE_JS_FALLBACK reste a false (valeur de production)', async ({ page }) => {
    await page.goto(FRONT + '/overview.html');
    await waitForReactMount(page);
    expect(await readJsFallbackFlag(page)).toBe(false);
  });

  test('avec override installe avant les scripts, DISABLE_JS_FALLBACK vaut true', async ({ page }) => {
    await disableJsFallback(page);
    await page.goto(FRONT + '/overview.html');
    await waitForReactMount(page);
    expect(await readJsFallbackFlag(page)).toBe(true);
  });

  test('contexte vierge : localStorage vide au premier chargement', async ({ page }) => {
    await disableJsFallback(page);
    await page.goto(FRONT + '/overview.html');
    await waitForReactMount(page);
    await expectFreshBrowserState(page);
  });

  test('gotoAndExpectBackend attend une lecture backend reelle', async ({ page }) => {
    await disableJsFallback(page);
    const res = await gotoAndExpectBackend(page, '/overview.html', '/overview', 'GET');
    expect(res.status()).toBe(200);
    expect(res.url()).toContain('/overview');
  });

  test('backend en echec : l erreur remonte au lieu du fallback local', async ({ page }) => {
    const errors = [];
    page.on('console', msg => {
      if (msg.type() === 'error') errors.push(msg.text());
    });
    await disableJsFallback(page);
    await page.route('**/api/v1/overview**', route => route.abort());
    await page.goto(FRONT + '/overview.html');
    await waitForReactMount(page);

    await expect
      .poll(() => errors.some(text => text.includes('[DISABLE_JS_FALLBACK]')))
      .toBe(true);
  });
});
