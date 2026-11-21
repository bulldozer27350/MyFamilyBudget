'use strict';
/**
 * Vidéo 3.7 — Dépenses ponctuelles et primes
 *
 * Usage : node scripts/videos/run.js 3.7
 */

const path = require('path');
const fs   = require('fs');
const { createSession, finalizeVideo, detectServer } = require('./helpers/recorder');

const OUTPUT_DIR = path.join(__dirname, 'output');
const DEMO_JSON  = path.join(__dirname, '..', '..', 'data', 'budget-familial-demo.json');

async function loadDemoData(apiUrl) {
  if (!fs.existsSync(DEMO_JSON)) return;
  try {
    const body = fs.readFileSync(DEMO_JSON, 'utf8');
    const res = await fetch(`${apiUrl}/budget/import`, {
      method:  'POST',
      headers: { 'Content-Type': 'application/json' },
      body,
      signal: AbortSignal.timeout(3000),
    });
    if (res.ok) console.log(`[Video 3.7] Données de démo chargées.`);
  } catch (_) {}
}

async function scrollToCenter(page, locator) {
  try {
    await locator.evaluate(el => el.scrollIntoView({ behavior: 'smooth', block: 'center' }));
    await page.waitForTimeout(400);
  } catch (_) {}
}

async function runScenario(page, R) {
  console.log('▶ [Étape 1] Navigation vers cashflow.html');

  await page.goto(`${R.baseUrl}/cashflow.html`, { waitUntil: 'domcontentloaded' });
  await R.reinject();

  await R.subtitle(
    'Certains flux financiers ne sont pas mensuels :\nvoyages, travaux, ou primes variables indexées sur votre salaire.',
    4500
  );

  const sectionOneoff = page.getByText('Dépenses ponctuelles', { exact: true }).locator('xpath=ancestor::div[.//table][1]');
  await sectionOneoff.waitFor({ state: 'visible', timeout: 8000 }).catch(() => {});
  await page.waitForTimeout(1000);
  await R.hideSubtitle();
  await page.waitForTimeout(300);

  // ── 2. Saisie d'une dépense ponctuelle (Vacances été 2027) ──────────────────
  console.log('▶ [Étape 2] Saisie d\'une dépense ponctuelle');

  await scrollToCenter(page, sectionOneoff);
  await R.moveTo(sectionOneoff);
  await R.zoomTo(sectionOneoff, 1.15, 600);

  const btnAddOneoff = sectionOneoff.locator('button:has-text("Ajouter une ligne")').first();
  await R.moveTo(btnAddOneoff);
  await R.clickOn(btnAddOneoff);
  await page.waitForTimeout(400);

  const rowOneoff = sectionOneoff.locator('table').first().locator('tbody tr').last();
  const inpsOneoff = rowOneoff.locator('input');

  // Libellé
  await R.moveTo(inpsOneoff.nth(0));
  await R.fillField(inpsOneoff.nth(0), 'Vacances été 2027');

  // Date
  await R.moveTo(inpsOneoff.nth(1));
  await R.fillField(inpsOneoff.nth(1), '2027-07-01');

  // Montant (€)
  await R.moveTo(inpsOneoff.nth(2));
  await R.fillField(inpsOneoff.nth(2), '2800');

  await R.subtitle(
    'Cette dépense de 2 800 € ne sera déduite qu\'en juillet 2027,\nsans altérer le reste de votre budget prévisionnel.',
    5000,
  );

  await R.zoomOut(500);
  await R.pause(400);

  // ── 3. Saisie d'une prime variable indexée sur le salaire ──────────────────
  console.log('▶ [Étape 3] Primes & intéressement');

  const sectionPrimes = page.getByText('Primes, participation & intéressement', { exact: true }).locator('xpath=ancestor::div[.//table][1]');
  await scrollToCenter(page, sectionPrimes);
  await R.moveTo(sectionPrimes);
  await R.zoomTo(sectionPrimes, 1.15, 600);

  const btnAddPrime = sectionPrimes.locator('button:has-text("Ajouter une ligne")').first();
  await R.moveTo(btnAddPrime);
  await R.clickOn(btnAddPrime);
  await page.waitForTimeout(400);

  const rowPrime = sectionPrimes.locator('table').first().locator('tbody tr').last();
  const inpsPrime = rowPrime.locator('input, select');

  // Libellé
  await R.moveTo(inpsPrime.nth(0));
  await R.fillField(inpsPrime.nth(0), 'Prime annuelle Thomas');

  // Type
  await R.moveTo(inpsPrime.nth(1));
  await R.fillField(inpsPrime.nth(1), 'Prime');

  // Revenu de référence (select)
  const selectRef = inpsPrime.nth(2);
  if (await selectRef.isVisible().catch(() => false)) {
    await R.moveTo(selectRef);
    await selectRef.selectOption({ label: 'Salaire Thomas' }).catch(() => {});
  }

  // Taux (%)
  await R.moveTo(inpsPrime.nth(3));
  await R.fillField(inpsPrime.nth(3), '8');

  await R.subtitle(
    '8 % indexés sur le salaire de Thomas : si son salaire progresse,\nla projection de prime s\'ajuste automatiquement.',
    5200,
  );

  await R.zoomOut(500);
  await R.pause(400);

  // ── 4. Clôture ────────────────────────────────────────────────────────────
  console.log('▶ [Étape 4] Clôture');
  await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'smooth' }));
  await R.pause(500);
  await R.subtitle(
    'Dépenses ponctuelles et primes enregistrées !\nNous allons maintenant examiner les jauges d\'indépendance FIRE.',
    4800,
  );
  await R.pause(1200);
}

(async () => {
  console.log('⏺  Vidéo 3.7 — Dépenses ponctuelles et primes');
  const server = await detectServer();
  await loadDemoData(server.api);

  const { browser, context, page, recorder } = await createSession({
    outputDir: OUTPUT_DIR,
    baseUrl: server.front,
  });

  recorder.baseUrl = server.front;

  try {
    await runScenario(page, recorder);
  } catch (err) {
    console.error('Erreur pendant le scénario :', err);
  } finally {
    await finalizeVideo(context, page, 'video-37-depenses-ponctuelles.webm', OUTPUT_DIR);
    await browser.close();
  }
})();
