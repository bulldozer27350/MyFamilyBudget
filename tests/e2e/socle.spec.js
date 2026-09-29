'use strict';

// VT-000 : verification du socle commun (helpers reset/import + nettoyage navigateur).
// Ne teste aucun comportement metier : uniquement que le mecanisme de remise a
// l etat de reference fonctionne, pour que les scenarios VT-1xx/2xx/5xx puissent
// s appuyer dessus.

const { test, expect } = require('@playwright/test');
const {
  API,
  FRONT,
  waitForReactMount,
  resetBackendState,
  resetToReferenceState,
  clearBrowserState,
} = require('./helpers');

test.describe('Socle de test commun (VT-000)', () => {
  test('resetBackendState retourne un BudgetDataDto', async ({ request }) => {
    const body = await resetBackendState(request);
    expect(body).toHaveProperty('settings');
  });

  test('resetToReferenceState charge le dataset de reference dans le backend', async ({ request }) => {
    const imported = await resetToReferenceState(request);
    expect(imported).toHaveProperty('settings');

    const res = await request.get(API + '/budget');
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.incomes.map(i => i.label)).toContain('Salaire');
    expect(body.charges.map(c => c.label)).toContain('Loyer');
    expect(body.placements.map(p => p.label)).toContain('PEA');
  });

  test('resetToReferenceState est idempotent', async ({ request }) => {
    await resetToReferenceState(request);
    await resetToReferenceState(request);
    const res = await request.get(API + '/budget');
    const body = await res.json();
    expect(body.incomes.filter(i => i.label === 'Salaire')).toHaveLength(1);
  });

  test('clearBrowserState vide localStorage et sessionStorage', async ({ page }) => {
    await page.goto(FRONT + '/overview.html');
    await waitForReactMount(page);

    await page.evaluate(() => {
      window.localStorage.setItem('budget_familial_data_v1', '{"marker":true}');
      window.sessionStorage.setItem('vt000_marker', '1');
    });

    await clearBrowserState(page);

    const state = await page.evaluate(() => ({
      local: window.localStorage.getItem('budget_familial_data_v1'),
      session: window.sessionStorage.getItem('vt000_marker'),
      localLength: window.localStorage.length,
    }));
    expect(state.local).toBeNull();
    expect(state.session).toBeNull();
    expect(state.localLength).toBe(0);
  });
});
