'use strict';
/**
 * Vidéo 3.8 — Vue d'ensemble : jauges FIRE et indépendance financière
 *
 * Usage : node scripts/videos/run.js 3.8
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
    if (res.ok) console.log(`[Video 3.8] Données de démo chargées.`);
  } catch (_) {}
}

async function scrollToCenter(page, locator) {
  try {
    await locator.evaluate(el => el.scrollIntoView({ behavior: 'smooth', block: 'center' }));
    await page.waitForTimeout(400);
  } catch (_) {}
}

async function runScenario(page, R) {
  console.log('▶ [Étape 1] Navigation vers overview.html');

  await page.goto(`${R.baseUrl}/overview.html`, { waitUntil: 'domcontentloaded' });
  await R.reinject();

  await R.subtitle(
    'Les jauges FIRE mesurent votre indépendance financière future :\nvotre patrimoine et vos pensions couvriront-ils vos charges ?',
    4500
  );

  const cardFIRE1 = page.getByText('Indépendance Financière (FIRE) — Règle des 4 %', { exact: false }).locator('xpath=ancestor::div[contains(@style, "margin")][1]');
  await cardFIRE1.waitFor({ state: 'visible', timeout: 8000 }).catch(() => {});
  await page.waitForTimeout(1000);
  await R.hideSubtitle();
  await page.waitForTimeout(300);

  // ── 2. Jauge 1 : Règle des 4 % (Liquidation totale) ────────────────────────
  console.log('▶ [Étape 2] Jauge Règle des 4 %');

  await scrollToCenter(page, cardFIRE1);
  await R.moveTo(cardFIRE1);
  await R.highlight(cardFIRE1);
  await R.zoomTo(cardFIRE1, 1.2, 600);

  await R.subtitle(
    'La première jauge applique la règle des 4 % sur TOUT votre patrimoine,\nen supposant la liquidation de vos placements ET de votre immobilier.',
    5500,
  );
  await R.subtitle(
    '⚠️ Votre résidence principale n\'étant liquide que si vous la vendez,\ncette jauge constitue un scénario théorique optimiste.',
    5200,
  );

  await R.clearHighlights();
  await R.zoomOut(500);
  await R.pause(400);

  // ── 3. Jauge 2 : Couverture réaliste (Pensions + Placements seuls) ──────────
  console.log('▶ [Étape 3] Jauge de Couverture réaliste');

  const cardFIRE2 = page.getByText('Couverture réaliste du niveau de vie', { exact: false }).locator('xpath=ancestor::div[contains(@style, "margin")][1]');
  await scrollToCenter(page, cardFIRE2);
  await R.moveTo(cardFIRE2);
  await R.highlight(cardFIRE2);
  await R.zoomTo(cardFIRE2, 1.2, 600);

  await R.subtitle(
    'La seconde jauge est beaucoup plus réaliste :\nl\'immobilier n\'est PAS vendu, seuls les placements et les pensions sont comptés.',
    5500,
  );
  await R.subtitle(
    'C\'est l\'indicateur clé à surveiller pour vous assurer\nque votre train de vie sera intégralement couvert à la retraite.',
    5200,
  );

  await R.clearHighlights();
  await R.zoomOut(500);
  await R.pause(400);

  // ── 4. Clôture ────────────────────────────────────────────────────────────
  console.log('▶ [Étape 4] Clôture');
  await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'smooth' }));
  await R.pause(500);
  await R.subtitle(
    'Ces jauges s\'alimentent directement de vos estimations de retraite,\nque nous allons détailler dans la prochaine vidéo.',
    5000,
  );
  await R.pause(1200);
}

(async () => {
  console.log('⏺  Vidéo 3.8 — Vue d\'ensemble : jauges FIRE et indépendance financière');
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
    await finalizeVideo(context, page, 'video-38-jauges-fire.webm', OUTPUT_DIR);
    await browser.close();
  }
})();
