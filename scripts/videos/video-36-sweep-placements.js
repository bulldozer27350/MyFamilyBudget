'use strict';
/**
 * Vidéo 3.6 — Virement automatique Sweep : lier épargne et compte courant
 *
 * Usage : node scripts/videos/run.js 3.6
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
    if (res.ok) console.log(`[Video 3.6] Données de démo chargées.`);
  } catch (_) {}
}

async function scrollToCenter(page, locator) {
  try {
    await locator.evaluate(el => el.scrollIntoView({ behavior: 'smooth', block: 'center' }));
    await page.waitForTimeout(400);
  } catch (_) {}
}

async function runScenario(page, R) {
  console.log('▶ [Étape 1] Navigation vers patrimoine.html');

  await page.goto(`${R.baseUrl}/patrimoine.html`, { waitUntil: 'domcontentloaded' });
  await R.reinject();

  await R.subtitle(
    'Le virement automatique (Sweep) transfère automatiquement les excédents\ndu compte courant vers vos placements cibles.',
    4500
  );

  const cardLivret = page.getByText('Livret A', { exact: false }).first().locator('xpath=ancestor::div[.//button[contains(., "📈")]][1]');
  await cardLivret.waitFor({ state: 'visible', timeout: 8000 }).catch(() => {});
  await page.waitForTimeout(1000);
  await R.hideSubtitle();
  await page.waitForTimeout(300);

  // ── 2. Ouverture du tiroir du Livret A ──────────────────────────────────────
  console.log('▶ [Étape 2] Configuration Sweep sur le Livret A');

  await scrollToCenter(page, cardLivret);
  await R.moveTo(cardLivret);
  await R.clickOn(cardLivret);
  await page.waitForTimeout(600);

  const drawer = page.locator('div[style*="z-index: 1000"], div[style*="zIndex: 1000"]').first();
  await drawer.waitFor({ state: 'visible', timeout: 5000 });

  // Section 4 : Automatisations
  const inputSweepPriority = drawer.locator('input[placeholder*="1, 2"]').first();
  if (await inputSweepPriority.isVisible().catch(() => false)) {
    await scrollToCenter(page, inputSweepPriority);
    await R.moveTo(inputSweepPriority);
    await R.fillField(inputSweepPriority, '1');
  }

  await R.subtitle(
    'Priorité 1 : le Livret A recevra les excédents de trésorerie en premier\ndès que le solde courant dépasse le seuil haut de 6 000 €.',
    5200,
  );

  // Plafond Sweep (22 950 €)
  const inputSweepCap = drawer.locator('input[placeholder*="22950"]').first();
  if (await inputSweepCap.isVisible().catch(() => false)) {
    await R.moveTo(inputSweepCap);
    await R.fillField(inputSweepCap, '22950');
  }

  await R.subtitle(
    'Plafond 22 950 € : le moteur n\'injectera plus d\'argent dessus\nune fois le livret plein, et passera au placement de priorité 2.',
    5000,
  );

  // Priorité pause épargne
  const inputPause = drawer.locator('div').filter({ hasText: /^Priorité pause épargne/ }).locator('input').first();
  if (await inputPause.isVisible().catch(() => false)) {
    await R.moveTo(inputPause);
    await R.fillField(inputPause, '1');
  }

  await R.subtitle(
    'Priorité pause 1 : en cas de coup dur (sous le seuil d\'alerte),\nles versements mensuels de ce livret seront suspendus en priorité.',
    5200,
  );

  // Enregistrer & fermer
  const btnSave = drawer.locator('button:has-text("Enregistrer"), button:has-text("Fermer"), button:has-text("✕")').first();
  await R.moveTo(btnSave);
  await R.clickOn(btnSave);
  await page.waitForTimeout(500);

  // ── 3. Clôture ────────────────────────────────────────────────────────────
  console.log('▶ [Étape 3] Clôture');
  await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'smooth' }));
  await R.pause(500);
  await R.subtitle(
    'Votre stratégie d\'épargne automatique est opérationnelle !\nProchaine étape : les dépenses ponctuelles et les primes.',
    4800,
  );
  await R.pause(1200);
}

(async () => {
  console.log('⏺  Vidéo 3.6 — Virement automatique Sweep : lier épargne et compte courant');
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
    await finalizeVideo(context, page, 'video-36-sweep-placements.webm', OUTPUT_DIR);
    await browser.close();
  }
})();
