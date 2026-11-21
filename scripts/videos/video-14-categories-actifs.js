'use strict';
/**
 * Vidéo 1.4 — Catégories d'actifs (Répartition d'actifs)
 *
 * Détecte automatiquement le serveur actif (port standard 8080 ou dev 3000).
 *
 * Ce que montre cette vidéo :
 *  - Rôle des catégories d'actifs et lien avec la Vue d'ensemble
 *  - Structure d'une catégorie : nom, icône, couleur, classe d'actif (bucket)
 *  - Création d'une nouvelle catégorie pour Thomas et Sophie : "Assurance-vie"
 *  - Bonne pratique : définir ses catégories avant de saisir ses placements
 *
 * Usage : node scripts/videos/run.js 1.4
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
    if (res.ok) console.log(`[Video 1.4] App réinitialisée pour la démo.`);
  } catch (_) {}
}

async function runScenario(page, R) {

  // ── 1. Navigation vers Paramètres ─────────────────────────────────────────
  console.log('▶ [Étape 1] Navigation vers settings.html');
  await R.navigate('settings.html', 'input[type="number"]');

  await R.subtitle(
    'Cette section définit les catégories utilisées pour classer vos placements.',
    4500,
  );
  await R.pause(500);

  // ── 2. Découverte de la section Catégories d'actifs ───────────────────────
  console.log('▶ [Étape 2] Section Catégories d\'actifs');
  const catSection = ':has-text("Catégories d\'actifs (Répartition d\'actifs)")';
  await R.scrollTo(catSection);
  await R.moveTo(catSection);
  await R.zoomTo(catSection, 1.25, 600);

  await R.subtitle(
    'Chaque catégorie est rattachée à l\'une des 6 grandes classes d\'actifs patrimoniales\n' +
    '(Cash, Fonds en Euros, Immobilier, Actions, etc.).',
    5500,
  );
  await R.subtitle(
    'C\'est ce classement qui alimente le graphique de répartition\n' +
    'dans la page Vue d\'ensemble.',
    5000,
  );

  // ── 3. Exemple sur une ligne existante ─────────────────────────────────────
  console.log('▶ [Étape 3] Ligne exemple');
  const firstRow = 'table tbody tr >> nth=0';
  await R.moveTo(firstRow);
  await R.highlight(firstRow);
  await R.subtitle(
    'Par exemple : un livret ou compte d\'épargne est classé dans\n' +
    '« Cash & Livrets réglementés ».',
    4500,
  );
  await R.clearHighlights();
  await R.pause(500);

  // ── 4. Ajout d'une catégorie "Assurance-vie" ──────────────────────────────
  console.log('▶ [Étape 4] Bouton Ajouter');
  const addBtn = 'button:has-text("+ Ajouter une ligne")';
  await R.moveTo(addBtn);
  await R.highlight(addBtn);
  await R.subtitle(
    'Sophie et Thomas ont souscrit une assurance-vie.\n' +
    'Ils créent la catégorie correspondante.',
    4500,
  );
  await R.clickOn(addBtn);
  await R.clearHighlights();

  // Attendre l'apparition de la nouvelle ligne dans le DOM
  const lastRow = page.locator('table tbody tr').last();
  await lastRow.waitFor({ state: 'visible', timeout: 8000 });
  await R.pause(600);

  // ── 5. Configuration de la nouvelle ligne ──────────────────────────────────
  console.log('▶ [Étape 5] Renseignement de la catégorie');
  const iconInput = lastRow.locator('td').nth(0).locator('input');
  const nameInput = lastRow.locator('td').nth(2).locator('input');
  const bucketSelect = lastRow.locator('td').nth(3).locator('select');

  // Icône
  await R.moveTo(iconInput);
  await R.clickOn(iconInput);
  await page.keyboard.press('Control+a');
  await page.keyboard.insertText('💰');
  await page.keyboard.press('Tab');
  await R.pause(400);

  // Nom
  await R.moveTo(nameInput);
  await R.subtitle('On nomme la catégorie « Assurance-vie ».', 3000);
  await R.fillField(nameInput, 'Assurance-vie', { blur: true });
  await R.pause(500);

  // Classe d'actif
  await R.moveTo(bucketSelect);
  await R.subtitle('On l\'associe à la classe « Fonds en Euros (Sécurité) ».', 3500);
  await R.clickOn(bucketSelect);
  await bucketSelect.selectOption('fondsEuros');
  await R.pause(800);

  // ── 6. Bonne pratique & Clôture ───────────────────────────────────────────
  console.log('▶ [Étape 6] Bonne pratique & Clôture');
  await R.zoomOut(500);
  await R.pause(400);
  await R.subtitle(
    '💡 Conseil : créez vos catégories ici avant de saisir vos placements dans l\'application.',
    5000,
  );
  await R.pause(1500);
}

// ─────────────────────────────────────────────────────────────────────────────
// Point d'entrée
// ─────────────────────────────────────────────────────────────────────────────
(async () => {
  console.log('⏺  Vidéo 1.4 — Catégories d\'actifs (Répartition d\'actifs)');
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
    await finalizeVideo(context, page, 'video-14-categories-actifs.webm', OUTPUT_DIR);
    await browser.close();
  }
})();
