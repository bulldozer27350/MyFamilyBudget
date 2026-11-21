'use strict';
/**
 * Vidéo 2.2 — Créer des catégories de dépenses
 *
 * Ce que montre cette vidéo :
 *  - Ouverture de la section Catégories dans import.html
 *  - Création d'une catégorie fixe : "Loyer" (Poste réductible : Non)
 *  - Création d'une catégorie variable : "Courses alimentaires" (Poste réductible : Oui)
 *  - Explication du rôle de l'indicateur "Poste réductible" pour l'analyse budgétaire
 *  - Ajout des catégories complémentaires (Crédits, Sorties)
 *
 * Usage : node scripts/videos/run.js 2.2
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
    if (res.ok) console.log(`[Video 2.2] App réinitialisée pour la démo.`);
  } catch (_) {}
}

async function runScenario(page, R) {

  // ── 1. Navigation vers Import ─────────────────────────────────────────────
  console.log('▶ [Étape 1] Navigation vers import.html');
  await R.navigate('import.html');

  await R.subtitle(
    'Avant de classer vos transactions bancaires,\n' +
    'nous allons créer les catégories de dépenses de votre foyer.',
    4500,
  );
  await R.pause(500);

  // ── 2. Ouverture de la section Catégories ──────────────────────────────────
  console.log('▶ [Étape 2] Déplier la section Catégories');
  const catSection = page.locator('div[role="button"]:has-text("Catégories")').first();
  await R.scrollTo(catSection);
  await R.moveTo(catSection);
  await R.highlight(catSection);
  await R.subtitle('On déplie la section Catégories.', 2500);
  await R.clickOn(catSection);
  await R.clearHighlights();
  await R.pause(600);

  // Cibler précisément la table des Catégories (reconnaissable à l'en-tête "Poste réductible")
  const catTable = page.locator('table', { has: page.locator('th:has-text("Poste réductible")') });
  await catTable.waitFor({ state: 'visible', timeout: 5000 });
  const catContainer = page.locator('div', { has: catTable });
  const addBtn = catContainer.locator('button:has-text("+ Ajouter une ligne")').first();

  await R.moveTo(addBtn);
  await R.zoomTo(addBtn, 1.25, 600);

  // ── 3. Catégorie variable : Courses alimentaires ──────────────────────────
  console.log('▶ [Étape 3] Ajout catégorie Courses alimentaires');
  await R.highlight(addBtn);
  await R.subtitle('Sophie ajoute une première catégorie pour les courses du foyer.', 3500);
  await R.clickOn(addBtn);
  await R.clearHighlights();

  const rowCourses = catTable.locator('tbody tr').last();
  await rowCourses.waitFor({ state: 'visible', timeout: 5000 });
  const labelCourses = rowCourses.locator('td').nth(0).locator('input');
  await R.moveTo(labelCourses);
  await R.fillField(labelCourses, 'Courses alimentaires', { blur: true });
  await R.pause(400);

  const compCourses = rowCourses.locator('td').nth(2).locator('select');
  await R.moveTo(compCourses);
  await R.highlight(compCourses);
  await R.subtitle(
    'Les courses peuvent être ajustées en cas de besoin.\n' +
    'On sélectionne « Oui » pour Poste réductible.',
    5000,
  );
  await R.clickOn(compCourses);
  await compCourses.selectOption('Oui');
  await R.clearHighlights();
  await R.pause(600);

  // ── 4. Catégorie fixe : Loyer ─────────────────────────────────────────────
  console.log('▶ [Étape 4] Ajout catégorie Loyer');
  await R.moveTo(addBtn);
  await R.clickOn(addBtn);

  const rowLoyer = catTable.locator('tbody tr').last();
  await rowLoyer.waitFor({ state: 'visible', timeout: 5000 });
  const labelLoyer = rowLoyer.locator('td').nth(0).locator('input');
  await R.moveTo(labelLoyer);
  await R.fillField(labelLoyer, 'Loyer', { blur: true });
  await R.pause(400);

  const compLoyer = rowLoyer.locator('td').nth(2).locator('select');
  await R.moveTo(compLoyer);
  await R.highlight(compLoyer);
  await R.subtitle(
    'Le loyer est une dépense fixe et incompressible :\n' +
    'on laisse « Poste réductible » sur Non.',
    5000,
  );
  await R.clearHighlights();
  await R.pause(600);

  // ── 5. Catégorie loisir : Restaurants & Sorties ───────────────────────────
  console.log('▶ [Étape 5] Ajout Restaurants & Sorties');
  await R.moveTo(addBtn);
  await R.clickOn(addBtn);

  const rowSorties = catTable.locator('tbody tr').last();
  await rowSorties.waitFor({ state: 'visible', timeout: 5000 });
  const labelSorties = rowSorties.locator('td').nth(0).locator('input');
  await R.moveTo(labelSorties);
  await R.fillField(labelSorties, 'Restaurants & Sorties', { blur: true });
  await R.pause(400);

  const compSorties = rowSorties.locator('td').nth(2).locator('select');
  await R.clickOn(compSorties);
  await compSorties.selectOption('Oui');
  await R.pause(500);

  // ── 6. Clôture ────────────────────────────────────────────────────────────
  console.log('▶ [Étape 6] Clôture');
  await R.zoomOut(500);
  await R.pause(400);
  await R.subtitle(
    '💡 Grâce à l\'indicateur "Poste réductible", l\'onglet Analyse\n' +
    'calculera votre capacité réelle d\'économie en isolant ces dépenses.',
    5500,
  );
  await R.subtitle(
    'Vos catégories sont prêtes ! Place à la catégorisation des transactions.',
    4000,
  );
  await R.pause(1500);
}

// ─────────────────────────────────────────────────────────────────────────────
// Point d'entrée
// ─────────────────────────────────────────────────────────────────────────────
(async () => {
  console.log('⏺  Vidéo 2.2 — Créer des catégories de dépenses');
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
    await finalizeVideo(context, page, 'video-22-categories-depenses.webm', OUTPUT_DIR);
    await browser.close();
  }
})();
