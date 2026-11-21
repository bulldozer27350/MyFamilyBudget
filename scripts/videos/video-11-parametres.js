'use strict';
/**
 * Vidéo 1.1 — Paramètres généraux
 *
 * Détecte automatiquement le serveur actif (port standard 8080 ou dev 3000).
 *
 * Ce que montre cette vidéo :
 *  - Année de naissance (Thomas : 1985)
 *  - Âge de départ à la retraite visé (64 — valeur par défaut)
 *  - Simuler jusqu'à l'âge de (85 — valeur par défaut)
 *  - Taux d'inflation estimé (2 %)
 *  - Mention du solde de départ (renvoi vidéo 1.2)
 *
 * Usage : node scripts/videos/run.js 1.1
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
    if (res.ok) console.log(`[Video 1.1] App réinitialisée pour la démo.`);
  } catch (_) {}
}

async function runScenario(page, R) {

  // ── 1. Ouverture de la page Paramètres ────────────────────────────────────
  console.log('▶ [Étape 1] Navigation vers settings.html');
  await R.navigate('settings.html', 'input[type="number"]');

  await R.subtitle('Commençons par les Paramètres généraux.', 2800);
  await R.subtitle(
    'L\'onglet ⚙️ Général est sélectionné par défaut.\n' +
    'Ces quelques valeurs suffisent pour démarrer.',
    4500,
  );
  await R.pause(800);

  // ── 2. Année de naissance ─────────────────────────────────────────────────
  console.log('▶ [Étape 2] Année de naissance');
  const birthInput = 'input[type="number"] >> nth=0';
  await R.moveTo(birthInput);
  await R.highlight(birthInput);
  await R.subtitle(
    'L\'année de naissance est utilisée pour calculer automatiquement\n' +
    'l\'année de départ à la retraite.',
    4500,
  );

  // Mettre en avant le menu Retraite
  await R.highlight('a.sidebar-nav-link[href="retraite.html"]');
  await R.subtitle(
    'Ces projections seront visibles dans la page Retraite —\n' +
    'nous y reviendrons dans les vidéos de la partie prévisionnelle.',
    5000,
  );
  await R.clearHighlights();
  await R.pause(600);

  // ── 3. Saisie de l'année de naissance ────────────────────────────────────
  console.log('▶ [Étape 3] Saisie 1985');
  await R.highlight(birthInput);
  await R.subtitle(
    '⚠️ La valeur doit être au format AAAA (4 chiffres).\n' +
    'Un format sur 2 chiffres provoquerait une erreur de calcul.',
    5000,
  );
  await R.clearHighlights();
  await R.pause(400);

  await R.subtitle('Thomas est né en 1985. On saisit l\'année complète.', 3000);
  await R.fillField(birthInput, '1985', { typeDelay: 100, blur: true });
  await R.pause(700);

  // ── 4. Vérification de l'année de retraite calculée ──────────────────────
  console.log('▶ [Étape 4] Année de retraite calculée');
  const calcText = ':has-text("Année de retraite calculée")';
  await R.moveTo(calcText);
  await R.zoomTo(calcText, 1.35, 600);
  // Pointer le curseur sur le <strong> affichant "2049" et l'encadrer pour attirer l'œil
  const strong2049 = 'strong:has-text("2049")';
  await R.moveTo(strong2049);
  await R.highlight(strong2049);
  await R.subtitle(
    'L\'application calcule automatiquement l\'année de retraite visée : 2049.',
    4500,
  );
  await R.clearHighlights();
  await R.zoomOut(500);
  await R.pause(600);

  // ── 5. Âge de départ à la retraite ───────────────────────────────────────
  console.log('▶ [Étape 5] Âge de départ');
  const retireInput = 'input[type="number"] >> nth=1';
  await R.moveTo(retireInput);
  await R.highlight(retireInput);
  await R.subtitle(
    'L\'âge de départ à la retraite visé est à 64 ans — la valeur par défaut.\n' +
    'Thomas décide de le laisser tel quel.',
    4500,
  );
  await R.clearHighlights();
  await R.pause(600);

  // ── 6. Simuler jusqu'à l'âge de ──────────────────────────────────────────
  console.log('▶ [Étape 6] Simuler jusqu\'à l\'âge de');
  const simInput = 'input[type="number"] >> nth=2';
  await R.moveTo(simInput);
  await R.highlight(simInput);
  await R.subtitle(
    'Ce paramètre prolonge la simulation bien au-delà du départ en retraite —\n' +
    'jusqu\'à l\'âge choisi, pour vérifier que le patrimoine tient dans la durée.',
    6000,
  );
  await R.subtitle('On garde 85 ans, la valeur par défaut.', 2500);
  await R.clearHighlights();
  await R.pause(600);

  // ── 7. Taux d'inflation ───────────────────────────────────────────────────
  console.log('▶ [Étape 7] Taux d\'inflation');
  const inflationInput = 'input[type="number"] >> nth=4';
  await R.moveTo(inflationInput);
  await R.highlight(inflationInput);
  await R.subtitle(
    'Le taux d\'inflation sert à indexer vos charges futures :\n' +
    'toute charge sans taux explicite augmentera de ce pourcentage chaque année.',
    6000,
  );
  await R.subtitle('On garde 2 % — une valeur raisonnable pour commencer.', 3000);
  await R.clearHighlights();
  await R.pause(600);

  // ── 8. Solde de départ ────────────────────────────────────────────────────
  console.log('▶ [Étape 8] Solde de départ');
  const balanceInput = 'input[type="number"] >> nth=3';
  await R.moveTo(balanceInput);
  await R.highlight(balanceInput);
  await R.subtitle(
    'Ce champ représente le solde de votre compte courant au moment\n' +
    'où vous commencez à utiliser l\'application.',
    5000,
  );
  await R.subtitle(
    'Nous lui consacrons la vidéo suivante :\n' +
    'il est lié à la Date Pivot, qui mérite une explication à part entière.',
    5500,
  );
  await R.clearHighlights();

  // ── 9. Clôture ────────────────────────────────────────────────────────────
  console.log('▶ [Étape 9] Clôture');
  await R.pause(600);
  await R.subtitle(
    'Ces paramètres sont les seuls nécessaires pour démarrer.\n' +
    'Les autres seront expliqués au fil des vidéos, quand ils deviendront utiles.',
    5500,
  );
  await R.pause(1500);
}

// ─────────────────────────────────────────────────────────────────────────────
// Point d'entrée
// ─────────────────────────────────────────────────────────────────────────────
(async () => {
  console.log('⏺  Vidéo 1.1 — Paramètres généraux');
  const server = await detectServer();
  await resetApp(server.api);

  const { browser, context, page, recorder } = await createSession({
    outputDir: OUTPUT_DIR,
    baseUrl: server.front,
  });

  try {
    await runScenario(page, recorder);
  } catch (err) {
    console.error('Erreur pendant le scénario :', err);
  } finally {
    await finalizeVideo(context, page, 'video-11-parametres.webm', OUTPUT_DIR);
    await browser.close();
  }
})();
