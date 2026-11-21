'use strict';
/**
 * Vidéo 2.1 — Importer un relevé bancaire (CSV)
 *
 * Ce que montre cette vidéo :
 *  - Présentation de la page Import (import.html)
 *  - Vérification des paramètres de format (séparateur, date)
 *  - Sélection du fichier relevé-nov-2026.csv
 *  - Aperçu des données et détection des colonnes
 *  - Validation de l'import et gestion automatique des doublons
 *
 * Usage : node scripts/videos/run.js 2.1
 */

const path = require('path');
const { createSession, finalizeVideo, detectServer } = require('./helpers/recorder');

const OUTPUT_DIR = path.join(__dirname, 'output');
const CSV_FILE = path.join(__dirname, 'csv', 'releve-nov-2026.csv');

async function resetApp(apiUrl) {
  try {
    const res = await fetch(`${apiUrl}/budget/reset`, {
      method: 'POST',
      signal: AbortSignal.timeout(3000),
    });
    if (res.ok) console.log(`[Video 2.1] App réinitialisée pour la démo.`);
  } catch (_) {}
}

async function runScenario(page, R) {

  // ── 1. Navigation vers Import ─────────────────────────────────────────────
  console.log('▶ [Étape 1] Navigation vers import.html');
  await R.navigate('import.html', 'label:has-text("Choisir un fichier")');

  await R.subtitle(
    'Pour analyser vos dépenses réelles, l\'application a besoin de vos relevés bancaires.',
    4500,
  );
  await R.subtitle(
    'La plupart des banques permettent d\'exporter vos opérations au format CSV ou Excel.',
    4500,
  );
  await R.pause(500);

  // ── 2. Paramètres de format ───────────────────────────────────────────────
  console.log('▶ [Étape 2] Format de fichier');
  const formatSection = ':has-text("Format du fichier & import")';
  await R.moveTo(formatSection);
  await R.zoomTo(formatSection, 1.25, 600);

  const sepSelect = page.locator('select').first();
  await R.moveTo(sepSelect);
  await R.highlight(sepSelect);
  await R.subtitle(
    'Vérifiez le séparateur de colonnes (souvent point-virgule)\n' +
    'et le format de date (DD/MM/YYYY pour les banques françaises).',
    5500,
  );
  await R.clearHighlights();
  await R.pause(400);

  // ── 3. Sélection du fichier CSV ───────────────────────────────────────────
  console.log('▶ [Étape 3] Sélection du fichier');
  const chooseBtn = page.locator('label:has-text("Choisir un fichier")');
  await R.moveTo(chooseBtn);
  await R.highlight(chooseBtn);
  await R.subtitle(
    'Thomas a exporté son relevé bancaire de novembre.\n' +
    'Il clique sur Choisir un fichier.',
    4500,
  );

  // Sélection du fichier via l'input file masqué
  const fileInput = page.locator('input[type="file"][accept*=".csv"]');
  await fileInput.setInputFiles(CSV_FILE);
  await R.clearHighlights();
  await R.pause(800);

  // ── 4. Aperçu et vérification des colonnes ────────────────────────────────
  console.log('▶ [Étape 4] Aperçu des colonnes');
  const previewTable = page.locator('table').first();
  await previewTable.waitFor({ state: 'visible', timeout: 8000 });
  await R.moveTo(previewTable);
  await R.subtitle(
    'L\'application affiche un aperçu des premières lignes.\n' +
    'Date, Libellé et Montant sont automatiquement reconnus.',
    5000,
  );
  await R.pause(600);

  // ── 5. Validation de l'import ─────────────────────────────────────────────
  console.log('▶ [Étape 5] Validation import');
  const importBtn = page.locator('main button:has-text("Importer")');
  await R.moveTo(importBtn);
  await R.highlight(importBtn);
  await R.subtitle('On valide l\'importation des opérations.', 3000);
  await R.clickOn(importBtn);
  await R.clearHighlights();

  // Attendre le message de confirmation
  const summaryBox = page.locator('main :has-text("transaction(s) importée(s)")').first();
  await summaryBox.waitFor({ state: 'visible', timeout: 8000 });
  await R.moveTo(summaryBox);
  await R.highlight(summaryBox);
  await R.subtitle(
    '19 transactions sont importées avec succès dans l\'application !',
    4000,
  );
  await R.clearHighlights();
  await R.pause(500);

  // ── 6. Clôture ────────────────────────────────────────────────────────────
  console.log('▶ [Étape 6] Clôture');
  await R.zoomOut(500);
  await R.pause(400);
  await R.subtitle(
    '💡 Si vous réimportez le même fichier par erreur,\n' +
    'l\'application détecte et ignore automatiquement les doublons.',
    5000,
  );
  await R.pause(1500);
}

// ─────────────────────────────────────────────────────────────────────────────
// Point d'entrée
// ─────────────────────────────────────────────────────────────────────────────
(async () => {
  console.log('⏺  Vidéo 2.1 — Importer un relevé bancaire (CSV)');
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
    await finalizeVideo(context, page, 'video-21-import-csv.webm', OUTPUT_DIR);
    await browser.close();
  }
})();
