'use strict';
/**
 * Vidéo 3.11 — Date Pivot : basculer du réel vers le prévisionnel
 *
 * Usage : node scripts/videos/run.js 3.11
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
    if (res.ok) console.log(`[Video 3.11] Données de démo chargées.`);
  } catch (_) {}
}

async function scrollToCenter(page, locator) {
  try {
    await locator.evaluate(el => el.scrollIntoView({ behavior: 'smooth', block: 'center' }));
    await page.waitForTimeout(400);
  } catch (_) {}
}

async function runScenario(page, R) {
  console.log('▶ [Étape 1] Navigation vers settings.html');

  await page.goto(`${R.baseUrl}/settings.html`, { waitUntil: 'domcontentloaded' });
  await R.reinject();

  await R.subtitle(
    'La Date Pivot est la frontière exacte\nentre votre historique bancaire réel et vos projections prévisionnelles.',
    4500
  );

  const sectionPivot = page.getByText('Date Pivot — Point de bascule', { exact: false }).locator('xpath=ancestor::div[contains(@style, "margin")][1]');
  await sectionPivot.waitFor({ state: 'visible', timeout: 8000 }).catch(() => {});
  await page.waitForTimeout(1000);
  await R.hideSubtitle();
  await page.waitForTimeout(300);

  // ── 2. Caler sur le dernier import ────────────────────────────────────────
  console.log('▶ [Étape 2] Bouton Caler sur le dernier import');

  await scrollToCenter(page, sectionPivot);
  await R.moveTo(sectionPivot);
  await R.zoomTo(sectionPivot, 1.15, 600);

  const btnCaler = sectionPivot.locator('button:has-text("Caler sur le dernier import")').first();
  if (await btnCaler.isVisible().catch(() => false)) {
    await R.moveTo(btnCaler);
    await R.highlight(btnCaler);
    await R.subtitle(
      'Ce bouton cale instantanément la Date Pivot\nsur la transaction bancaire importée la plus récente.',
      5000,
    );
    await R.clearHighlights();
    await R.clickOn(btnCaler);
    await page.waitForTimeout(500);
  }

  // ── 3. Bascule en mode Automatique ─────────────────────────────────────────
  console.log('▶ [Étape 3] Mode Automatique et solde réel');

  const radioAuto = sectionPivot.locator('input[type="radio"][value="auto"]').first();
  if (await radioAuto.isVisible().catch(() => false)) {
    await R.moveTo(radioAuto);
    await R.clickOn(radioAuto);
    await page.waitForTimeout(500);
  }

  // Bandeau vert ✅ Solde réel constaté
  const bandeauVert = sectionPivot.locator('div').filter({ hasText: /^✅ Solde réel constaté/ }).first();
  if (await bandeauVert.isVisible().catch(() => false)) {
    await R.moveTo(bandeauVert);
    await R.highlight(bandeauVert);
    await R.subtitle(
      'Le solde réel est calculé depuis toutes vos opérations réelles jusqu\'à cette date,\nremplaçant définitivement l\'estimation de départ.',
      5500,
    );
    await R.clearHighlights();
  }

  await R.subtitle(
    'Dans tous les graphiques : à gauche de la date pivot = le réel constaté,\nà droite = la projection fidèle de votre avenir financier.',
    5500,
  );

  await R.zoomOut(500);
  await R.pause(400);

  // ── 4. Clôture de la formation ────────────────────────────────────────────
  console.log('▶ [Étape 4] Clôture globale de la série');
  await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'smooth' }));
  await R.pause(500);
  await R.subtitle(
    'Félicitations ! Vous maîtrisez désormais l\'ensemble de MyFamilyBudget :\nde la gestion du quotidien jusqu\'aux projections patrimoniales.',
    5500,
  );
  await R.pause(1500);
}

(async () => {
  console.log('⏺  Vidéo 3.11 — Date Pivot : basculer du réel vers le prévisionnel');
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
    await finalizeVideo(context, page, 'video-311-date-pivot.webm', OUTPUT_DIR);
    await browser.close();
  }
})();
