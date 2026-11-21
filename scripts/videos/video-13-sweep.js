'use strict';
/**
 * Vidéo 1.3 — Virement automatique & Seuils (Sweep)
 *
 * Détecte automatiquement le serveur actif (port standard 8080 ou dev 3000).
 *
 * Ce que montre cette vidéo :
 *  - Activation du virement automatique compte courant ↔ épargne
 *  - Définition du seuil haut (versement vers l'épargne : 6 000 €)
 *  - Définition du seuil bas (renflouement depuis l'épargne : 2 000 €)
 *  - Définition du seuil d'alerte (sécurité : 1 000 €)
 *
 * Usage : node scripts/videos/run.js 1.3
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
    if (res.ok) console.log(`[Video 1.3] App réinitialisée pour la démo.`);
  } catch (_) {}
}

async function runScenario(page, R) {

  // ── 1. Navigation vers Paramètres ─────────────────────────────────────────
  console.log('▶ [Étape 1] Navigation vers settings.html');
  await R.navigate('settings.html', 'input[type="number"]');

  await R.subtitle(
    'Le virement automatique évite d\'avoir trop d\'argent qui dort\n' +
    'sur le compte courant — ou pas assez.',
    4500,
  );
  await R.pause(500);

  // ── 2. Découverte de la section Virement automatique ──────────────────────
  console.log('▶ [Étape 2] Section Virement automatique');
  const sweepSection = ':has-text("Virement automatique compte courant ↔ épargne")';
  await R.scrollTo(sweepSection);
  await R.moveTo(sweepSection);
  await R.zoomTo(sweepSection, 1.25, 600);

  // ── 3. Activation de l'option ─────────────────────────────────────────────
  console.log('▶ [Étape 3] Activation sweep');
  const checkboxLabel = 'label:has-text("Activer le virement automatique")';
  await R.moveTo(checkboxLabel);
  await R.highlight(checkboxLabel);
  await R.subtitle('Commençons par activer la fonctionnalité.', 3000);
  await R.clickOn(checkboxLabel);
  await R.clearHighlights();
  await R.pause(500);

  // ── 4. Seuil haut ─────────────────────────────────────────────────────────
  console.log('▶ [Étape 4] Seuil haut (6 000 €)');
  const ceilingInput = 'input[placeholder="ex. 15000"]';
  await R.moveTo(ceilingInput);
  await R.highlight(ceilingInput);
  await R.subtitle(
    'Seuil haut : si le compte dépasse ce montant en fin de mois,\n' +
    'l\'excédent est automatiquement transféré vers votre épargne.',
    5500,
  );
  await R.subtitle('Sophie et Thomas décident de fixer le plafond à 6 000 €.', 3500);
  await R.fillField(ceilingInput, '6000', { typeDelay: 90, blur: true });
  await R.clearHighlights();
  await R.pause(600);

  // ── 5. Seuil bas ──────────────────────────────────────────────────────────
  console.log('▶ [Étape 5] Seuil bas (2 000 €)');
  const floorInput = 'input[placeholder="ex. 3000"]';
  await R.moveTo(floorInput);
  await R.highlight(floorInput);
  await R.subtitle(
    'Seuil bas : si le solde descend sous cette limite,\n' +
    'l\'application rapatrie le manquant depuis vos comptes d\'épargne.',
    5500,
  );
  await R.subtitle('Ils se fixent un filet de sécurité à 2 000 €.', 3000);
  await R.fillField(floorInput, '2000', { typeDelay: 90, blur: true });
  await R.clearHighlights();
  await R.pause(600);

  // ── 6. Seuil d'alerte ─────────────────────────────────────────────────────
  console.log('▶ [Étape 6] Seuil d\'alerte (1 000 €)');
  const alertInput = 'input[placeholder="ex. 1000"]';
  await R.moveTo(alertInput);
  await R.highlight(alertInput);
  await R.subtitle(
    'Le seuil d\'alerte est différent : il ne déclenche aucun virement,\n' +
    'mais suspend les versements vers l\'épargne si le compte passe en dessous.',
    5500,
  );
  await R.subtitle('C\'est une sécurité essentielle pour les mois difficiles : 1 000 €.', 3500);
  await R.fillField(alertInput, '1000', { typeDelay: 90, blur: true });
  await R.clearHighlights();
  await R.pause(600);

  // ── 7. Conclusion ─────────────────────────────────────────────────────────
  console.log('▶ [Étape 7] Conclusion');
  await R.zoomOut(500);
  await R.pause(400);
  await R.subtitle(
    'Ces seuils régulent automatiquement vos prévisions de trésorerie.\n' +
    'Nous verrons dans les vidéos Placements comment choisir les comptes cibles.',
    5500,
  );
  await R.pause(1500);
}

// ─────────────────────────────────────────────────────────────────────────────
// Point d'entrée
// ─────────────────────────────────────────────────────────────────────────────
(async () => {
  console.log('⏺  Vidéo 1.3 — Virement automatique & Seuils (Sweep)');
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
    await finalizeVideo(context, page, 'video-13-sweep.webm', OUTPUT_DIR);
    await browser.close();
  }
})();
