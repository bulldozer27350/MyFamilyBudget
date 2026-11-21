'use strict';
/**
 * Vidéo 2.6 — Le Pointage mensuel
 *
 * Ce que montre cette vidéo :
 *  - D'où vient la liste du pointage (charges & revenus saisis dans Trésorerie)
 *  - Navigation Trésorerie → Pointage pour montrer le lien entre les deux
 *  - Les 4 indicateurs clés (Budget prévu, Débits, Pointé, Non pointé)
 *  - Le bouton « Rapprochement automatique »
 *  - Les statuts colorés (Conforme, Économie, Dépassement)
 *  - Le pointage manuel sur une ligne restante
 *
 * Usage : node scripts/videos/run.js 2.6
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
    if (res.ok) console.log(`[Video 2.6] Données de démo chargées.`);
  } catch (_) {}
}

async function runScenario(page, R) {

  // ── 1. Démarrage sur Trésorerie pour montrer l'origine des données ───────────
  console.log('▶ [Étape 1] Navigation vers cashflow.html');
  await R.navigate('cashflow.html');
  await R.pause(500);

  await R.subtitle(
    'Avant de parler de pointage, voyons d\'où vient la liste que vous allez pointer.',
    5000,
  );
  await R.pause(400);

  // Montrer la section des charges récurrentes dans Trésorerie
  const chargesSection = page.locator(':has-text("Charges récurrentes")').first();
  await R.scrollTo(chargesSection);
  await R.moveTo(chargesSection);
  await R.zoomTo(chargesSection, 1.2, 600);
  await R.subtitle(
    'C\'est ici, dans l\'onglet Trésorerie, que vous saisissez vos charges et revenus récurrents :\n' +
    'loyer, salaires, crédits, abonnements…',
    6000,
  );
  await R.zoomOut(500);
  await R.pause(400);

  await R.subtitle(
    '📌 Ces lignes de budget constituent la liste du Pointage.\n' +
    'Pas de saisie dans Trésorerie → rien à pointer.',
    5500,
  );
  await R.pause(400);

  await R.subtitle(
    'Nous reviendrons en détail sur la Trésorerie dans le Jeu 3 — Prévisionnel.\n' +
    'Pour l\'instant, Thomas et Sophie ont déjà renseigné leurs charges.',
    6000,
  );
  await R.pause(500);

  // ── 2. Navigation vers Pointage depuis la sidebar ──────────────────────────
  console.log('▶ [Étape 2] Navigation vers pointage.html');
  const pointageNavLink = page.locator('a[href="pointage.html"]').first();
  await R.moveTo(pointageNavLink);
  await R.highlight(pointageNavLink);
  await R.subtitle('Direction l\'onglet Pointage.', 2500);
  await R.clickOn(pointageNavLink);
  await R.clearHighlights();
  await page.waitForURL('**/pointage.html', { timeout: 5000 }).catch(() => {});
  await page.waitForLoadState('domcontentloaded');
  await page.locator('button:has-text("Rapprochement automatique")').waitFor({ state: 'visible', timeout: 8000 });
  await R.pause(500);

  await R.subtitle(
    'Le pointage répond à une question simple :\n' +
    'est-ce que mes dépenses réelles collent à ce que j\'avais prévu ?',
    5000,
  );
  await R.pause(400);

  // ── 3. Explication de la liste visible ─────────────────────────────────────
  console.log('▶ [Étape 3] Explication de la liste');
  const tableBody = page.locator('tbody').first();
  await R.scrollTo(tableBody);
  await R.moveTo(tableBody);
  await R.zoomTo(tableBody, 1.15, 600);
  await R.subtitle(
    'Chaque ligne ici correspond à une charge ou un revenu saisi dans Trésorerie.\n' +
    'L\'application les reprend automatiquement : rien à re-saisir.',
    6000,
  );
  await R.zoomOut(500);
  await R.pause(400);

  // ── 4. Les KPIs du Pointage ─────────────────────────────────────────────────
  console.log('▶ [Étape 4] Les 4 KPIs');
  const kpiBudget = page.locator(':has-text("Budget prévu")').first();
  await R.moveTo(kpiBudget);
  await R.zoomTo(kpiBudget, 1.25, 600);
  await R.subtitle('Budget prévu : le total des charges planifiées pour ce mois.', 4000);

  const kpiDebits = page.locator(':has-text("Débits en banque")').first();
  await R.moveTo(kpiDebits);
  await R.subtitle('Débits en banque : le montant effectivement prélevé sur le compte.', 4000);

  const kpiPointe = page.locator(':has-text("Pointé sur budget")').first();
  await R.moveTo(kpiPointe);
  await R.subtitle('Pointé sur budget : la part déjà associée aux lignes de budget.', 4000);
  await R.zoomOut(500);
  await R.pause(500);

  // ── 5. Rapprochement automatique ───────────────────────────────────────────
  console.log('▶ [Étape 5] Rapprochement automatique');
  const autoBtn = page.locator('button:has-text("Rapprochement automatique")');
  await R.moveTo(autoBtn);
  await R.highlight(autoBtn);
  await R.subtitle(
    'Le bouton « Rapprochement automatique » compare les opérations bancaires\n' +
    'importées aux lignes de budget et les associe en un clic.',
    5500,
  );
  await R.clickOn(autoBtn);
  await R.clearHighlights();
  await R.pause(1000);

  // ── 6. Statuts colorés ──────────────────────────────────────────────────────
  console.log('▶ [Étape 6] Statuts colorés');
  const firstRow = page.locator('tbody tr').first();
  await R.moveTo(firstRow);
  await R.zoomTo(firstRow, 1.25, 600);
  await R.subtitle('🔵 Conforme : la dépense réelle correspond exactement au montant prévu.', 4500);
  await R.subtitle('🟢 Économie : vous avez dépensé moins que le budget alloué !', 4000);
  await R.subtitle('🔴 Dépassement : l\'opération dépasse le budget fixé pour ce poste.', 4000);
  await R.zoomOut(500);
  await R.pause(600);

  // ── 7. Pointage manuel ──────────────────────────────────────────────────────
  console.log('▶ [Étape 7] Pointage manuel');
  const pointerBtn = page.locator('button:has-text("Pointer")').first();
  if (await pointerBtn.isVisible()) {
    await R.moveTo(pointerBtn);
    await R.highlight(pointerBtn);
    await R.subtitle(
      'Pour les lignes non traitées automatiquement,\n' +
      'le bouton « Pointer » ouvre le rapprochement manuel.',
      4500,
    );
    await R.clickOn(pointerBtn);
    await R.pause(800);
    await R.clearHighlights();
  }

  // ── 8. Clôture ──────────────────────────────────────────────────────────────
  console.log('▶ [Étape 8] Clôture');
  await R.subtitle(
    '💡 En pointant chaque mois, vous contrôlez l\'équilibre réel de vos finances\n' +
    'et détectez toute anomalie avant qu\'elle n\'affecte votre épargne.',
    5500,
  );
  await R.pause(1500);
}

// ─────────────────────────────────────────────────────────────────────────────
// Point d'entrée
// ─────────────────────────────────────────────────────────────────────────────
(async () => {
  console.log('⏺  Vidéo 2.6 — Le Pointage mensuel');
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
    await finalizeVideo(context, page, 'video-26-pointage.webm', OUTPUT_DIR);
    await browser.close();
  }
})();
