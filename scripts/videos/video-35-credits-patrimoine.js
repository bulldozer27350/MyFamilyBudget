'use strict';
/**
 * Vidéo 3.5 — Placements : crédits immobiliers (Passif)
 *
 * Usage : node scripts/videos/run.js 3.5
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
    if (res.ok) console.log(`[Video 3.5] Données de démo chargées.`);
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
    'Vos crédits immobiliers ont un double rôle dans MyFamilyBudget :\nune charge mensuelle en Trésorerie, et votre passif dans Patrimoine.',
    4500
  );

  const sectionCredits = page.getByText('Crédits & Passif Immobilier', { exact: false }).locator('xpath=ancestor::div[.//button][1]');
  await sectionCredits.waitFor({ state: 'visible', timeout: 8000 }).catch(() => {});
  await page.waitForTimeout(1000);
  await R.hideSubtitle();
  await page.waitForTimeout(300);

  // ── 2. Saisie du Crédit immobilier principal dans Patrimoine ───────────────
  console.log('▶ [Étape 2] Saisie du Crédit immobilier principal');

  await scrollToCenter(page, sectionCredits);
  await R.moveTo(sectionCredits);
  await R.zoomTo(sectionCredits, 1.15, 600);

  const btnAdd = sectionCredits.locator('button:has-text("+ Ajouter un prêt")').first();
  await R.moveTo(btnAdd);
  await R.clickOn(btnAdd);
  await page.waitForTimeout(500);

  const drawer = page.locator('div[style*="z-index: 1000"], div[style*="zIndex: 1000"]').first();
  await drawer.waitFor({ state: 'visible', timeout: 5000 });

  // Libellé
  const inputLabel = drawer.locator('input[type="text"]').first();
  await R.moveTo(inputLabel);
  await R.fillField(inputLabel, 'Crédit immobilier principal');

  // Taux hors assurance (%)
  const inputRate = drawer.locator('div').filter({ hasText: /^Taux hors assurance/ }).locator('input').first();
  if (await inputRate.isVisible().catch(() => false)) {
    await R.moveTo(inputRate);
    await R.fillField(inputRate, '1.85');
  }

  // Mensualité
  const inputMonthly = drawer.locator('div').filter({ hasText: /^Mensualité/ }).locator('input').first();
  if (await inputMonthly.isVisible().catch(() => false)) {
    await R.moveTo(inputMonthly);
    await R.fillField(inputMonthly, '820');
  }

  // CRD au relevé
  const inputCRD = drawer.locator('div').filter({ hasText: /^CRD au relevé/ }).locator('input').first();
  if (await inputCRD.isVisible().catch(() => false)) {
    await scrollToCenter(page, inputCRD);
    await R.moveTo(inputCRD);
    await R.fillField(inputCRD, '142000');
  }

  await R.subtitle(
    'Le Capital Restant Dû (CRD) représente la dette à rembourser.\nIl est amorti mois par mois jusqu\'à l\'échéance du contrat.',
    5200,
  );

  // Enregistrer
  const btnSave = drawer.locator('button:has-text("Enregistrer")').first();
  if (await btnSave.isVisible().catch(() => false)) {
    await R.moveTo(btnSave);
    await R.clickOn(btnSave);
  } else {
    const btnClose = drawer.locator('button:has-text("✕")').first();
    await R.moveTo(btnClose);
    await R.clickOn(btnClose);
  }
  await page.waitForTimeout(500);

  // ── 3. Synthèse des prêts et impact sur le patrimoine net ──────────────────
  console.log('▶ [Étape 3] Synthèse du passif');

  await scrollToCenter(page, sectionCredits);
  await R.moveTo(sectionCredits);
  await R.highlight(sectionCredits);

  await R.subtitle(
    'Ce passif total est automatiquement soustrait de vos actifs bruts\npour calculer votre patrimoine net réel.',
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
    'Vos crédits et passifs sont en place !\nDans la vidéo suivante, nous relierons l\'épargne via le virement Sweep.',
    5000,
  );
  await R.pause(1200);
}

(async () => {
  console.log('⏺  Vidéo 3.5 — Placements : crédits immobiliers (Passif)');
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
    await finalizeVideo(context, page, 'video-35-credits-patrimoine.webm', OUTPUT_DIR);
    await browser.close();
  }
})();
