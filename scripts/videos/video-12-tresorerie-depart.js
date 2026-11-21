'use strict';
/**
 * Vidéo 1.2 — Trésorerie de départ & Date Pivot
 *
 * Détecte automatiquement le serveur actif (port standard 8080 ou dev 3000).
 *
 * Ce que montre cette vidéo :
 *  - Explication de la trésorerie disponible de départ
 *  - Saisie du solde initial de Thomas & Sophie (4 200 €)
 *  - Présentation de la Date Pivot (Mode Manuel vs Mode Automatique)
 *  - Pourquoi démarrer en mode Manuel
 *
 * Usage : node scripts/videos/run.js 1.2
 */

const path = require('path');
const { createSession, finalizeVideo, detectServer } = require('./helpers/recorder');

const OUTPUT_DIR = path.join(__dirname, 'output');

async function resetApp(apiUrl) {
  try {
    const res = await fetch(`${apiUrl}/budget/reset`, {
      method: 'POST',
      signal: AbortSignal.timeout(3000),
    });
    if (res.ok) console.log(`[Video 1.2] App réinitialisée pour la démo.`);
  } catch (_) {}
}

async function runScenario(page, R) {

  // ── 1. Navigation vers Paramètres ─────────────────────────────────────────
  console.log('▶ [Étape 1] Navigation vers settings.html');
  await R.navigate('settings.html', 'input[type="number"]');

  await R.subtitle(
    'Cette vidéo explique comment indiquer à l\'application le solde de votre compte courant,\n' +
    'point de départ de tous les calculs prévisionnels.',
    5000,
  );
  await R.pause(600);

  // ── 2. Découverte de la section Date Pivot ────────────────────────────────
  console.log('▶ [Étape 2] Section Date Pivot');
  const pivotHeader = ':has-text("📌 Date Pivot")';
  await R.moveTo(pivotHeader);
  await R.zoomTo(pivotHeader, 1.25, 600);
  await R.subtitle(
    'La Date Pivot est le point de bascule entre le passé réel et le futur projeté.',
    4500,
  );

  // ── 3. Le mode Manuel ─────────────────────────────────────────────────────
  console.log('▶ [Étape 3] Mode Manuel');
  const manualRadio = 'label:has-text("Manuel")';
  await R.moveTo(manualRadio);
  await R.highlight(manualRadio);
  await R.subtitle(
    'Mode Manuel : vous saisissez vous-même le solde de départ.\n' +
    'C\'est le plus simple pour démarrer.',
    4500,
  );
  await R.clearHighlights();
  await R.pause(400);

  // ── 4. Saisie de la trésorerie de départ (4 200 €) ─────────────────────────
  console.log('▶ [Étape 4] Trésorerie de départ');
  await R.zoomOut(500);
  await R.pause(300);

  const balanceInput = page.locator('input[type="number"]').nth(3);
  await R.moveTo(balanceInput);
  await R.highlight(balanceInput);
  await R.subtitle(
    'Thomas vérifie son compte en ligne : ils ont 4 200 € sur le compte courant.',
    4000,
  );

  await R.subtitle('On saisit 4 200 €. L\'application part de ce solde pour ses projections.', 3500);
  await R.fillField(balanceInput, '4200', { typeDelay: 90, blur: true });
  await R.clearHighlights();
  await R.pause(800);

  // ── 5. Le mode Automatique (explication) ──────────────────────────────────
  console.log('▶ [Étape 5] Mode Automatique');
  await R.moveTo(pivotHeader);
  await R.zoomTo(pivotHeader, 1.25, 600);

  const autoRadio = 'label:has-text("Automatique — solde calculé")';
  await R.moveTo(autoRadio);
  await R.highlight(autoRadio);
  await R.subtitle(
    'Plus tard, quand vous aurez importé vos relevés bancaires,\n' +
    'le mode Automatique calculera le solde exact à partir des opérations réelles.',
    5500,
  );
  await R.subtitle(
    'Pour l\'instant, nous restons en mode Manuel.\n' +
    'Nous verrons le mode Automatique lors de l\'import bancaire.',
    5000,
  );
  await R.clearHighlights();
  await R.zoomOut(500);

  // ── 6. Synthèse & Clôture ─────────────────────────────────────────────────
  console.log('▶ [Étape 6] Synthèse & Clôture');
  await R.pause(500);
  await R.subtitle(
    'En résumé : le mode Manuel convient parfaitement au lancement.\n' +
    'Le mode Automatique prendra le relais après vos premiers imports.',
    5000,
  );
  await R.pause(1500);
}

// ─────────────────────────────────────────────────────────────────────────────
// Point d'entrée
// ─────────────────────────────────────────────────────────────────────────────
(async () => {
  console.log('⏺  Vidéo 1.2 — Trésorerie de départ & Date Pivot');
  const server = await detectServer();

  const { browser, context, page, recorder } = await createSession({
    outputDir: OUTPUT_DIR,
    baseUrl: server.front,
  });

  try {
    await runScenario(page, recorder);
  } catch (err) {
    console.error('Erreur pendant le scénario :', err);
  } finally {
    await finalizeVideo(context, page, 'video-12-tresorerie-depart.webm', OUTPUT_DIR);
    await browser.close();
  }
})();
