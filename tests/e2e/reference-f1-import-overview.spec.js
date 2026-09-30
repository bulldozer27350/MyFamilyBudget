'use strict';

// VT-500 / F1 : Import -> Overview -> modification -> Overview (16-tests.md).
// Contexte vierge, fallback JS desactive, dataset canonique versionne. L import est fait par
// le bouton "Importer JSON" de l IHM ; la modification (salaire mensuel 3 000 -> 3 600) est
// ecrite cote backend puis relue par l API et par l Overview apres reload.

const { test, expect } = require('@playwright/test');
const fs = require('fs');
const {
  API,
  REFERENCE_DATASET,
  resetBackendState,
  waitForReactMount,
  expectBackendCall,
  disableJsFallback,
  gotoAndExpectBackend,
  expectFreshBrowserState,
} = require('./helpers');

const REFERENCE = JSON.parse(fs.readFileSync(REFERENCE_DATASET, 'utf8'));
const SALARY = REFERENCE.incomes.find(i => i.id === 'inc_1');
const NEW_MONTHLY = 3600;

function salaryOf(overviewBody) {
  const income = overviewBody.data.incomes.find(i => i.id === SALARY.id);
  expect(income, 'revenu ' + SALARY.id + ' absent de la reponse Overview').toBeTruthy();
  return Number(income.monthly);
}

test.describe('F1 - Import -> Overview -> modification -> Overview (VT-500)', () => {
  test.beforeEach(async ({ page, request }) => {
    await resetBackendState(request);
    await disableJsFallback(page);
  });

  test('import du dataset de reference, modification d une ligne, Overview relu apres reload', async ({ page, request }) => {
    // 1-2. Contexte neuf, fallback desactive, page Overview chargee sur un backend vide.
    await gotoAndExpectBackend(page, '/overview.html', '/overview');
    await expectFreshBrowserState(page);

    // 3-4. Import du dataset de reference par l IHM : reponse 2xx exigee.
    const importCall = expectBackendCall(page, '/budget/import', 'POST');
    await page.evaluate(() => {
      const inp = document.querySelector('input[type="file"][accept="application/json"]');
      if (inp) { inp.style.display = 'block'; inp.style.visibility = 'visible'; }
    });
    await page.locator('input[type="file"][accept="application/json"]').setInputFiles(REFERENCE_DATASET);
    const imported = await importCall;
    expect(imported.status()).toBe(200);
    expect(await imported.json()).toHaveProperty('settings');

    // 5. Overview : KPIs et donnees du dataset de reference, servis par le backend.
    const first = await gotoAndExpectBackend(page, '/overview.html', '/overview');
    expect(first.status()).toBe(200);
    const firstBody = await first.json();
    expect(salaryOf(firstBody)).toBe(Number(SALARY.monthly));
    expect(firstBody.data.settings.birthYear).toBe(REFERENCE.settings.birthYear);
    await expect(page.locator('#root')).toContainText('Patrimoine placé actuel');
    const incomeBefore = Number(firstBody.cashflow.find(r => r.year === 2027).income);
    expect(incomeBefore).toBeGreaterThan(0);

    // 6. Modification representative d une ligne de tresorerie.
    const update = await request.put(API + '/tresorerie/incomes/' + SALARY.id, {
      data: { field: 'monthly', value: NEW_MONTHLY },
      headers: { 'Content-Type': 'application/json' },
    });
    expect(update.status(), 'PUT /tresorerie/incomes/' + SALARY.id).toBe(200);

    // 7. Relecture directe de l API correspondante puis de l Overview.
    const tresorerie = await (await request.get(API + '/tresorerie')).json();
    expect(Number(tresorerie.incomes.find(i => i.id === SALARY.id).monthly)).toBe(NEW_MONTHLY);
    const overview = await (await request.get(API + '/overview')).json();
    expect(salaryOf(overview)).toBe(NEW_MONTHLY);

    // 8-9. Reload : l Overview lu par la page reflete la valeur persistee.
    const afterCall = expectBackendCall(page, '/overview', 'GET');
    await page.reload();
    await waitForReactMount(page);
    const afterBody = await (await afterCall).json();
    expect(salaryOf(afterBody)).toBe(NEW_MONTHLY);
    const incomeAfter = Number(afterBody.cashflow.find(r => r.year === 2027).income);
    expect(incomeAfter / incomeBefore).toBeCloseTo(NEW_MONTHLY / Number(SALARY.monthly), 4);
    await expect(page.locator('#root')).toContainText('Patrimoine placé actuel');
  });
});
