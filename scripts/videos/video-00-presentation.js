'use strict';
/**
 * Vidéo 0 — Présentation générale de MyFamilyBudget
 *
 * Détecte automatiquement si l'application tourne sur le port standard (8080)
 * ou en développement (3000).
 *
 * Usage : node scripts/videos/run.js 0
 */

const path = require('path');
const fs   = require('fs');
const { createSession, finalizeVideo, detectServer } = require('./helpers/recorder');

const OUTPUT_DIR = path.join(__dirname, 'output');
const DEMO_JSON  = path.join(__dirname, '..', '..', 'budget-familial-demo.json');

// ─────────────────────────────────────────────────────────────────────────────
// Chargement optionnel des données de démo
// ─────────────────────────────────────────────────────────────────────────────
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
    if (res.ok) console.log(`[Video 0] Données de démo chargées.`);
  } catch (_) {}
}

// ─────────────────────────────────────────────────────────────────────────────
// Scénario
// ─────────────────────────────────────────────────────────────────────────────
async function runScenario(page, R) {

  // ── 1. Ouverture sur la Vue d'ensemble ────────────────────────────────────
  await R.navigate('overview.html', '#root');

  await R.subtitle('MyFamilyBudget : votre tableau de bord budgétaire familial.', 3200);
  await R.subtitle(
    'Cette vidéo vous présente en 2 minutes l\'organisation de l\'application\n' +
    '— avant de vous montrer, étape par étape, comment vous en servir.',
    5500,
  );
  await R.pause(800);

  // ── 2. Organisation du menu (Sidebar) ────────────────────────────────────
  await R.subtitle('Le menu latéral est découpé en deux grandes logiques.', 3000);
  await R.pause(400);

  // Partie 1 : Analyse du réel (Import, Opérations en cours, Pointage, Analyse)
  await R.highlight('a.sidebar-nav-link[href="import.html"], a.sidebar-nav-link[href="pending.html"], a.sidebar-nav-link[href="pointage.html"], a.sidebar-nav-link[href="analyse.html"]');
  await R.subtitle(
    'En haut : tout ce qui concerne votre argent aujourd\'hui —\n' +
    'relevés bancaires, dépenses du mois, solde réel.',
    5500,
  );
  await R.clearHighlights();
  await R.pause(500);

  // Partie 2 : Prévisions & planification (Vue d'ensemble, Trésorerie, Patrimoine, Retraite, Impôts, Paramètres)
  await R.highlight('a.sidebar-nav-link[href="overview.html"], a.sidebar-nav-link[href="cashflow.html"], a.sidebar-nav-link[href="patrimoine.html"], a.sidebar-nav-link[href="retraite.html"], a.sidebar-nav-link[href="impots.html"], a.sidebar-nav-link[href="settings.html"]');
  await R.subtitle(
    'En bas : tout ce qui concerne votre avenir —\n' +
    'revenus et charges prévisionnels, placements, retraite.',
    5500,
  );
  await R.clearHighlights();
  await R.pause(800);

  // ── 3. Tour rapide des sections du quotidien ──────────────────────────────
  await R.subtitle('Voici comment Sophie et Thomas Martin vont utiliser l\'application.', 3500);
  await R.pause(400);

  const tourStops = [
    { href: 'import.html',   sub: 'Tout commence par l\'import de leurs relevés bancaires.' },
    { href: 'pending.html',  sub: 'Ils suivent leurs dépenses engagées mais pas encore débitées.' },
    { href: 'pointage.html', sub: 'Ils vérifient que leurs dépenses réelles collent à leur budget.' },
    { href: 'analyse.html',  sub: 'Ils analysent leurs tendances de dépenses mois par mois.' },
    { href: 'overview.html', sub: 'Et ils voient en un coup d\'œil l\'état global de leurs finances.' },
  ];

  for (const stop of tourStops) {
    const linkSel = `a.sidebar-nav-link[href="${stop.href}"]`;
    await R.moveTo(linkSel);
    await R.pause(300);
    await R.clickOn(linkSel, { before: 200, after: 300 });
    await page.waitForTimeout(1000);
    await R.reinject();
    await R.subtitle(stop.sub, 3200);
    await R.pause(300);
  }

  // ── 4. Paramètres ─────────────────────────────────────────────────────────
  await R.pause(400);
  const settingsLinkSel = 'a.sidebar-nav-link[href="settings.html"]';
  await R.moveTo(settingsLinkSel);
  await R.pause(300);
  await R.clickOn(settingsLinkSel, { before: 200, after: 300 });
  await page.waitForTimeout(1000);
  await R.reinject();

  await R.subtitle(
    'Avant tout cela, quelques réglages initiaux sont nécessaires dans Paramètres.\n' +
    'C\'est par là que nous commençons dans la vidéo suivante.',
    5500,
  );
  await R.pause(800);

  // ── 5. Sauvegarde locale & Restauration ───────────────────────────────────
  await R.subtitle('Vos données restent sur votre machine. Aucun cloud, aucun serveur distant.', 4800);

  // Mettre en avant le bouton d'export dans la sidebar
  const exportSel = 'button:has-text("Exporter"), .sidebar-footer button, button:has-text("JSON")';
  await R.highlight(exportSel);
  await R.subtitle(
    'L\'export JSON sauvegarde votre budget complet sur votre disque.\n' +
    'L\'import JSON permet de le restaurer en cas de besoin ou avant une mise à jour.',
    6000,
  );
  await R.clearHighlights();
  await R.pause(600);

  // ── 6. Lancement ──────────────────────────────────────────────────────────
  await R.subtitle(
    'Pour lancer l\'application : double-cliquez sur MyFamilyBudget.bat (Windows)\n' +
    'puis accédez directement à votre tableau de bord.',
    5500,
  );

  await R.pause(1500);
}

// ─────────────────────────────────────────────────────────────────────────────
// Point d'entrée
// ─────────────────────────────────────────────────────────────────────────────
(async () => {
  console.log('⏺  Vidéo 0 — Présentation générale');
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
    await finalizeVideo(context, page, 'video-00-presentation.webm', OUTPUT_DIR);
    await browser.close();
  }
})();
