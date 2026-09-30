'use strict';

// VT-500 / F4 : Operation engagee -> modification -> reload (16-tests.md).
// Remplace le test historique de functional.spec.js : creation, modification (note, categorie,
// ventilation), lecture directe du backend, reload, nouvelle lecture du backend. Fallback JS
// desactive ; la persistance est prouvee par GET /pending-operations (jamais par un etat JS ou
// localStorage, qui est vide avant le reload).

const { test, expect } = require('@playwright/test');
const {
  API,
  FRONT,
  resetToReferenceState,
  waitForReactMount,
  expectBackendCall,
  disableJsFallback,
  expectFreshBrowserState,
} = require('./helpers');

const OP_ID = 'op_e2e_ui_test';
const FORCE_WRITE = '/pending-operations/force';

const CREATED = {
  id: OP_ID,
  date: '2026-06-15',
  expectedDate: '2026-06-25',
  type: 'cheque',
  refNumber: 'CHQ-990011',
  label: 'Test Facture Travaux',
  amount: -120.0,
  categoryId: '',
  notes: 'Initiale sans note',
};

const MODIFIED = {
  ...CREATED,
  refNumber: 'CHQ-990011-MOD',
  label: 'Test Facture Travaux Modifiee',
  categoryId: 'cat_travaux',
  notes: 'Note detaillee apres modification',
  splits: [
    { id: 'sp_1', categoryId: 'cat_travaux', amount: -80.0, label: 'Peinture' },
    { id: 'sp_2', categoryId: 'cat_divers', amount: -40.0, label: 'Outillage' },
  ],
};

async function backendOperation(request) {
  const res = await request.get(API + '/pending-operations');
  expect(res.status(), 'GET /pending-operations').toBe(200);
  const body = await res.json();
  return (body.pendingOperations || []).find(o => o.id === OP_ID);
}

/** Enregistre une operation par l API du front et exige l ecriture backend 2xx. */
async function saveFromFront(page, op, opId) {
  const write = expectBackendCall(page, FORCE_WRITE, 'POST');
  await page.evaluate(
    ([data, id]) => window.BudgetApp.BudgetApi.savePendingOperation(data, id),
    [op, opId]
  );
  expect((await write).status()).toBe(200);
}

test.describe('F4 - Operation engagee -> modification -> reload (VT-500)', () => {
  test.beforeEach(async ({ page, request }) => {
    await resetToReferenceState(request);
    await disableJsFallback(page);
  });

  test('creation, modification et persistance backend apres reload', async ({ page, request }) => {
    expect(await backendOperation(request), 'aucune operation avant le scenario').toBeUndefined();

    await page.goto(FRONT + '/pending.html');
    await waitForReactMount(page);
    await expectFreshBrowserState(page);

    // Creation : ecriture backend exigee puis relecture directe.
    await saveFromFront(page, CREATED);
    const created = await backendOperation(request);
    expect(created, 'operation creee cote backend').toBeTruthy();
    expect(created.label).toBe(CREATED.label);
    expect(created.notes).toBe(CREATED.notes);

    // Modification complete : note, categorie, ventilation.
    await saveFromFront(page, MODIFIED, OP_ID);
    const modified = await backendOperation(request);
    expect(modified.label).toBe(MODIFIED.label);
    expect(modified.categoryId).toBe(MODIFIED.categoryId);
    expect(modified.notes).toBe(MODIFIED.notes);
    expect(modified.splits).toHaveLength(2);
    expect(modified.splits[0].label).toBe('Peinture');

    // Aucune ecriture en attente de synchronisation : tout est parti au backend.
    const queue = await page.evaluate(() => window.localStorage.getItem('budgetapp.syncQueue.v1'));
    expect(queue === null || JSON.parse(queue).length === 0, 'file de synchronisation vide').toBe(true);

    // Reload avec un localStorage vide : l ecran ne peut afficher que ce que le backend restitue.
    await page.evaluate(() => window.localStorage.clear());
    const read = expectBackendCall(page, '/pending-operations', 'GET');
    await page.reload();
    await waitForReactMount(page);
    expect((await read).status()).toBe(200);

    await expect(page.locator('text=Test Facture Travaux Modifiee')).toBeVisible({ timeout: 10000 });
    await expect(page.locator('text=Note detaillee apres modification')).toBeVisible({ timeout: 10000 });
    await expect(page.locator('text=✂ Ventilée (2)')).toBeVisible({ timeout: 10000 });

    const persisted = await backendOperation(request);
    expect(persisted.label).toBe(MODIFIED.label);
    expect(persisted.splits).toHaveLength(2);
  });
});
