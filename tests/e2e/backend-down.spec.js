'use strict';

// VT-240 : diagnostic backend indisponible (test negatif).
// Avec DISABLE_JS_FALLBACK=true, une panne du backend ne doit plus etre masquee par le fallback JS :
//   - lecture : la vue n affiche aucune donnee du dataset (Overview, Tresorerie, Patrimoine restent
//     sur "Chargement...") et l erreur [DISABLE_JS_FALLBACK] remonte dans la console. Constat pour
//     Parametres : la vue sort de "Chargement" et affiche ses valeurs par defaut (birthYear 1985...)
//     faute de donnees ; le test verifie qu aucune valeur du dataset n y apparait ;
//   - ecriture : aucune reponse 2xx n est obtenue, la requete echoue explicitement (erreur console,
//     mutation placee dans la file de synchronisation "en attente") et rien n est presente comme
//     enregistre par le serveur, meme apres reload.
//
// La panne est simulee au niveau reseau du navigateur (page.route + abort) : le backend Spring
// partage par la suite n est pas arrete, ce qui garde les autres scenarios independants. Test
// strictement diagnostique : il ne rend pas le fallback indisponible par defaut (valeur de production
// inchangee a false, cf. no-fallback.spec.js).

const { test, expect } = require('@playwright/test');
const {
  FRONT,
  resetToReferenceState,
  waitForReactMount,
  disableJsFallback,
  readJsFallbackFlag,
  gotoAndExpectBackend,
} = require('./helpers');

const API_GLOB = '**/api/v1/**';
const SYNC_QUEUE_KEY = 'budgetapp.syncQueue.v1';

// Contenu metier du dataset de reference : ne doit jamais apparaitre backend coupe.
const BUSINESS_MARKERS = ['Salaire', 'Loyer', 'PEA', 'Revenus récurrents', 'Placements & comptes'];

/** Coupe le backend pour la page : toute requete vers l API echoue, comme un serveur injoignable. */
async function cutBackend(page) {
  await page.route(API_GLOB, route => route.abort('connectionrefused'));
}

/** Suit les erreurs console et les reponses HTTP 2xx de l API. */
function trackPage(page) {
  const state = { errors: [], apiSuccesses: [] };
  page.on('console', msg => {
    if (msg.type() === 'error') state.errors.push(msg.text());
  });
  page.on('response', res => {
    if (res.url().includes('/api/v1/') && res.status() >= 200 && res.status() < 300) {
      state.apiSuccesses.push(res.request().method() + ' ' + res.url());
    }
  });
  return state;
}

async function expectNoBusinessData(page) {
  const text = await page.locator('#root').innerText();
  for (const marker of BUSINESS_MARKERS) {
    expect(text, 'donnee "' + marker + '" affichee alors que le backend est coupe').not.toContain(marker);
  }
}

/** Valeurs par defaut de la vue Parametres : ni le dataset (1990 / 5000) ni une valeur locale. */
async function expectSettingsNotFromDataset(page) {
  const values = await page.locator('input').evaluateAll(els => els.map(e => e.value));
  expect(values, 'valeur du dataset (annee de naissance 1990) affichee').not.toContain('1990');
  expect(values, 'valeur du dataset (tresorerie de depart 5000) affichee').not.toContain('5000');
}

test.describe('Backend indisponible, fallback JS desactive (VT-240)', () => {
  test.beforeEach(async ({ page, request }) => {
    await resetToReferenceState(request);
    await disableJsFallback(page);
  });

  const READ_VIEWS = [
    ['Overview',    '/overview.html'],
    ['Tresorerie',  '/cashflow.html'],
    ['Patrimoine',  '/patrimoine.html'],
  ];

  for (const [name, frontPath] of READ_VIEWS) {
    test(name + ' : la lecture echoue explicitement, aucune donnee de repli affichee', async ({ page }) => {
      const tracked = trackPage(page);
      await cutBackend(page);

      await page.goto(FRONT + frontPath);
      await page.waitForFunction(() => window.DISABLE_JS_FALLBACK === true);
      expect(await readJsFallbackFlag(page)).toBe(true);

      await expect
        .poll(() => tracked.errors.some(t => t.includes('[DISABLE_JS_FALLBACK]')))
        .toBe(true);

      await waitForReactMount(page);
      await expect(page.locator('#root')).toContainText('Chargement');
      await expectNoBusinessData(page);
      expect(tracked.apiSuccesses, 'aucune reponse 2xx de l API attendue').toEqual([]);
    });
  }

  test('Parametres : la lecture echoue explicitement, aucune valeur du dataset affichee', async ({ page }) => {
    const tracked = trackPage(page);
    await cutBackend(page);

    await page.goto(FRONT + '/settings.html');
    await page.waitForFunction(() => window.DISABLE_JS_FALLBACK === true);

    await expect
      .poll(() => tracked.errors.some(t => t.includes('[DISABLE_JS_FALLBACK]')))
      .toBe(true);
    await expect
      .poll(() => tracked.errors.some(t => t.includes('Erreur lors du chargement des paramètres')))
      .toBe(true);

    await waitForReactMount(page);
    await expectSettingsNotFromDataset(page);
    expect(tracked.apiSuccesses, 'aucune reponse 2xx de l API attendue').toEqual([]);
  });

  test('ecriture : la modification echoue explicitement et n est jamais presentee comme enregistree', async ({ page }) => {
    // Chargement normal, backend disponible : la vue Parametres est affichee.
    const first = await gotoAndExpectBackend(page, '/settings.html', '/settings');
    expect(first.status()).toBe(200);
    const label = 'Trésorerie disponible de départ (€)';
    const field = page.getByText(label, { exact: true }).locator('xpath=following-sibling::input').first();
    const initial = await field.inputValue();

    // Panne du backend puis ecriture depuis l IHM.
    const tracked = trackPage(page);
    const failedWrite = page.waitForEvent('requestfailed', {
      predicate: req => req.method() === 'PUT' && req.url().includes('/api/v1/settings'),
      timeout: 20000,
    });
    await cutBackend(page);
    const changed = String(Number(initial) + 4444);
    await field.fill(changed);
    await field.blur();
    await failedWrite;

    // Echec explicite : erreur console, mutation en attente d envoi, aucune reponse 2xx.
    await expect
      .poll(() => tracked.errors.some(t => t.includes('Failed to update settings field on backend')))
      .toBe(true);
    const queue = await page.evaluate(key => JSON.parse(window.localStorage.getItem(key) || '{}'), SYNC_QUEUE_KEY);
    const pending = [...(queue.auto || []), ...(queue.validate || [])];
    expect(pending.some(e => e.method === 'PUT' && e.url.includes('/settings')),
      'la modification doit etre en attente d envoi, pas enregistree').toBe(true);
    expect(tracked.apiSuccesses, 'aucune reponse 2xx de l API attendue').toEqual([]);

    // Apres reload, toujours sans backend : ni valeur modifiee, ni valeur du dataset.
    await page.reload();
    await waitForReactMount(page);
    await expectSettingsNotFromDataset(page);
    const shown = await page.locator('input').evaluateAll(els => els.map(e => e.value));
    expect(shown, 'valeur non enregistree affichee comme si elle l etait').not.toContain(changed);
    expect(tracked.apiSuccesses).toEqual([]);
  });
});
