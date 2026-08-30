'use strict';

const { test, expect } = require('@playwright/test');
const path = require('path');
const fs   = require('fs');

const API   = 'http://localhost:8080/api/v1';
const FRONT = 'http://localhost:3000';

// Fichier de donnees de test a la racine du projet ou dans data/
const JSON_DATASET = fs.existsSync(path.resolve(__dirname, '..', '..', 'data', 'budget-familial.json'))
  ? path.resolve(__dirname, '..', '..', 'data', 'budget-familial.json')
  : path.resolve(__dirname, '..', '..', 'budget-familial.json');

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Attend une reponse 2xx du backend Spring Boot sur un chemin d API donne.
 * Si le fallback JS prend le relais (aucun appel reseau emis), waitForResponse
 * expire et le test echoue, ce qui est le comportement voulu.
 */
function expectBackendCall(page, apiPath, method) {
  return page.waitForResponse(
    res => {
      const url         = res.url();
      const status      = res.status();
      const matchPath   = url.includes(API + apiPath) || url.includes('/api/v1' + apiPath);
      const matchMethod = method ? res.request().method() === method : true;
      return matchPath && matchMethod && status >= 200 && status < 300;
    },
    { timeout: 20000 }
  );
}

async function waitForReactMount(page) {
  await page.waitForSelector('#root > *', { timeout: 15000 });
}

// ---------------------------------------------------------------------------
// 1. Vue d ensemble – GET /overview
// ---------------------------------------------------------------------------
test.describe("Vue d ensemble", () => {
  test('charge les KPIs via GET /overview', async ({ page }) => {
    const backendCall = expectBackendCall(page, '/overview', 'GET');
    await page.goto(FRONT + '/overview.html');
    await waitForReactMount(page);

    const res = await backendCall;
    expect(res.status(), 'GET /overview doit retourner 200').toBe(200);

    const body = await res.json();
    expect(body, 'OverviewResponseDto doit avoir une propriete data').toHaveProperty('data');

    await expect(page.locator('#root')).not.toBeEmpty();
  });

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
// 2. Tresorerie – GET /tresorerie
// ---------------------------------------------------------------------------
test.describe('Tresorerie', () => {
  test('charge les donnees via GET /tresorerie', async ({ page }) => {
    const backendCall = expectBackendCall(page, '/tresorerie', 'GET');
    await page.goto(FRONT + '/cashflow.html');
    await waitForReactMount(page);

    const res = await backendCall;
    expect(res.status()).toBe(200);

    const body = await res.json();
    expect(body).toHaveProperty('incomes');
    expect(body).toHaveProperty('charges');
  });
});

// ---------------------------------------------------------------------------
// 3. Patrimoine – GET /patrimoine
// ---------------------------------------------------------------------------
test.describe('Patrimoine', () => {
  test('charge les donnees via GET /patrimoine', async ({ page }) => {
    const backendCall = expectBackendCall(page, '/patrimoine', 'GET');
    await page.goto(FRONT + '/patrimoine.html');
    await waitForReactMount(page);

    const res = await backendCall;
    expect(res.status()).toBe(200);

    const body = await res.json();
    expect(body).toHaveProperty('placements');
  });
});

// ---------------------------------------------------------------------------
// 4. Parametres – GET /settings
// ---------------------------------------------------------------------------
test.describe('Parametres', () => {
  test('charge les parametres via GET /settings', async ({ page }) => {
    const backendCall = expectBackendCall(page, '/settings', 'GET');
    await page.goto(FRONT + '/settings.html');
    await waitForReactMount(page);

    const res = await backendCall;
    expect(res.status()).toBe(200);
  });
});

// ---------------------------------------------------------------------------
// 5. Analyse – GET /analyse
// ---------------------------------------------------------------------------
test.describe('Analyse', () => {
  test('charge les donnees via GET /analyse avec data complet', async ({ page }) => {
    const backendCall = expectBackendCall(page, '/analyse', 'GET');
    await page.goto(FRONT + '/analyse.html');
    await waitForReactMount(page);

    const res = await backendCall;
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body, 'AnalyseResponseDto doit contenir la propriete data').toHaveProperty('data');
    expect(body.data, 'data doit contenir charges').toHaveProperty('charges');
    expect(body.data, 'data doit contenir bankImport').toHaveProperty('bankImport');
    expect(body.data, 'data doit contenir settings').toHaveProperty('settings');
    expect(body).toHaveProperty('bankImport');
    expect(body).toHaveProperty('charges');
    expect(body).toHaveProperty('kpis');

    await expect(page.locator('#root')).not.toBeEmpty();
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
  test('le bouton Importer JSON envoie le fichier au backend et l IHM se met a jour', async ({ page }) => {
    expect(fs.existsSync(JSON_DATASET), 'Le fichier budget-familial.json doit exister').toBe(true);

    await page.goto(FRONT + '/overview.html');
    await waitForReactMount(page);

    // Attacher la surveillance reseau AVANT le declenchement du changement de fichier
    const backendImportCall = page.waitForResponse(
      res => {
        const url    = res.url();
        const method = res.request().method();
        const ok     = res.status() >= 200 && res.status() < 300;
        return (url.includes('/budget/import') || url.includes('/api/v1/budget/import'))
          && method === 'POST'
          && ok;
      },
      { timeout: 30000 }
    );

    // Rendre l input file interactif (il est cache par display:none)
    await page.evaluate(() => {
      const inp = document.querySelector('input[type="file"][accept="application/json"]');
      if (inp) { inp.style.display = 'block'; inp.style.visibility = 'visible'; }
    });

    const fileInput = page.locator('input[type="file"][accept="application/json"]');
    await fileInput.setInputFiles(JSON_DATASET);

    // ── Assertion reseau (obligatoire) ──────────────────────────────────────
    const res = await backendImportCall;
    expect(res.status(), 'POST /budget/import doit retourner 200').toBe(200);
    expect(res.request().method()).toBe('POST');
    expect(res.url()).toMatch(/\/budget\/import/);

    const body = await res.json().catch(() => ({}));
    expect(body, 'La reponse doit contenir un BudgetDataDto (settings)').toHaveProperty('settings');

    // ── Assertion IHM ───────────────────────────────────────────────────────
    await expect(page.locator('#root')).not.toBeEmpty();
  });

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
// 9. Operations engagees (pending.html) – Creation, modification, ventilation, reactivite immediate et persistance
// ---------------------------------------------------------------------------
test.describe('Operations engagees (pending.html)', () => {
  test('modification d une operation (categorie, note, ventilation) : mise a jour immediate et persistance apres rafraichissement', async ({ page }) => {
    await page.goto(FRONT + '/pending.html');
    await waitForReactMount(page);

    // Initialiser ou inserer une operation engagee via le service / store
    await page.evaluate(async () => {
      const api = window.BudgetApp?.BudgetApi;
      if (api) {
        await api.savePendingOperation({
          id: 'op_e2e_ui_test',
          date: '2026-06-15',
          expectedDate: '2026-06-25',
          type: 'cheque',
          refNumber: 'CHQ-990011',
          label: 'Test Facture Travaux',
          amount: -120.00,
          categoryId: '',
          notes: 'Initiale sans note'
        });
      }
    });

    // Recharger la vue pour afficher l'operation
    await page.goto(FRONT + '/pending.html');
    await waitForReactMount(page);

    // Verifier la presence de la ligne
    await expect(page.locator('text=Test Facture Travaux')).toBeVisible({ timeout: 10000 });

    // Modifier l'operation via API / store pour simuler une edition complete avec note, categorie et splits
    await page.evaluate(async () => {
      const api = window.BudgetApp?.BudgetApi;
      if (api) {
        await api.savePendingOperation({
          id: 'op_e2e_ui_test',
          date: '2026-06-15',
          expectedDate: '2026-06-25',
          type: 'cheque',
          refNumber: 'CHQ-990011-MOD',
          label: 'Test Facture Travaux Modifiee',
          amount: -120.00,
          categoryId: 'cat_travaux',
          notes: 'Note detaillee apres modification',
          splits: [
            { id: 'sp_1', categoryId: 'cat_travaux', amount: -80.00, label: 'Peinture' },
            { id: 'sp_2', categoryId: 'cat_divers', amount: -40.00, label: 'Outillage' }
          ]
        }, 'op_e2e_ui_test');
      }
    });

    // Verifier que l'API renvoie bien les donnees modifiees avec les splits et notes
    const opData = await page.evaluate(async () => {
      const api = window.BudgetApp?.BudgetApi;
      const res = await api.getPendingOperations();
      return res.pendingOperations.find(o => o.id === 'op_e2e_ui_test');
    });

    expect(opData.label).toBe('Test Facture Travaux Modifiee');
    expect(opData.notes).toBe('Note detaillee apres modification');
    expect(opData.splits).toHaveLength(2);
    expect(opData.splits[0].label).toBe('Peinture');

    // Rafraichir la page pour verifier la persistance complete
    await page.reload();
    await waitForReactMount(page);

    await expect(page.locator('text=Test Facture Travaux Modifiee')).toBeVisible({ timeout: 10000 });
    await expect(page.locator('text=Note detaillee apres modification')).toBeVisible({ timeout: 10000 });
    await expect(page.locator('text=✂ Ventilée (2)')).toBeVisible({ timeout: 10000 });
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