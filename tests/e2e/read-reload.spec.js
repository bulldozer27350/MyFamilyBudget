'use strict';

// VT-210 : scenarios frontend lecture + reload.
// Objectif : verifier que Overview, Tresorerie, Patrimoine, Parametres et Analyse consomment
// reellement le backend, avec fallback JS desactive, contexte vierge et relecture apres reload.
//
// Pour chaque ecran : (1) la lecture backend 2xx est exigee au premier chargement, (2) le contenu
// metier minimal attendu du dataset de reference est affiche, (3) apres page.reload() une
// nouvelle lecture backend est exigee et le meme contenu reste affiche.
// Aucun contrat metier n est modifie ; le dataset est celui de tests/e2e/fixtures.

const { test, expect } = require('@playwright/test');
const fs   = require('fs');
const path = require('path');
const {
  REFERENCE_DATASET,
  resetToReferenceState,
  importDataset,
  waitForReactMount,
  expectBackendCall,
  disableJsFallback,
  readJsFallbackFlag,
  gotoAndExpectBackend,
  expectFreshBrowserState,
} = require('./helpers');

const REFERENCE = JSON.parse(fs.readFileSync(REFERENCE_DATASET, 'utf8'));
const REF_SETTINGS = REFERENCE.settings;

/** Recharge la page en exigeant une nouvelle lecture backend (aucun rejeu depuis un etat JS). */
async function reloadAndExpectBackend(page, apiPath) {
  const backendCall = expectBackendCall(page, apiPath, 'GET');
  await page.reload();
  await waitForReactMount(page);
  return backendCall;
}

/** Valeurs courantes de tous les champs <input> de la page. */
function inputValues(page) {
  return page.locator('input').evaluateAll(els => els.map(e => e.value));
}

/** Champ de saisie place juste apres son libelle (vue Parametres). */
function settingsField(page, label) {
  return page.getByText(label, { exact: true }).locator('xpath=following-sibling::input').first();
}

test.describe('Lecture + reload sans fallback JS (VT-210)', () => {
  test.beforeEach(async ({ page, request }) => {
    await resetToReferenceState(request);
    await disableJsFallback(page);
  });

  test('Overview : KPIs servis par le backend, identiques apres reload', async ({ page }) => {
    const first = await gotoAndExpectBackend(page, '/overview.html', '/overview');
    expect(first.status()).toBe(200);
    expect(await readJsFallbackFlag(page)).toBe(true);
    await expectFreshBrowserState(page);

    const body = await first.json();
    expect(body.data.settings.birthYear).toBe(REF_SETTINGS.birthYear);
    const retireYear = REF_SETTINGS.birthYear + REF_SETTINGS.retireAge;

    const assertScreen = async () => {
      const root = page.locator('#root');
      await expect(root).toContainText('Patrimoine placé actuel');
      await expect(root).toContainText('Flux net — année en cours');
      await expect(root).toContainText('Solde réel');
      const retireKpi = page.getByText('Année de retraite visée', { exact: true }).locator('xpath=..');
      await expect(retireKpi).toContainText(String(retireYear));
    };

    await assertScreen();
    const second = await reloadAndExpectBackend(page, '/overview');
    expect(second.status()).toBe(200);
    await assertScreen();
  });

  test('Tresorerie : lignes du dataset relues depuis le backend apres reload', async ({ page }) => {
    const first = await gotoAndExpectBackend(page, '/cashflow.html', '/tresorerie');
    expect(first.status()).toBe(200);
    await expectFreshBrowserState(page);

    const body = await first.json();
    expect(body.incomes.map(i => i.label)).toContain('Salaire');
    expect(body.charges.map(c => c.label)).toContain('Loyer');

    const assertScreen = async () => {
      await expect(page.locator('#root')).toContainText('Revenus récurrents');
      await expect(page.locator('#root')).toContainText('Charges récurrentes');
      await expect.poll(() => inputValues(page)).toEqual(expect.arrayContaining(['Salaire', 'Loyer']));
    };

    await assertScreen();
    const second = await reloadAndExpectBackend(page, '/tresorerie');
    expect(second.status()).toBe(200);
    await assertScreen();
  });

  test('Patrimoine : placement du dataset affiche depuis le backend et apres reload', async ({ page }) => {
    const first = await gotoAndExpectBackend(page, '/patrimoine.html', '/patrimoine');
    expect(first.status()).toBe(200);
    await expectFreshBrowserState(page);

    const body = await first.json();
    expect(body.placements.map(p => p.label)).toContain('PEA');

    const assertScreen = async () => {
      await expect(page.locator('#root')).toContainText('Placements & comptes');
      await expect(page.locator('#root')).toContainText('PEA');
    };

    await assertScreen();
    const second = await reloadAndExpectBackend(page, '/patrimoine');
    expect(second.status()).toBe(200);
    await assertScreen();
  });

  test('Parametres : valeurs du dataset affichees, puis valeur modifiee cote serveur relue apres reload', async ({ page, request }) => {
    const first = await gotoAndExpectBackend(page, '/settings.html', '/settings');
    expect(first.status()).toBe(200);
    await expectFreshBrowserState(page);

    const fields = [
      ['Année de naissance (parent référent)', REF_SETTINGS.birthYear],
      ['Âge de départ à la retraite visé', REF_SETTINGS.retireAge],
      ['Simuler la retraite jusqu\'à l\'âge de', REF_SETTINGS.simulateUntilAge],
      ['Trésorerie disponible de départ (€)', REF_SETTINGS.startBalance],
    ];
    for (const [label, expected] of fields) {
      await expect(settingsField(page, label)).toHaveValue(String(expected));
    }

    // Le serveur change hors du navigateur : seul un vrai rechargement backend peut le refleter.
    const changedStartBalance = REF_SETTINGS.startBalance + 2222;
    const changed = JSON.parse(JSON.stringify(REFERENCE));
    changed.settings.startBalance = changedStartBalance;
    const tmpDataset = test.info().outputPath('budget-familial-modifie.json');
    fs.mkdirSync(path.dirname(tmpDataset), { recursive: true });
    fs.writeFileSync(tmpDataset, JSON.stringify(changed), 'utf8');
    await importDataset(request, tmpDataset);

    const second = await reloadAndExpectBackend(page, '/settings');
    expect(second.status()).toBe(200);
    await expect(settingsField(page, 'Trésorerie disponible de départ (€)'))
      .toHaveValue(String(changedStartBalance));
    await expect(settingsField(page, 'Année de naissance (parent référent)'))
      .toHaveValue(String(REF_SETTINGS.birthYear));
  });

  test('Analyse : etat sans import bancaire servi par le backend, identique apres reload', async ({ page }) => {
    const first = await gotoAndExpectBackend(page, '/analyse.html', '/analyse');
    expect(first.status()).toBe(200);
    await expectFreshBrowserState(page);

    const body = await first.json();
    expect(body.data.bankImport.transactions).toHaveLength(0);
    expect(body).toHaveProperty('kpis');

    const assertScreen = async () => {
      await expect(page.locator('#root')).toContainText('Aucune transaction importée');
    };

    await assertScreen();
    const second = await reloadAndExpectBackend(page, '/analyse');
    expect(second.status()).toBe(200);
    await assertScreen();
  });
});
