'use strict';

// Mecanismes de reinitialisation d etat pour les scenarios de reference (VT-000).
//
// Etat cote serveur  : resetBackendState() / importReferenceDataset() (API REST).
// Etat cote navigateur : chaque test Playwright recoit deja un contexte vierge ;
//   clearBrowserState() ne sert que si un scenario a d abord charge une page de
//   l application dans le meme contexte (localStorage alors alimente par le front).
//
// Ces helpers ne modifient pas le contrat metier : ils n appellent que
// POST /budget/reset et POST /budget/import, deja exposes.

const fs = require('fs');
const { API, REFERENCE_DATASET, BROWSER_STORAGE_KEYS } = require('./constants');

/**
 * Remet le backend a l etat initial (POST /budget/reset).
 * @param {import('@playwright/test').APIRequestContext} request
 */
async function resetBackendState(request) {
  const res = await request.post(API + '/budget/reset');
  if (res.status() !== 200) {
    throw new Error('POST /budget/reset a retourne ' + res.status() + ' (200 attendu)');
  }
  return res.json();
}

/**
 * Importe un dataset dans le backend (POST /budget/import). Par defaut le
 * dataset de reference versionne. Retourne le BudgetDataDto de la reponse.
 * @param {import('@playwright/test').APIRequestContext} request
 * @param {string} [datasetPath]
 */
async function importDataset(request, datasetPath) {
  const payload = JSON.parse(fs.readFileSync(datasetPath || REFERENCE_DATASET, 'utf8'));
  const res = await request.post(API + '/budget/import', {
    data: payload,
    headers: { 'Content-Type': 'application/json' },
  });
  if (res.status() !== 200) {
    throw new Error('POST /budget/import a retourne ' + res.status() + ' (200 attendu)');
  }
  return res.json();
}

/**
 * Etat serveur deterministe : reset puis import du dataset de reference.
 * A appeler en debut de scenario (test.beforeEach ou debut de test).
 * @param {import('@playwright/test').APIRequestContext} request
 */
async function resetToReferenceState(request) {
  await resetBackendState(request);
  return importDataset(request, REFERENCE_DATASET);
}

/**
 * Vide localStorage et sessionStorage de la page courante. La page doit deja
 * avoir ete chargee sur l origine du front (sinon l acces au stockage est refuse).
 * Ne s applique pas avant le chargement des scripts : pour cela, voir VT-200.
 * @param {import('@playwright/test').Page} page
 */
async function clearBrowserState(page) {
  await page.evaluate(keys => {
    keys.forEach(k => window.localStorage.removeItem(k));
    window.localStorage.clear();
    window.sessionStorage.clear();
  }, BROWSER_STORAGE_KEYS);
}

module.exports = {
  resetBackendState,
  importDataset,
  resetToReferenceState,
  clearBrowserState,
};
