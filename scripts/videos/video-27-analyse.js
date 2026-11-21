'use strict';
/**
 * Vidéo 2.7 — Analyse : comprendre ses dépenses
 *
 * Ce que montre cette vidéo :
 *  - Indicateurs clés (Dépenses, Revenus, Postes réductibles)
 *  - Visualisation des postes de dépenses du plus élevé au plus faible
 *  - Onglet « Atterrissage du Mois » (Réel vs Budget du mois en cours)
 *  - Onglet « Dérives par Ligne » (Identification des écarts structurels)
 *
 * Usage : node scripts/videos/run.js 2.7
 */

const path = require('path');
const fs = require('fs');
const { createSession, finalizeVideo, detectServer } = require('./helpers/recorder');

const OUTPUT_DIR = path.join(__dirname, 'output');
const DEMO_JSON = path.join(__dirname, '..', '..', 'data', 'budget-familial-demo.json');

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
    if (res.ok) console.log(`[Video 2.7] Données de démo chargées.`);
  } catch (_) {}
}

async function runScenario(page, R) {

  // ── 1. Navigation vers Analyse ─────────────────────────────────────────────
  console.log('▶ [Étape 1] Navigation vers analyse.html');
  await R.navigate('analyse.html', 'button:has-text("Vue Générale")');

  await R.subtitle(
    'L\'onglet Analyse transforme vos données bancaires\n' +
    'en enseignements clairs sur vos habitudes financières.',
    4500,
  );
  await R.pause(500);

  // ── 2. Les indicateurs clés (Vue Générale) ─────────────────────────────────
  console.log('▶ [Étape 2] Indicateurs clés');
  const kpiExpenses = page.locator(':has-text("Dépenses")').first();
  await R.moveTo(kpiExpenses);
  await R.zoomTo(kpiExpenses, 1.25, 600);
  await R.subtitle('Dépenses et Revenus résument votre train de vie sur la période.', 4000);

  const kpiCompressible = page.locator(':has-text("Postes réductibles")').first();
  await R.moveTo(kpiCompressible);
  await R.highlight(kpiCompressible);
  await R.subtitle(
    'Les catégories marquées « réductibles » sont isolées ici :\n' +
    'c\'est votre marge de manœuvre en cas d\'imprévu.',
    5500,
  );
  await R.clearHighlights();
  await R.zoomOut(500);
  await R.pause(500);

  // ── 3. Postes de dépense classés ──────────────────────────────────────────
  console.log('▶ [Étape 3] Classement des dépenses');
  const tableSection = page.locator(':has-text("Postes de dépense, du plus élevé au plus faible")').first();
  await R.moveTo(tableSection);
  await R.highlight(tableSection);
  await R.subtitle(
    'Vos dépenses réelles sont classées de la plus importante à la plus légère.',
    4500,
  );
  await R.clearHighlights();
  await R.pause(600);

  // ── 4. Atterrissage du Mois ───────────────────────────────────────────────
  console.log('▶ [Étape 4] Atterrissage du Mois');
  const landingTab = page.locator('button:has-text("Atterrissage du Mois")');
  await R.moveTo(landingTab);
  await R.clickOn(landingTab);
  await R.pause(700);

  await R.subtitle(
    'L\'Atterrissage compare, catégorie par catégorie,\n' +
    'le budget prévu et ce qui a été réellement dépensé ce mois-ci.',
    5500,
  );

  const landingTable = page.locator('table').first();
  await R.moveTo(landingTable);
  await R.zoomTo(landingTable, 1.2, 600);
  await R.subtitle(
    'Vous repérez immédiatement les postes en avance ou en dépassement.',
    4500,
  );
  await R.zoomOut(500);
  await R.pause(500);

  // ── 5. Dérives par Ligne ───────────────────────────────────────────────────
  console.log('▶ [Étape 5] Dérives par Ligne');
  const driftTab = page.locator('button:has-text("Dérives par Ligne")');
  await R.moveTo(driftTab);
  await R.clickOn(driftTab);
  await R.pause(700);

  await R.subtitle(
    'L\'onglet Dérives met en lumière les écarts structurels dans la durée\n' +
    'pour vous aider à réajuster vos prévisions budgétaires.',
    5500,
  );
  await R.pause(600);

  // ── 6. Clôture ────────────────────────────────────────────────────────────
  console.log('▶ [Étape 6] Clôture');
  await R.subtitle(
    '💡 Grâce à l\'Analyse, votre budget ne reste pas théorique :\n' +
    'il colle fidèlement à la réalité de votre vie de famille.',
    5500,
  );
  await R.pause(1500);
}

// ─────────────────────────────────────────────────────────────────────────────
// Point d'entrée
// ─────────────────────────────────────────────────────────────────────────────
(async () => {
  console.log('⏺  Vidéo 2.7 — Analyse : comprendre ses dépenses');
  const server = await detectServer();
  await loadDemoData(server.api);

  const { browser, context, page, recorder } = await createSession({
    outputDir: OUTPUT_DIR,
    baseUrl: server.front,
  });

  try {
    await runScenario(page, recorder);
  } catch (err) {
    console.error('Erreur pendant le scénario :', err);
  } finally {
    await finalizeVideo(context, page, 'video-27-analyse.webm', OUTPUT_DIR);
    await browser.close();
  }
})();
