'use strict';
/**
 * Vidéo 3.1 — Saisir ses revenus récurrents (Trésorerie)
 *
 * Usage : node scripts/videos/run.js 3.1
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
    if (res.ok) console.log(`[Video 3.1] Données de démo chargées.`);
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
    'La page Trésorerie est le cœur de votre budget prévisionnel :\non y saisit les revenus et les charges de chaque mois.',
    4500
  );

  const sectionRevenus = page.getByText('Revenus récurrents', { exact: true }).locator('xpath=ancestor::div[.//table][1]');
  await sectionRevenus.waitFor({ state: 'visible', timeout: 8000 }).catch(() => {});
  await page.waitForTimeout(1000);
  await R.hideSubtitle();
  await page.waitForTimeout(300);

  // ── 2. Focus sur la section Revenus récurrents ──────────────────────────────
  console.log('▶ [Étape 2] Saisie du revenu de Thomas');

  await scrollToCenter(page, sectionRevenus);
  await R.moveTo(sectionRevenus);
  await R.zoomTo(sectionRevenus, 1.15, 600);

  const btnAdd = sectionRevenus.locator('button:has-text("Ajouter une ligne")').first();
  await R.clickOn(btnAdd);
  await page.waitForTimeout(400);

  // Dernière ligne du tableau des revenus
  const rowThomas = sectionRevenus.locator('table tbody tr').last();
  const inputsThomas = rowThomas.locator('input');

  // Libellé (index 0)
  const inputLabelT = inputsThomas.nth(0);
  await R.moveTo(inputLabelT);
  await R.fillField(inputLabelT, 'Salaire Thomas');

  // Montant (index 1)
  const inputAmountT = inputsThomas.nth(1);
  await R.moveTo(inputAmountT);
  await R.fillField(inputAmountT, '1800');

  // Augmentation annuelle % (index 2)
  const inputGrowthT = inputsThomas.nth(2);
  await R.moveTo(inputGrowthT);
  await R.fillField(inputGrowthT, '2');

  // Date de début (index 3)
  const inputStartT = inputsThomas.nth(3);
  await R.moveTo(inputStartT);
  await R.fillField(inputStartT, '2024-01-01');

  await R.subtitle(
    'Thomas perçoit 1 800 € nets par mois depuis janvier 2024,\navec une prévision d\'augmentation de 2 % par an.',
    4500,
  );

  // ── 3. Saisie du revenu de Sophie ──────────────────────────────────────────
  console.log('▶ [Étape 3] Saisie du revenu de Sophie');

  await R.clickOn(btnAdd);
  await page.waitForTimeout(400);

  const rowSophie = sectionRevenus.locator('table tbody tr').last();
  const inputsSophie = rowSophie.locator('input');

  const inputLabelS = inputsSophie.nth(0);
  await R.moveTo(inputLabelS);
  await R.fillField(inputLabelS, 'Salaire Sophie');

  const inputAmountS = inputsSophie.nth(1);
  await R.moveTo(inputAmountS);
  await R.fillField(inputAmountS, '2500');

  const inputGrowthS = inputsSophie.nth(2);
  await R.moveTo(inputGrowthS);
  await R.fillField(inputGrowthS, '2');

  const inputStartS = inputsSophie.nth(3);
  await R.moveTo(inputStartS);
  await R.fillField(inputStartS, '2024-01-01');

  await R.subtitle(
    'Sophie gagne 2 500 € nets par mois, augmentés également de 2 % par an.',
    4200,
  );

  await R.zoomOut(500);
  await R.pause(400);

  // ── 4. Graphique de projection de trésorerie ───────────────────────────────
  console.log('▶ [Étape 4] Graphique de projection en direct');

  const canvasTreso = page.locator('canvas').first();
  await scrollToCenter(page, canvasTreso);
  await R.moveTo(canvasTreso);
  await R.highlight(canvasTreso);
  await R.zoomTo(canvasTreso, 1.2, 600);

  await R.subtitle(
    'Le graphique se met à jour en temps réel avec les nouveaux revenus saisis,\ntraçant l\'accumulation d\'épargne prévisionnelle.',
    5000,
  );

  await R.clearHighlights();
  await R.zoomOut(500);
  await R.pause(500);

  // ── 5. Clôture ────────────────────────────────────────────────────────────
  console.log('▶ [Étape 5] Clôture');
  await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'smooth' }));
  await R.pause(500);
  await R.subtitle(
    'Revenus enregistrés ! Dans la vidéo suivante,\nnous allons saisir les charges récurrentes du foyer.',
    4500,
  );
  await R.pause(1200);
}

(async () => {
  console.log('⏺  Vidéo 3.1 — Saisir ses revenus récurrents (Trésorerie)');
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
    await finalizeVideo(context, page, 'video-31-revenus-recurrents.webm', OUTPUT_DIR);
    await browser.close();
  }
})();
