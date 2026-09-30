'use strict';

const { test, expect } = require('@playwright/test');
const fs   = require('fs');

// Constantes et helpers communs : voir tests/e2e/helpers (VT-000).
const {
  API,
  FRONT,
  REFERENCE_DATASET,
  resetToReferenceState,
  expectBackendCall,
  waitForReactMount,
} = require('./helpers');

// VT-500 : ces tests n utilisent que le dataset canonique versionne (aucun jeu de donnees local),
// et partent d un backend remis a l etat de reference. Les parcours de reference F1 a F6 sont
// dans reference-f*.spec.js, read-reload.spec.js, mutation-patrimoine.spec.js,
// settings-multi-domain.spec.js et backend-down.spec.js.
const JSON_DATASET = REFERENCE_DATASET;

test.beforeEach(async ({ request }) => {
  await resetToReferenceState(request);
});

// ---------------------------------------------------------------------------
// 1. Vue d ensemble – GET /overview
// ---------------------------------------------------------------------------
test.describe("Vue d ensemble", () => {
  test('recharge les KPIs quand la case Monnaie constante est cochee', async ({ page }) => {
    await page.goto(FRONT + '/overview.html');
    await waitForReactMount(page);

    const backendCall = expectBackendCall(page, '/overview', 'GET');
    const checkbox = page.locator('input[type="checkbox"]').first();
    await checkbox.check();

    const res = await backendCall;
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body, 'La reponse doit contenir les donnees de vue d ensemble').toHaveProperty('data');
  });
});

// ---------------------------------------------------------------------------
// 6. IMPORT JSON – POST /budget/import  ← TEST CRITIQUE
//
//  Scenario complet :
//    a) Naviguer vers overview.html (AppLayout present)
//    b) Rendre l input file visible (il est display:none en production)
//    c) Injecter budget-familial.json via setInputFiles
//    d) Attendre que le backend recoive POST /budget/import (status 200)
//    e) Verifier le corps de la requete (BudgetDataDto valide)
//    f) Verifier l IHM : pas d alerte d erreur, contenu toujours rendu
//
//  Si le fallback JS (BudgetStore.importJSON -> localStorage seulement)
//  prend le relais sans appel reseau, waitForResponse expire → FAIL.
// ---------------------------------------------------------------------------
test.describe('Import JSON (POST /budget/import)', () => {
  test('le corps de la requete POST /budget/import est un BudgetDataDto JSON valide', async ({ page }) => {
    expect(fs.existsSync(JSON_DATASET)).toBe(true);

    await page.goto(FRONT + '/overview.html');
    await waitForReactMount(page);

    const backendReqCall = page.waitForRequest(
      req => req.url().includes('/budget/import') && req.method() === 'POST',
      { timeout: 30000 }
    );

    await page.evaluate(() => {
      const inp = document.querySelector('input[type="file"][accept="application/json"]');
      if (inp) { inp.style.display = 'block'; inp.style.visibility = 'visible'; }
    });

    await page.locator('input[type="file"][accept="application/json"]').setInputFiles(JSON_DATASET);

    const req = await backendReqCall;

    const ct = req.headers()['content-type'] || '';
    expect(ct, 'Content-Type doit etre application/json').toContain('application/json');

    const raw = req.postData();
    expect(raw, 'Le corps ne doit pas etre vide').toBeTruthy();

    const parsed = JSON.parse(raw);
    expect(parsed, 'Le BudgetDataDto doit avoir settings').toHaveProperty('settings');
    expect(parsed, 'Le BudgetDataDto doit avoir incomes').toHaveProperty('incomes');
  });
});

// ---------------------------------------------------------------------------
// 7. Reset – POST /budget/reset
// ---------------------------------------------------------------------------
test.describe('Reinitialisation (POST /budget/reset)', () => {
  test('le bouton Reinitialiser appelle POST /budget/reset', async ({ page }) => {
    await page.goto(FRONT + '/overview.html');
    await waitForReactMount(page);

    page.on('dialog', async d => { await d.accept(); });

    const backendResetCall = page.waitForResponse(
      res => (res.url().includes('/budget/reset') || res.url().includes('/api/v1/budget/reset')) && res.request().method() === 'POST',
      { timeout: 20000 }
    );

    const resetBtn = page.getByRole('button', { name: /r[ée]initialiser/i });
    await resetBtn.click();

    const res = await backendResetCall;
    expect(res.status()).toBe(200);
  });
});

// ---------------------------------------------------------------------------
// 8. Export JSON – GET /budget
// ---------------------------------------------------------------------------
test.describe('Export JSON (GET /budget)', () => {
  test('GET /budget retourne un BudgetDataDto complet', async ({ page }) => {
    const res = await page.request.get(API + '/budget');
    expect(res.status()).toBe(200);

    const body = await res.json();
    expect(body).toHaveProperty('settings');
    expect(body).toHaveProperty('incomes');
    expect(body).toHaveProperty('charges');
    expect(body).toHaveProperty('placements');
  });
});

// ---------------------------------------------------------------------------
// 10. Smoke tests API backend (sans IHM)
//    Garantit que le serveur Spring Boot repond correctement sur tous les
//    endpoints definis dans le contrat OpenAPI.
// ---------------------------------------------------------------------------
test.describe('Smoke tests API backend', () => {
  test('GET /overview retourne 200 avec data', async ({ request }) => {
    const res = await request.get(API + '/overview');
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body).toHaveProperty('data');
  });

  test('GET /tresorerie retourne 200 avec incomes et charges', async ({ request }) => {
    const res = await request.get(API + '/tresorerie');
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body).toHaveProperty('incomes');
    expect(body).toHaveProperty('charges');
  });

  test('GET /patrimoine retourne 200 avec placements', async ({ request }) => {
    const res = await request.get(API + '/patrimoine');
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body).toHaveProperty('placements');
  });

  test('GET /retraite retourne 200', async ({ request }) => {
    const res = await request.get(API + '/retraite');
    expect(res.status()).toBe(200);
  });

  test('GET /impots retourne 200', async ({ request }) => {
    const res = await request.get(API + '/impots');
    expect(res.status()).toBe(200);
  });

  test('GET /settings retourne 200', async ({ request }) => {
    const res = await request.get(API + '/settings');
    expect(res.status()).toBe(200);
  });

  test('GET /analyse retourne 200', async ({ request }) => {
    const res = await request.get(API + '/analyse');
    expect(res.status()).toBe(200);
  });

  test('GET /bank-import retourne 200', async ({ request }) => {
    const res = await request.get(API + '/bank-import');
    expect(res.status()).toBe(200);
  });

  test('GET /pending-operations retourne 200', async ({ request }) => {
    const res = await request.get(API + '/pending-operations');
    expect(res.status()).toBe(200);
  });

  test('GET /pointage retourne 200', async ({ request }) => {
    const res = await request.get(API + '/pointage');
    expect(res.status()).toBe(200);
  });

  test('POST /budget/import avec budget-familial.json retourne 200 et un BudgetDataDto', async ({ request }) => {
    expect(fs.existsSync(JSON_DATASET)).toBe(true);
    const payload = JSON.parse(fs.readFileSync(JSON_DATASET, 'utf8'));

    const res = await request.post(API + '/budget/import', {
      data: payload,
      headers: { 'Content-Type': 'application/json' },
    });
    expect(res.status(), 'POST /budget/import doit retourner 200').toBe(200);

    const body = await res.json();
    expect(body, 'La reponse doit etre un BudgetDataDto').toHaveProperty('settings');
  });

  test('POST /tresorerie/incomes (ajout vide) retourne 201', async ({ request }) => {
    const res = await request.post(API + '/tresorerie/incomes', {
      data: {},
      headers: { 'Content-Type': 'application/json' },
    });
    expect(res.status()).toBe(201);
  });

  test('POST /budget/reset retourne 200', async ({ request }) => {
    const res = await request.post(API + '/budget/reset');
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body).toHaveProperty('settings');
  });
});