'use strict';
/**
 * Vidéo 2.8 — Vue d'ensemble : tableau de bord global
 *
 * Usage : node scripts/videos/run.js 2.8
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
    if (res.ok) console.log(`[Video 2.8] Données de démo chargées.`);
  } catch (_) {}
}

/**
 * Centre verticalement un élément au milieu du viewport avant zoom.
 * Évite les décalages où l'élément est tronqué en haut ou en bas.
 */
async function scrollToCenter(page, locator) {
  try {
    await locator.evaluate(el => el.scrollIntoView({ behavior: 'smooth', block: 'center' }));
    await page.waitForTimeout(400);
  } catch (_) {}
}

async function runScenario(page, R) {

  // ── 1. Navigation — arrivée sur overview.html ──────────────────────────────
  console.log('▶ [Étape 1] Navigation vers overview.html');

  await page.goto(`${R.baseUrl}/overview.html`, { waitUntil: 'domcontentloaded' });
  await R.reinject();

  // Sous-titre d'accueil immédiat pendant que React calcule et rend
  await R.subtitle(
    'La Vue d\'ensemble est le tableau de bord central de MyFamilyBudget :\ntout votre horizon financier en un seul regard.',
    4500
  );

  // Détection du libellé "Patrimoine placé actuel" (présent dans tous les modes)
  const labelPatrim = page.getByText('Patrimoine placé actuel', { exact: true }).first();
  await labelPatrim.waitFor({ state: 'visible', timeout: 8000 }).catch(() => {});
  await page.waitForTimeout(1000);
  await R.hideSubtitle();
  await page.waitForTimeout(300);

  // ── 2. Les 4 KPIs du haut — chaque carte ciblée par le curseur ───────────────
  console.log('▶ [Étape 2] Les 4 indicateurs clés (KPIs)');

  await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'smooth' }));
  await page.waitForTimeout(300);

  // Ciblage robuste de chaque carte KPI via le parent direct de son libellé
  // (évite de matcher #root via filter(has: ...).first())
  const labelTreso = page.locator('div').filter({ hasText: /^Solde réel|^Trésorerie de départ/ }).last();
  const kpiTreso   = labelTreso.locator('..');

  const kpiPatrim  = labelPatrim.locator('..');

  const labelFlux  = page.getByText('Flux net — année en cours', { exact: true }).first();
  const kpiFlux    = labelFlux.locator('..');

  const labelRetr  = page.getByText('Année de retraite visée', { exact: true }).first();
  const kpiRetraite = labelRetr.locator('..');

  // KPI 1 : Trésorerie de départ / Solde réel
  await R.moveTo(kpiTreso);
  await R.highlight(kpiTreso);
  await R.subtitle('Trésorerie / Solde réel : le solde de départ de toutes vos projections.', 4200);
  await R.clearHighlights();

  // KPI 2 : Patrimoine placé actuel
  await R.moveTo(kpiPatrim);
  await R.highlight(kpiPatrim);
  await R.subtitle('Patrimoine placé actuel : la valeur cumulée de l\'ensemble de vos comptes et placements.', 4500);
  await R.clearHighlights();

  // KPI 3 : Flux net annuel
  await R.moveTo(kpiFlux);
  await R.highlight(kpiFlux);
  await R.subtitle('Flux net annuel : votre capacité d\'épargne globale sur l\'année en cours.', 4200);
  await R.clearHighlights();

  // KPI 4 : Année de retraite visée
  await R.moveTo(kpiRetraite);
  await R.highlight(kpiRetraite);
  await R.subtitle('Année de retraite visée : l\'horizon temporel de vos projections financières.', 4200);
  await R.clearHighlights();
  await R.pause(400);

  // ── 3. Graphique Trésorerie disponible & Patrimoine Net ────────────────────
  console.log('▶ [Étape 3] Graphique Trésorerie');

  // Premier canvas de la page = InteractiveTreasuryChart
  const canvasTreso = page.locator('canvas').first();
  await scrollToCenter(page, canvasTreso);
  await R.moveTo(canvasTreso);
  await R.zoomTo(canvasTreso, 1.2, 600);
  await R.subtitle(
    'Ce graphique interactif trace la trésorerie disponible,\n' +
    'le passif (crédits restants) et votre patrimoine net réel mois par mois.',
    5500,
  );
  await R.subtitle(
    '💡 Molette pour zoomer sur une période, glisser pour naviguer dans le temps.',
    4500,
  );
  await R.zoomOut(500);
  await R.pause(400);

  // ── 4. Journal des virements & mouvements prévus ──────────────────────────
  console.log('▶ [Étape 4] Journal des mouvements prévus');

  // Ciblage direct du tableau du journal (reconnaissable par son en-tête Virement)
  const journalTable = page.locator('table:has(th:has-text("Virement"))').first();
  await scrollToCenter(page, journalTable);
  await R.moveTo(journalTable);
  await R.zoomTo(journalTable, 1.15, 600);
  await R.subtitle(
    'Le Journal des mouvements détaille, jour par jour,\n' +
    'chaque flux financier prévu : salaires, loyer, mensualités, placements...',
    5500,
  );

  // Pointeur vers les filtres de colonne
  const filterInput = journalTable.locator('input[placeholder*="Filtrer"]').first();
  if (await filterInput.isVisible().catch(() => false)) {
    await R.moveTo(filterInput);
  }
  await R.subtitle(
    'Chaque colonne dispose d\'un tri et d\'un filtre pour retrouver une opération future.',
    4500,
  );
  await R.zoomOut(500);
  await R.pause(400);

  // ── 5. Répartition d'actifs (Asset Allocation) & Camembert ─────────────────
  console.log('▶ [Étape 5] Répartition d\'actifs (Camembert)');

  const assetTitle = page.getByText("Répartition d'actifs (Asset Allocation)", { exact: true }).first();
  await scrollToCenter(page, assetTitle);
  await R.moveTo(assetTitle);
  await R.subtitle(
    'Plus bas, la Répartition d\'actifs illustre la diversification de vos avoirs.',
    4200,
  );

  // Ciblage unique et direct du select contenant l'option 'pie' (Camembert)
  const selectPie = page.locator('select:has(option[value="pie"])').first();
  if (await selectPie.isVisible().catch(() => false)) {
    await R.moveTo(selectPie);
    await selectPie.selectOption('pie');
    await page.waitForTimeout(600);
  }

  // Le canvas du camembert est le canvas du milieu (2e canvas sur la page quand le camembert est actif)
  const canvasPie = page.locator('canvas').nth(1);
  if (await canvasPie.isVisible().catch(() => false)) {
    await scrollToCenter(page, canvasPie);
    await R.moveTo(canvasPie);
    await R.highlight(canvasPie);
    await R.zoomTo(canvasPie, 1.25, 600);
    await R.subtitle(
      'L\'affichage en camembert regroupe vos avoirs par classe d\'actif :\n' +
      'Cash, Immobilier, Assurance-vie, Actions...',
      5200,
    );
    await R.clearHighlights();
    await R.zoomOut(500);
    await R.pause(400);
  }

  // ── 6. Graphique Patrimoine placé — 3 scénarios (en bas de page) ───────────
  console.log('▶ [Étape 6] Graphique 3 scénarios');

  // Dernier canvas de la page = LineChartJS (Patrimoine 3 scénarios)
  const canvasScen = page.locator('canvas').last();
  await scrollToCenter(page, canvasScen);
  await R.moveTo(canvasScen);
  await R.zoomTo(canvasScen, 1.2, 600);
  await R.subtitle(
    'Ce graphique projette l\'évolution de vos placements sur plusieurs décennies\n' +
    'selon 3 scénarios de marché : pessimiste, correct et optimiste.',
    5500,
  );
  await R.zoomOut(500);
  await R.pause(400);

  // ── 7. Synthèse et clôture du Jeu 2 ────────────────────────────────────────
  console.log('▶ [Étape 7] Clôture du Jeu 2');

  await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'smooth' }));
  await R.pause(700);
  await R.subtitle(
    'Vous maîtrisez désormais toute la gestion du quotidien !\n' +
    'Rendez-vous dans la partie suivante pour bâtir votre budget prévisionnel.',
    5500,
  );
  await R.pause(1200);
}

// ─────────────────────────────────────────────────────────────────────────────
// Point d'entrée
// ─────────────────────────────────────────────────────────────────────────────
(async () => {
  console.log('⏺  Vidéo 2.8 — Vue d\'ensemble : tableau de bord global');
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
    await finalizeVideo(context, page, 'video-28-overview.webm', OUTPUT_DIR);
    await browser.close();
  }
})();
