'use strict';
/**
 * Vidéo 3.4 — Placements & Patrimoine : créer ses livrets et comptes
 *
 * Usage : node scripts/videos/run.js 3.4
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
    if (res.ok) console.log(`[Video 3.4] Données de démo chargées.`);
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
    'La page Placements & Patrimoine centralise tous vos comptes d\'épargne,\nlivrets et investissements.',
    4500
  );

  const sectionPlacements = page.getByText('Comptes & Placements d\'épargne', { exact: true }).locator('xpath=ancestor::div[.//button][1]');
  await sectionPlacements.waitFor({ state: 'visible', timeout: 8000 }).catch(() => {});
  await page.waitForTimeout(1000);
  await R.hideSubtitle();
  await page.waitForTimeout(300);

  // ── 2. Création d'un placement via le drawer ────────────────────────────────
  console.log('▶ [Étape 2] Ouverture du tiroir de nouveau placement');

  await scrollToCenter(page, sectionPlacements);
  await R.moveTo(sectionPlacements);

  const btnAdd = page.locator('button:has-text("+ Ajouter un nouveau placement")').first();
  await scrollToCenter(page, btnAdd);
  await R.moveTo(btnAdd);
  await R.clickOn(btnAdd);
  await page.waitForTimeout(500);

  // Le drawer est ouvert
  const drawer = page.locator('div[style*="z-index: 1000"], div[style*="zIndex: 1000"]').first();
  await drawer.waitFor({ state: 'visible', timeout: 5000 });

  await R.subtitle(
    'Le tiroir latéral permet de configurer le placement :\nsa catégorie d\'actif, son versement programmé et ses rendements.',
    5000,
  );

  // Libellé
  const inputLabel = drawer.locator('input[placeholder*="Livret A"]').first();
  await R.moveTo(inputLabel);
  await R.fillField(inputLabel, 'Livret A');

  // Catégorie
  const selectCat = drawer.locator('select').first();
  if (await selectCat.isVisible().catch(() => false)) {
    await R.moveTo(selectCat);
    await selectCat.selectOption({ label: 'Livret' }).catch(() => {});
  }

  // Versement mensuel (section 2)
  const inputMonthly = drawer.locator('div').filter({ hasText: /^Versement mensuel/ }).locator('input').first();
  if (await inputMonthly.isVisible().catch(() => false)) {
    await scrollToCenter(page, inputMonthly);
    await R.moveTo(inputMonthly);
    await R.fillField(inputMonthly, '150');
  }

  // Rendements (3 scénarios : pess 2.4, corr 3.0, opti 3.5)
  const inputPess = drawer.locator('div').filter({ hasText: /^Pessimiste/ }).locator('input').first();
  const inputCorr = drawer.locator('div').filter({ hasText: /^Correct/ }).locator('input').first();
  const inputOpti = drawer.locator('div').filter({ hasText: /^Optimiste/ }).locator('input').first();

  if (await inputPess.isVisible().catch(() => false)) {
    await scrollToCenter(page, inputPess);
    await R.moveTo(inputPess);
    await R.fillField(inputPess, '2.4');
    await R.moveTo(inputCorr);
    await R.fillField(inputCorr, '3.0');
    await R.moveTo(inputOpti);
    await R.fillField(inputOpti, '3.5');
  }

  await R.subtitle(
    'On définit 3 hypothèses de taux : 2.4 %, 3.0 % et 3.5 %.\nPour un livret réglementé, la fourchette reste resserrée.',
    5000,
  );

  // Enregistrement
  const btnSave = drawer.locator('button:has-text("Créer le placement"), button:has-text("Enregistrer")').first();
  await R.moveTo(btnSave);
  await R.clickOn(btnSave);
  await page.waitForTimeout(600);

  // ── 3. Saisie du solde actuel via la fenêtre Historique (📈) ────────────────
  console.log('▶ [Étape 3] Fenêtre Historique 📈 et saisie du solde');

  // Carte du Livret A
  const cardLivret = page.getByText('Livret A', { exact: false }).first().locator('xpath=ancestor::div[.//button[contains(., "📈")]][1]');
  await scrollToCenter(page, cardLivret);
  await R.moveTo(cardLivret);

  const btnHistory = cardLivret.locator('button:has-text("📈")').first();
  await R.moveTo(btnHistory);
  await R.highlight(btnHistory);
  await R.subtitle(
    'Pour saisir le solde constaté du placement,\ncliquez sur le bouton 📈 Historique.',
    4500,
  );
  await R.clearHighlights();
  await R.clickOn(btnHistory);
  await page.waitForTimeout(600);

  // Modale Historique
  const modalHistory = page.locator('div[style*="z-index: 1000"], div[style*="zIndex: 1000"]').first();
  await modalHistory.waitFor({ state: 'visible', timeout: 5000 });

  const btnAddHist = modalHistory.locator('button:has-text("Ajouter une ligne")').first();
  await scrollToCenter(page, btnAddHist);
  await R.moveTo(btnAddHist);
  await R.clickOn(btnAddHist);
  await page.waitForTimeout(400);

  // Saisie du solde (8500 €)
  const lastHistRow = modalHistory.locator('table tbody tr').last();
  const inputValue = lastHistRow.locator('input[type="number"]').first();
  await R.moveTo(inputValue);
  await R.fillField(inputValue, '8500');

  await R.subtitle(
    'Le solde actuel est fixé à 8 500 €.\nCette valeur sert de point de départ aux 3 courbes de projection.',
    5000,
  );

  // Fermeture de la modale historique
  const btnCloseHist = modalHistory.locator('button:has-text("✕")').first();
  await R.moveTo(btnCloseHist);
  await R.clickOn(btnCloseHist);
  await page.waitForTimeout(500);

  // ── 4. Clôture ────────────────────────────────────────────────────────────
  console.log('▶ [Étape 4] Clôture');
  await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'smooth' }));
  await R.pause(500);
  await R.subtitle(
    'Le Livret A est configuré ! Vous pouvez ainsi créer tous vos livrets,\nassurances-vie, PEA ou comptes d\'épargne.',
    5200,
  );
  await R.pause(1200);
}

(async () => {
  console.log('⏺  Vidéo 3.4 — Placements & Patrimoine : créer ses livrets et comptes');
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
    await finalizeVideo(context, page, 'video-34-placements-patrimoine.webm', OUTPUT_DIR);
    await browser.close();
  }
})();
