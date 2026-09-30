'use strict';

// VT-230 : facade Settings apres separation de l ownership.
// Modifie, depuis l ecran Parametres, trois champs de proprietaires distincts :
//   - Retraite      : retireAge (age de depart)
//   - Tresorerie    : startBalance (tresorerie de depart)
//   - Objectifs     : goalSecureHorizonMonths (seuil de securisation, store dedie)
// Chaque ecriture doit etre acceptee (PUT /settings 2xx). Les trois valeurs sont ensuite relues
// dans GET /settings, puis dans les vues impactees (Overview) et dans l ecran Parametres apres
// reload. Fallback JS desactive, contexte vierge : aucune valeur n est acceptee depuis un etat JS.
//
// Dataset : dataset de reference, avec sweepEnabled renseigne dans une copie temporaire.
// Constat connu (VT-340) : BudgetMutationService.updateTaxSettings leve une NullPointerException
// des qu un champ est modifie alors que sweepEnabled est absent des donnees importees, ce qui est
// le cas du fichier partage. Ce fichier n est pas modifie ici (meme contournement que VT-340).

const { test, expect } = require('@playwright/test');
const fs   = require('fs');
const path = require('path');
const {
  API,
  REFERENCE_DATASET,
  resetBackendState,
  importDataset,
  waitForReactMount,
  expectBackendCall,
  disableJsFallback,
  gotoAndExpectBackend,
  expectFreshBrowserState,
} = require('./helpers');

const REFERENCE = JSON.parse(fs.readFileSync(REFERENCE_DATASET, 'utf8'));
const REF = REFERENCE.settings;

const NEW_RETIRE_AGE   = REF.retireAge + 2;
const NEW_START_BALANCE = REF.startBalance + 3333;
const NEW_SECURE_HORIZON = 18;

const LABEL_RETIRE_AGE = 'Âge de départ à la retraite visé';
const LABEL_START_BALANCE = 'Trésorerie disponible de départ (€)';
const LABEL_SECURE_HORIZON = 'Début de sécurisation (mois avant l\'échéance)';

/** Champ de saisie place juste apres son libelle (vue Parametres). */
function settingsField(page, label) {
  return page.getByText(label, { exact: true }).locator('xpath=following-sibling::input').first();
}

/** Saisit une valeur, quitte le champ (flush du debounce) et exige la reponse PUT /settings 2xx. */
async function changeSettingAndExpectWrite(page, label, value) {
  const write = expectBackendCall(page, '/settings', 'PUT');
  const field = settingsField(page, label);
  await field.fill(String(value));
  await field.blur();
  const res = await write;
  expect(res.status(), 'PUT /settings pour "' + label + '"').toBe(200);
}

/** Ecrit une copie temporaire du dataset de reference avec sweepEnabled renseigne. */
function writeDatasetWithSweepEnabled(testInfo) {
  const copy = JSON.parse(JSON.stringify(REFERENCE));
  copy.settings.sweepEnabled = false;
  const file = testInfo.outputPath('budget-familial-sweep.json');
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, JSON.stringify(copy), 'utf8');
  return file;
}

test.describe('Parametres multi-domaines (VT-230)', () => {
  test.beforeEach(async ({ page, request }, testInfo) => {
    await resetBackendState(request);
    await importDataset(request, writeDatasetWithSweepEnabled(testInfo));
    await disableJsFallback(page);
  });

  test('trois proprietaires modifies : relus par /settings, Overview et Parametres apres reload', async ({ page, request }) => {
    const first = await gotoAndExpectBackend(page, '/settings.html', '/settings');
    expect(first.status()).toBe(200);
    await expectFreshBrowserState(page);

    const initial = (await first.json()).settings;
    expect(initial.retireAge).toBe(REF.retireAge);
    expect(initial.startBalance).toBe(REF.startBalance);
    await expect(settingsField(page, LABEL_RETIRE_AGE)).toHaveValue(String(REF.retireAge));
    await expect(settingsField(page, LABEL_START_BALANCE)).toHaveValue(String(REF.startBalance));

    await changeSettingAndExpectWrite(page, LABEL_RETIRE_AGE, NEW_RETIRE_AGE);
    await changeSettingAndExpectWrite(page, LABEL_START_BALANCE, NEW_START_BALANCE);
    await changeSettingAndExpectWrite(page, LABEL_SECURE_HORIZON, NEW_SECURE_HORIZON);

    // Relecture directe de la facade : les trois proprietaires sont restitues ensemble.
    const res = await request.get(API + '/settings');
    expect(res.status()).toBe(200);
    const settings = (await res.json()).settings;
    expect(Number(settings.retireAge)).toBe(NEW_RETIRE_AGE);
    expect(Number(settings.startBalance)).toBe(NEW_START_BALANCE);
    expect(Number(settings.goalSecureHorizonMonths)).toBe(NEW_SECURE_HORIZON);
    // Un champ non touche du meme ecran reste inchange.
    expect(Number(settings.birthYear)).toBe(REF.birthYear);

    // Vue impactee : Overview relit le backend (annee de retraite, tresorerie de depart).
    const overview = await gotoAndExpectBackend(page, '/overview.html', '/overview');
    expect(overview.status()).toBe(200);
    const overviewSettings = (await overview.json()).data.settings;
    expect(Number(overviewSettings.retireAge)).toBe(NEW_RETIRE_AGE);
    expect(Number(overviewSettings.startBalance)).toBe(NEW_START_BALANCE);
    const retireKpi = page.getByText('Année de retraite visée', { exact: true }).locator('xpath=..');
    await expect(retireKpi).toContainText(String(REF.birthYear + NEW_RETIRE_AGE));

    // Reload de l ecran Parametres : les trois valeurs sont relues depuis le backend.
    const reloaded = await gotoAndExpectBackend(page, '/settings.html', '/settings');
    const reloadedSettings = (await reloaded.json()).settings;
    expect(Number(reloadedSettings.goalSecureHorizonMonths)).toBe(NEW_SECURE_HORIZON);
    await expect(settingsField(page, LABEL_RETIRE_AGE)).toHaveValue(String(NEW_RETIRE_AGE));
    await expect(settingsField(page, LABEL_START_BALANCE)).toHaveValue(String(NEW_START_BALANCE));
    await expect(settingsField(page, LABEL_SECURE_HORIZON)).toHaveValue(String(NEW_SECURE_HORIZON));

    const again = expectBackendCall(page, '/settings', 'GET');
    await page.reload();
    await waitForReactMount(page);
    expect((await again).status()).toBe(200);
    await expect(settingsField(page, LABEL_SECURE_HORIZON)).toHaveValue(String(NEW_SECURE_HORIZON));
  });
});
