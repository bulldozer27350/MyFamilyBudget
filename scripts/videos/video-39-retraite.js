'use strict';
/**
 * Vidéo 3.9 — Retraite : estimation des pensions
 *
 * Usage : node scripts/videos/run.js 3.9
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
    if (res.ok) console.log(`[Video 3.9] Données de démo chargées.`);
  } catch (_) {}
}

async function scrollToCenter(page, locator) {
  try {
    await locator.evaluate(el => el.scrollIntoView({ behavior: 'smooth', block: 'center' }));
    await page.waitForTimeout(400);
  } catch (_) {}
}

async function runScenario(page, R) {
  console.log('▶ [Étape 1] Navigation vers retraite.html');

  await page.goto(`${R.baseUrl}/retraite.html`, { waitUntil: 'domcontentloaded' });
  await R.reinject();

  await R.subtitle(
    'La page Retraite estime vos futures pensions (Régime Général & Agirc-Arrco)\ndirectement à partir de vos trimestres et historiques.',
    4500
  );

  const sectionIdentite = page.getByText('Identité & réglages carrière', { exact: true }).locator('xpath=ancestor::div[contains(@style, "margin")][1]');
  await sectionIdentite.waitFor({ state: 'visible', timeout: 8000 }).catch(() => {});
  await page.waitForTimeout(1000);
  await R.hideSubtitle();
  await page.waitForTimeout(300);

  // ── 2. Réglages de la carrière de Thomas ──────────────────────────────────
  console.log('▶ [Étape 2] Carrière de Thomas');

  await scrollToCenter(page, sectionIdentite);
  await R.moveTo(sectionIdentite);
  await R.zoomTo(sectionIdentite, 1.15, 600);

  // Revenu lié (select)
  const selectIncome = sectionIdentite.locator('select').first();
  if (await selectIncome.isVisible().catch(() => false)) {
    await R.moveTo(selectIncome);
    await selectIncome.selectOption({ label: 'Salaire Thomas' }).catch(() => {});
  }

  await R.subtitle(
    'Lier la personne à son salaire permet au moteur de prolonger\nles trimestres et cotisations futures jusqu\'à l\'âge de départ.',
    5200,
  );

  // Trimestres validés à ce jour
  const inputTrimestres = sectionIdentite.locator('input[type="number"]#trimestres').first();
  if (await inputTrimestres.isVisible().catch(() => false)) {
    await R.moveTo(inputTrimestres);
    await R.fillField(inputTrimestres, '92');
  }

  await R.subtitle(
    'Thomas a 92 trimestres validés à ce jour,\nchiffre disponible sur le relevé de carrière officiel info-retraite.fr.',
    5000,
  );

  await R.zoomOut(500);
  await R.pause(400);

  // ── 3. Analyse des KPIs de projection ─────────────────────────────────────
  console.log('▶ [Étape 3] KPIs de projection de la pension');

  const kpiRow = page.locator('div').filter({ has: page.getByText('Total pension brute / mois', { exact: true }) }).first();
  await scrollToCenter(page, kpiRow);
  await R.moveTo(kpiRow);
  await R.highlight(kpiRow);
  await R.zoomTo(kpiRow, 1.2, 600);

  await R.subtitle(
    'L\'application calcule la pension de base et la complémentaire Agirc-Arrco,\navec prise en compte automatique d\'une éventuelle décote ou surcote.',
    5500,
  );
  await R.subtitle(
    'Ces montants s\'injectent automatiquement dans votre trésorerie à la retraite,\nsans rien avoir à ressaisir manuellement !',
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
    'Projections de retraite prêtes !\nNous allons maintenant examiner la fiscalité et le prélèvement à la source.',
    5000,
  );
  await R.pause(1200);
}

(async () => {
  console.log('⏺  Vidéo 3.9 — Retraite : estimation des pensions');
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
    await finalizeVideo(context, page, 'video-39-retraite.webm', OUTPUT_DIR);
    await browser.close();
  }
})();
