'use strict';

// Helpers d attente sur les reponses du backend (VT-000).
// Deplaces depuis functional.spec.js sans changement de comportement.

const { API } = require('./constants');

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

module.exports = { expectBackendCall, waitForReactMount };
