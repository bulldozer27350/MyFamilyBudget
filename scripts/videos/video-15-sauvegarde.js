'use strict';
/**
 * Vidéo 1.5 — Sauvegarde, export et restauration
 *
 * Détecte automatiquement le serveur actif (port standard 8080 ou dev 3000).
 *
 * Ce que montre cette vidéo :
 *  - Confidentialité : données 100% locales, aucun cloud
 *  - Export d'une sauvegarde complète au format JSON
 *  - Restauration des données via Importer (JSON)
 *  - Mise en garde sur le bouton Réinitialiser
 *  - Bonne pratique : exporter avant chaque mise à jour
 *
 * Usage : node scripts/videos/run.js 1.5
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
    if (res.ok) console.log(`[Video 1.5] App réinitialisée pour la démo.`);
  } catch (_) {}
}

async function runScenario(page, R) {

  // ── 1. Navigation vers Vue d'ensemble ──────────────────────────────────────
  console.log('▶ [Étape 1] Navigation vers overview.html');
  await R.navigate('overview.html');

  await R.subtitle(
    'Vos données financières ne transitent par aucun cloud :\n' +
    'elles sont conservées localement sur votre ordinateur.',
    5000,
  );
  await R.subtitle(
    'Voici comment les sauvegarder facilement et les restaurer en cas de besoin.',
    4500,
  );
  await R.pause(500);

  // ── 2. Localisation des outils de sauvegarde ───────────────────────────────
  console.log('▶ [Étape 2] Barre latérale - Actions de sauvegarde');
  const exportBtn = 'button:has-text("Exporter (JSON)")';
  await R.moveTo(exportBtn);
  await R.zoomTo(exportBtn, 1.3, 600);

  // ── 3. Export JSON ────────────────────────────────────────────────────────
  console.log('▶ [Étape 3] Export JSON');
  await R.highlight(exportBtn);
  await R.subtitle(
    'Le bouton Exporter (JSON) génère un fichier de sauvegarde\n' +
    'contenant l\'intégralité de vos comptes, opérations et paramètres.',
    5500,
  );

  // Déclenchement du téléchargement en interceptant l'événement Playwright
  const downloadPromise = page.waitForEvent('download', { timeout: 4000 }).catch(() => null);
  await R.clickOn(exportBtn);
  await downloadPromise;

  await R.subtitle(
    'Votre fichier de sauvegarde est téléchargé.\n' +
    'Conservez-le précieusement sur une clé USB ou un disque externe.',
    5000,
  );
  await R.clearHighlights();
  await R.pause(600);

  // ── 4. Import JSON ────────────────────────────────────────────────────────
  console.log('▶ [Étape 4] Import JSON');
  const importBtn = 'button:has-text("Importer (JSON)")';
  await R.moveTo(importBtn);
  await R.highlight(importBtn);
  await R.subtitle(
    'Pour transférer votre budget sur un autre ordinateur\n' +
    'ou restaurer une sauvegarde, utilisez le bouton Importer (JSON).',
    5500,
  );
  await R.subtitle(
    '⚠️ Attention : l\'import remplace intégralement les données actuelles.\n' +
    'Pensez toujours à faire un export préalable.',
    5500,
  );
  await R.clearHighlights();
  await R.pause(600);

  // ── 5. Réinitialisation ───────────────────────────────────────────────────
  console.log('▶ [Étape 5] Réinitialiser');
  const resetBtn = 'button:has-text("Réinitialiser")';
  await R.moveTo(resetBtn);
  await R.highlight(resetBtn);
  await R.subtitle(
    '⚠️ Le bouton Réinitialiser efface toutes les données de l\'application.\n' +
    'À n\'utiliser que si vous souhaitez repartir de zéro.',
    5500,
  );
  await R.clearHighlights();
  await R.pause(500);

  // ── 6. Bonne pratique & Clôture ───────────────────────────────────────────
  console.log('▶ [Étape 6] Bonne pratique & Clôture');
  await R.zoomOut(500);
  await R.pause(400);
  await R.subtitle(
    '💡 Bonne pratique : effectuez toujours un export de sécurité\n' +
    'avant d\'installer une nouvelle version de MyFamilyBudget.',
    5500,
  );
  await R.pause(1500);
}

// ─────────────────────────────────────────────────────────────────────────────
// Point d'entrée
// ─────────────────────────────────────────────────────────────────────────────
(async () => {
  console.log('⏺  Vidéo 1.5 — Sauvegarde, export et restauration');
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
    await finalizeVideo(context, page, 'video-15-sauvegarde.webm', OUTPUT_DIR);
    await browser.close();
  }
})();
