'use strict';

// VT-220 : mutation Patrimoine -> Tresorerie -> Overview, puis reload.
// Une ecriture de placement faite depuis l IHM Patrimoine doit etre acceptee par le backend
// (POST /patrimoine/placements), relue par les trois surfaces (Patrimoine, Tresorerie, Overview)
// et rester visible apres reload. Fallback JS desactive, contexte vierge.
//
// Aucune assertion ne s appuie sur un etat JS non relu du serveur : les valeurs controlees
// proviennent uniquement de reponses HTTP du backend (lecture directe ou lecture faite par la
// page au chargement) ; l IHM n est utilisee que pour declencher la mutation et rouvrir le tiroir.
//
// Dataset de reference : PEA 200 EUR/mois du 2026-01-01 au 2053-12-31 => epargne 2027 = 2 400.
// Le montant d epargne d une annee pleine est independant de la date du jour.

const { test, expect } = require('@playwright/test');
const {
  API,
  resetToReferenceState,
  waitForReactMount,
  expectBackendCall,
  disableJsFallback,
  gotoAndExpectBackend,
  expectFreshBrowserState,
} = require('./helpers');

const YEAR = 2027;
const PEA_MONTHLY_BEFORE = 200;
const PEA_MONTHLY_AFTER  = 300;
const NEW_LABEL   = 'Livret VT-220';
const NEW_MONTHLY = 50;
const PLACEMENT_WRITE = '/patrimoine/placements';

function savingsOf(cashflow, year) {
  const row = cashflow.find(r => r.year === year);
  expect(row, 'annee ' + year + ' absente du cashflow').toBeTruthy();
  return Number(row.savings);
}

async function getJson(request, apiPath) {
  const res = await request.get(API + apiPath);
  expect(res.status(), 'GET ' + apiPath).toBe(200);
  return res.json();
}

/** Champ de saisie du tiroir place dans le bloc qui suit son libelle. */
function drawerField(page, label) {
  return page.getByText(label, { exact: true }).locator('xpath=following-sibling::div//input').first();
}

/** Saisit une valeur puis quitte le champ (le blur declenche l ecriture sans attendre le debounce). */
async function fillAndCommit(field, value) {
  await field.fill(String(value));
  await field.blur();
}

/** Lit l epargne de l annee via Tresorerie et Overview, telles que servies par le backend. */
async function readSavings(request) {
  const tresorerie = await getJson(request, '/tresorerie');
  const overview   = await getJson(request, '/overview');
  return {
    tresorerie: savingsOf(tresorerie.cashflow, YEAR),
    overview:   savingsOf(overview.cashflow, YEAR),
  };
}

/**
 * Rouvre Tresorerie et Overview dans le navigateur : les reponses backend lues par les pages
 * elles-memes doivent porter l epargne attendue, avant puis apres un reload.
 */
async function expectPagesServeSavings(page, expected) {
  const tresorerie = await gotoAndExpectBackend(page, '/cashflow.html', '/tresorerie');
  expect(savingsOf((await tresorerie.json()).cashflow, YEAR)).toBeCloseTo(expected, 2);

  const overview = await gotoAndExpectBackend(page, '/overview.html', '/overview');
  expect(savingsOf((await overview.json()).cashflow, YEAR)).toBeCloseTo(expected, 2);

  const reloaded = expectBackendCall(page, '/overview', 'GET');
  await page.reload();
  await waitForReactMount(page);
  expect(savingsOf((await (await reloaded).json()).cashflow, YEAR)).toBeCloseTo(expected, 2);
}

test.describe('Mutation Patrimoine -> Tresorerie -> Overview (VT-220)', () => {
  test.beforeEach(async ({ page, request }) => {
    await resetToReferenceState(request);
    await disableJsFallback(page);
  });

  test('modification du versement mensuel du PEA : relu par les trois vues, puis apres reload', async ({ page, request }) => {
    const before = await readSavings(request);
    expect(before.tresorerie).toBeCloseTo(PEA_MONTHLY_BEFORE * 12, 2);
    expect(before.overview).toBeCloseTo(PEA_MONTHLY_BEFORE * 12, 2);

    const first = await gotoAndExpectBackend(page, '/patrimoine.html', '/patrimoine');
    expect(first.status()).toBe(200);
    await expectFreshBrowserState(page);

    await page.locator('#root').getByText('PEA', { exact: true }).first().click();
    const monthly = drawerField(page, 'Versement mensuel (€ / mois)');
    await expect(monthly).toBeVisible();

    const write = expectBackendCall(page, PLACEMENT_WRITE, 'POST');
    await fillAndCommit(monthly, PEA_MONTHLY_AFTER);
    expect((await write).status()).toBe(200);

    // Relecture directe du backend : Patrimoine, puis les deux vues qui en derivent.
    const patrimoine = await getJson(request, '/patrimoine');
    const pea = patrimoine.placements.find(p => p.label === 'PEA');
    expect(pea, 'PEA present apres mutation').toBeTruthy();
    expect(Number(pea.monthly)).toBe(PEA_MONTHLY_AFTER);
    expect(patrimoine.placements.filter(p => p.label === 'PEA')).toHaveLength(1);

    const after = await readSavings(request);
    expect(after.tresorerie).toBeCloseTo(PEA_MONTHLY_AFTER * 12, 2);
    expect(after.overview).toBeCloseTo(PEA_MONTHLY_AFTER * 12, 2);

    // Reload navigateur : les pages relisent le backend et servent la valeur persistee.
    await expectPagesServeSavings(page, PEA_MONTHLY_AFTER * 12);

    // Le tiroir Patrimoine rouvert apres reload affiche la valeur persistee.
    const reread = await gotoAndExpectBackend(page, '/patrimoine.html', '/patrimoine');
    const rereadPea = (await reread.json()).placements.find(p => p.label === 'PEA');
    expect(Number(rereadPea.monthly)).toBe(PEA_MONTHLY_AFTER);
    await page.locator('#root').getByText('PEA', { exact: true }).first().click();
    await expect(drawerField(page, 'Versement mensuel (€ / mois)')).toHaveValue(String(PEA_MONTHLY_AFTER));
  });

  test('creation d un placement : relu par les trois vues, puis apres reload', async ({ page, request }) => {
    const before = await readSavings(request);
    expect(before.tresorerie).toBeCloseTo(PEA_MONTHLY_BEFORE * 12, 2);

    const first = await gotoAndExpectBackend(page, '/patrimoine.html', '/patrimoine');
    expect(first.status()).toBe(200);
    await expectFreshBrowserState(page);

    await page.getByRole('button', { name: '+ Ajouter un nouveau placement' }).click();
    await expect(page.getByText('Nouveau placement / compte', { exact: true })).toBeVisible();

    await fillAndCommit(drawerField(page, 'Libellé du compte'), NEW_LABEL);
    await fillAndCommit(drawerField(page, 'Versement mensuel (€ / mois)'), NEW_MONTHLY);
    await fillAndCommit(drawerField(page, 'Versements dès le'), '2026-01-01');

    const write = expectBackendCall(page, PLACEMENT_WRITE, 'POST');
    await page.getByRole('button', { name: '✓ Créer le placement' }).click();
    expect((await write).status()).toBe(200);

    const patrimoine = await getJson(request, '/patrimoine');
    const created = patrimoine.placements.find(p => p.label === NEW_LABEL);
    expect(created, 'placement cree relu depuis le backend').toBeTruthy();
    expect(Number(created.monthly)).toBe(NEW_MONTHLY);
    expect(patrimoine.placements.map(p => p.label)).toContain('PEA');

    const expected = (PEA_MONTHLY_BEFORE + NEW_MONTHLY) * 12;
    const after = await readSavings(request);
    expect(after.tresorerie).toBeCloseTo(expected, 2);
    expect(after.overview).toBeCloseTo(expected, 2);

    await expectPagesServeSavings(page, expected);

    const reread = await gotoAndExpectBackend(page, '/patrimoine.html', '/patrimoine');
    expect((await reread.json()).placements.map(p => p.label)).toContain(NEW_LABEL);
    await expect(page.locator('#root')).toContainText(NEW_LABEL);
  });
});
