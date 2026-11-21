'use strict';
/**
 * Vidéo 3.10 — Impôts : Prélèvement à la Source et simulation
 *
 * Usage : node scripts/videos/run.js 3.10
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
    if (res.ok) console.log(`[Video 3.10] Données de démo chargées.`);
  } catch (_) {}
}

async function scrollToCenter(page, locator) {
  try {
    await locator.evaluate(el => el.scrollIntoView({ behavior: 'smooth', block: 'center' }));
    await page.waitForTimeout(400);
  } catch (_) {}
}

async function runScenario(page, R) {
  console.log('▶ [Étape 1] Navigation vers impots.html');

  await page.goto(`${R.baseUrl}/impots.html`, { waitUntil: 'domcontentloaded' });
  await R.reinject();

  await R.subtitle(
    'La page Impôts modélise la fiscalité de votre foyer\npour calculer le Prélèvement à la Source (PAS) et l\'impôt net.',
    4500
  );

  const sectionFoyer = page.getByText('Foyer fiscal', { exact: true }).locator('xpath=ancestor::div[contains(@style, "margin")][1]');
  await sectionFoyer.waitFor({ state: 'visible', timeout: 8000 }).catch(() => {});
  await page.waitForTimeout(1000);
  await R.hideSubtitle();
  await page.waitForTimeout(300);

  // ── 2. Foyer fiscal & Parts ───────────────────────────────────────────────
  console.log('▶ [Étape 2] Foyer fiscal et parts');

  await scrollToCenter(page, sectionFoyer);
  await R.moveTo(sectionFoyer);
  await R.zoomTo(sectionFoyer, 1.15, 600);

  await R.subtitle(
    'Pour un couple avec 2 enfants : 2 parts adultes + 1 part pour les enfants,\nsoit 3 parts fiscales au barème progressif.',
    5200,
  );
  await R.subtitle(
    'L\'abattement de 10 % pour frais professionnels s\'applique par défaut\nsur l\'ensemble des revenus d\'activité.',
    4800,
  );

  await R.zoomOut(500);
  await R.pause(400);

  // ── 3. KPIs fiscaux et taux de PAS ─────────────────────────────────────────
  console.log('▶ [Étape 3] KPIs fiscaux');

  const kpisFiscaux = page.locator('div').filter({ has: page.getByText('Taux de PAS estimé', { exact: true }) }).first();
  await scrollToCenter(page, kpisFiscaux);
  await R.moveTo(kpisFiscaux);
  await R.highlight(kpisFiscaux);
  await R.zoomTo(kpisFiscaux, 1.2, 600);

  await R.subtitle(
    'L\'outil simule automatiquement votre taux de prélèvement à la source\net mensualise l\'impôt directement dans votre budget.',
    5200,
  );

  await R.clearHighlights();
  await R.zoomOut(500);
  await R.pause(400);

  // ── 4. Ajustements réels (Avis d'imposition) ──────────────────────────────
  console.log('▶ [Étape 4] Ajustements réels');

  const sectionAjust = page.getByText('Impôt réel constaté (avis d\'imposition)', { exact: true }).locator('xpath=ancestor::div[contains(@style, "margin")][1]');
  await scrollToCenter(page, sectionAjust);
  await R.moveTo(sectionAjust);
  await R.zoomTo(sectionAjust, 1.15, 600);

  await R.subtitle(
    'Dès réception de votre avis officiel, saisissez le montant d\'impôt réel :\nl\'écart avec la prévision générera un ajustement automatique.',
    5500,
  );

  await R.zoomOut(500);
  await R.pause(400);

  // ── 5. Clôture ────────────────────────────────────────────────────────────
  console.log('▶ [Étape 5] Clôture');
  await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'smooth' }));
  await R.pause(500);
  await R.subtitle(
    'Fiscalité maîtrisée ! Dans l\'ultime vidéo de cette série,\nnous activerons la Date Pivot en mode automatique.',
    5000,
  );
  await R.pause(1200);
}

(async () => {
  console.log('⏺  Vidéo 3.10 — Impôts : Prélèvement à la Source et simulation');
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
    await finalizeVideo(context, page, 'video-310-impots.webm', OUTPUT_DIR);
    await browser.close();
  }
})();
