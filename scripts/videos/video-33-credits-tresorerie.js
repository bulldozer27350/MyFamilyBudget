'use strict';
/**
 * Vidéo 3.3 — Remboursements de crédits (charges liées aux prêts)
 *
 * Usage : node scripts/videos/run.js 3.3
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
    if (res.ok) console.log(`[Video 3.3] Données de démo chargées.`);
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
    'Sophie et Thomas remboursent deux crédits immobiliers :\nleurs mensualités sont des charges budgétaires avec une date de fin.',
    4500
  );

  const sectionCharges = page.getByText('Charges récurrentes', { exact: true }).locator('xpath=ancestor::div[.//table][1]');
  await sectionCharges.waitFor({ state: 'visible', timeout: 8000 }).catch(() => {});
  await page.waitForTimeout(1000);
  await R.hideSubtitle();
  await page.waitForTimeout(300);

  // ── 2. Crédit immobilier principal ─────────────────────────────────────────
  console.log('▶ [Étape 2] Saisie du Crédit immobilier principal');

  await scrollToCenter(page, sectionCharges);
  await R.moveTo(sectionCharges);
  await R.zoomTo(sectionCharges, 1.15, 600);

  const btnAdd = sectionCharges.locator('button:has-text("Ajouter une ligne")').first();
  await R.clickOn(btnAdd);
  await page.waitForTimeout(400);

  const rowCredit1 = sectionCharges.locator('table tbody tr').last();
  const inps1 = rowCredit1.locator('input');

  await R.moveTo(inps1.nth(0));
  await R.fillField(inps1.nth(0), 'Crédit immobilier principal');

  await R.moveTo(inps1.nth(1));
  await R.fillField(inps1.nth(1), '820');

  // Croissance 0 %
  await R.moveTo(inps1.nth(2));
  await R.fillField(inps1.nth(2), '0');

  // Début
  await R.moveTo(inps1.nth(3));
  await R.fillField(inps1.nth(3), '2020-09-01');

  // Fin
  await R.moveTo(inps1.nth(4));
  await R.fillField(inps1.nth(4), '2045-09-01');

  await R.subtitle(
    'La date de fin indique à l\'application d\'arrêter automatiquement\ncette mensualité à l\'échéance du prêt.',
    4800,
  );

  // ── 3. Crédit agrandissement (fin en 2029) ──────────────────────────────────
  console.log('▶ [Étape 3] Saisie du Crédit agrandissement');

  await R.clickOn(btnAdd);
  await page.waitForTimeout(400);

  const rowCredit2 = sectionCharges.locator('table tbody tr').last();
  const inps2 = rowCredit2.locator('input');

  await R.moveTo(inps2.nth(0));
  await R.fillField(inps2.nth(0), 'Crédit agrandissement');

  await R.moveTo(inps2.nth(1));
  await R.fillField(inps2.nth(1), '350');

  await R.moveTo(inps2.nth(2));
  await R.fillField(inps2.nth(2), '0');

  await R.moveTo(inps2.nth(3));
  await R.fillField(inps2.nth(3), '2022-03-01');

  await R.moveTo(inps2.nth(4));
  await R.fillField(inps2.nth(4), '2029-03-01');

  await R.subtitle(
    'Ce prêt se termine en mars 2029 : dès cette date,\nl\'application libérera automatiquement 350 € de trésorerie par mois.',
    5000,
  );

  await R.zoomOut(500);
  await R.pause(400);

  // ── 4. Observation sur le graphique ────────────────────────────────────────
  console.log('▶ [Étape 4] Impact sur la courbe après 2029');

  const canvasTreso = page.locator('canvas').first();
  await scrollToCenter(page, canvasTreso);
  await R.moveTo(canvasTreso);
  await R.highlight(canvasTreso);
  await R.zoomTo(canvasTreso, 1.2, 600);

  await R.subtitle(
    'Sur la courbe, la pente d\'accumulation s\'accélère nettement après 2029\ngrâce à la fin du crédit d\'agrandissement.',
    5200,
  );

  await R.clearHighlights();
  await R.zoomOut(500);
  await R.pause(400);

  // ── 5. Clôture ────────────────────────────────────────────────────────────
  console.log('▶ [Étape 5] Clôture');
  await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'smooth' }));
  await R.pause(500);
  await R.subtitle(
    'Pour calculer votre passif et votre patrimoine net,\nles crédits se configurent également dans l\'onglet Patrimoine.',
    5000,
  );
  await R.pause(1200);
}

(async () => {
  console.log('⏺  Vidéo 3.3 — Remboursements de crédits (charges liées aux prêts)');
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
    await finalizeVideo(context, page, 'video-33-credits-tresorerie.webm', OUTPUT_DIR);
    await browser.close();
  }
})();
