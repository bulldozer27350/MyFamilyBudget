'use strict';

// Mode "sans fallback JS" et contexte vierge pour les scenarios critiques (VT-200).
//
// - disableJsFallback(page|context) : installe, AVANT le chargement des scripts, l override
//   lu par view/config.js (sessionStorage) qui force window.DISABLE_JS_FALLBACK = true.
// - expectFreshBrowserState(page) : prouve que localStorage est vide au premier chargement.
// - gotoAndExpectBackend / actAndExpectBackend : attendent une reponse 2xx du backend pour
//   une lecture ou une ecriture critique ; aucun appel reseau = echec (pas de faux vert).
//
// Aucun contrat metier n est modifie ; la valeur de production reste false.

const { expect } = require('@playwright/test');
const { FRONT, NO_FALLBACK_STORAGE_KEY } = require('./constants');
const { expectBackendCall, waitForReactMount } = require('./backend');

/**
 * Force DISABLE_JS_FALLBACK=true pour toutes les pages futures de la page ou du contexte.
 * A appeler avant le premier page.goto().
 * @param {import('@playwright/test').Page | import('@playwright/test').BrowserContext} target
 */
async function disableJsFallback(target) {
  await target.addInitScript(key => {
    try {
      window.sessionStorage.setItem(key, 'true');
    } catch (e) {
      // ignore : config.js conservera alors la valeur par defaut
    }
  }, NO_FALLBACK_STORAGE_KEY);
}

/**
 * Retourne l etat de window.DISABLE_JS_FALLBACK dans la page courante.
 * @param {import('@playwright/test').Page} page
 */
async function readJsFallbackFlag(page) {
  return page.evaluate(() => window.DISABLE_JS_FALLBACK);
}

/**
 * Ouvre une page et attend la reponse backend correspondante (lecture critique).
 * Retourne la reponse. La page doit etre une URL relative au front (ex: '/overview.html').
 *
 * La page est d abord ramenee sur about:blank : une requete encore en vol du document precedent (par exemple la
 * relecture que l ecran lance apres un import) ne peut plus satisfaire l attente armee juste apres. Sans cela, la
 * reponse retournee pouvait appartenir a l ancien document, et son corps n etait alors plus lisible une fois la
 * navigation faite (`Response body is not available for a response that was navigated away from`).
 * @param {import('@playwright/test').Page} page
 * @param {string} frontPath  ex: '/overview.html'
 * @param {string} apiPath    ex: '/overview'
 * @param {string} [method]   defaut : 'GET'
 */
async function gotoAndExpectBackend(page, frontPath, apiPath, method = 'GET') {
  await page.goto('about:blank');
  const backendCall = expectBackendCall(page, apiPath, method);
  await page.goto(FRONT + frontPath);
  await waitForReactMount(page);
  return backendCall;
}

/**
 * Execute une action utilisateur (clic, saisie...) et attend la reponse backend associee
 * (lecture ou ecriture critique). L attente est armee AVANT l action.
 * @param {import('@playwright/test').Page} page
 * @param {string} apiPath
 * @param {string} method
 * @param {() => Promise<unknown>} action
 */
async function actAndExpectBackend(page, apiPath, method, action) {
  const backendCall = expectBackendCall(page, apiPath, method);
  await action();
  return backendCall;
}

/**
 * Verifie qu aucune donnee locale historique n est presente : localStorage vide.
 * A appeler juste apres le premier chargement d une page dans un contexte neuf.
 * @param {import('@playwright/test').Page} page
 */
async function expectFreshBrowserState(page) {
  const keys = await page.evaluate(() => Object.keys(window.localStorage));
  expect(keys, 'localStorage doit etre vide dans un contexte vierge').toEqual([]);
}

module.exports = {
  disableJsFallback,
  readJsFallbackFlag,
  gotoAndExpectBackend,
  actAndExpectBackend,
  expectFreshBrowserState,
};
