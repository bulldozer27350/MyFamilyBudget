'use strict';
/**
 * Vidéo 2.4 — Trier et rechercher dans les transactions
 *
 * Ce que montre cette vidéo :
 *  - Tri instantané des colonnes (ex: Montant croissant / décroissant)
 *  - Filtrage multi-critères par libellé ou catégorie
 *  - Visualisation du compteur de lignes filtrées
 *  - Réinitialisation complète des filtres en un clic
 *
 * Usage : node scripts/videos/run.js 2.4
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
    if (res.ok) console.log(`[Video 2.4] App réinitialisée pour la démo.`);
  } catch (_) {}
}

async function seedCategories(apiUrl) {
  const cats = [
    { id: 'cat-loyer', label: 'Loyer', kind: 'Dépense', compressible: 'Non' },
    { id: 'cat-courses', label: 'Courses alimentaires', kind: 'Dépense', compressible: 'Oui' },
    { id: 'cat-carburant', label: 'Carburant', kind: 'Dépense', compressible: 'Non' },
    { id: 'cat-credits', label: 'Crédits', kind: 'Dépense', compressible: 'Non' },
  ];
  for (const c of cats) {
    await fetch(`${apiUrl}/bank-import/categories`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(c),
    }).catch(() => {});
  }
}

async function seedImport(page, baseUrl) {
  await page.goto(`${baseUrl}/import.html`, { waitUntil: 'domcontentloaded' });
  const hasTx = await page.locator('table', { has: page.locator('th:has-text("Libellé")') })
                          .locator('tbody tr').first().isVisible().catch(() => false);
  if (hasTx) return;

  const fileInput = page.locator('input[type="file"][accept*=".csv"]');
  await fileInput.setInputFiles(CSV_FILE);
  const importBtn = page.locator('main button:has-text("Importer")');
  await importBtn.waitFor({ state: 'visible', timeout: 5000 });
  await importBtn.click();
  await page.locator('main :has-text("transaction(s) importée(s)")').first().waitFor({ state: 'visible', timeout: 5000 });
}

async function runScenario(page, R) {

  // ── 1. Navigation vers Import ─────────────────────────────────────────────
  console.log('▶ [Étape 1] Navigation vers import.html');
  await R.navigate('import.html');

  await R.subtitle(
    'Comment retrouver rapidement une transaction précise\n' +
    'parmi des dizaines d\'opérations bancaires ?',
    4500,
  );
  await R.pause(500);

  // ── 2. Défilement vers la section Transactions ────────────────────
  console.log('▶ [Étape 2] Section Transactions');
  const txTable = page.locator('table', { has: page.locator('th:has-text("Libellé")') });
  await R.scrollTo(txTable);
  await R.pause(400);

  // ── 3. Tri par montant ────────────────────────────────────────────
  console.log('▶ [Étape 3] Tri par montant');
  const amountHeader = txTable.locator('thead tr').first().locator('th:has-text("Montant")');
  await R.moveTo(amountHeader);
  await R.highlight(amountHeader);
  await R.subtitle(
    'Un clic sur l\'en-tête d\'une colonne trie le tableau.',
    3500,
  );
  await R.clickOn(amountHeader);
  await R.pause(800);

  await R.subtitle('Un second clic inverse l\'ordre du tri.', 3000);
  await R.clickOn(amountHeader);
  await R.clearHighlights();
  await R.pause(600);

  // ── 4. Filtrage par libellé ───────────────────────────────────────
  console.log('▶ [Étape 4] Filtrage par libellé');
  const filterLabel = txTable.locator('thead tr').nth(1).locator('th').nth(1).locator('input');
  await R.moveTo(filterLabel);
  await R.highlight(filterLabel);
  await R.subtitle(
    'Les filtres sous les colonnes permettent d\'isoler un poste précis.',
    4000,
  );
  await R.fillField(filterLabel, 'TOTAL');
  await R.clearHighlights();
  await R.pause(600);

  await R.subtitle('Par exemple : toutes les dépenses de carburant Total.', 3500);
  await R.pause(600);

  // ── 5. Compteur de résultats filtrés ──────────────────────────────────────
  console.log('▶ [Étape 5] Compteur');
  const counterBox = page.locator(':has-text("Lignes affichées")').first();
  await R.moveTo(counterBox);
  await R.highlight(counterBox);
  await R.subtitle(
    'Le compteur indique en temps réel le nombre de lignes correspondant aux critères.',
    4500,
  );
  await R.clearHighlights();
  await R.pause(500);

  // ── 6. Réinitialisation des filtres ───────────────────────────────────────
  console.log('▶ [Étape 6] Réinitialisation');
  const resetBtn = page.locator('button:has-text("Réinitialiser les filtres")');
  await R.moveTo(resetBtn);
  await R.highlight(resetBtn);
  await R.subtitle('Un clic sur « Réinitialiser » remet instantanément tout à zéro.', 4000);
  await R.clickOn(resetBtn);
  await R.clearHighlights();
  await R.pause(700);

  // ── 7. Clôture ────────────────────────────────────────────────────────────
  console.log('▶ [Étape 7] Clôture');
  await R.subtitle(
    'Ces outils de tri et filtre restent disponibles sur l\'ensemble des tableaux de l\'application.',
    5000,
  );
  await R.pause(1500);
}

// ─────────────────────────────────────────────────────────────────────────────
// Point d'entrée
// ─────────────────────────────────────────────────────────────────────────────
(async () => {
  console.log('⏺  Vidéo 2.4 — Trier et rechercher dans les transactions');
  const server = await detectServer();
  await seedCategories(server.api);

  const { browser, context, page, recorder } = await createSession({
    outputDir: OUTPUT_DIR,
    baseUrl: server.front,
  });

  try {
    await seedImport(page, server.front);
    await runScenario(page, recorder);
  } catch (err) {
    console.error('Erreur pendant le scénario :', err);
  } finally {
    await finalizeVideo(context, page, 'video-24-recherche-tri.webm', OUTPUT_DIR);
    await browser.close();
  }
})();
