'use strict';
/**
 * Vidéo 3.2 — Saisir ses charges récurrentes
 *
 * Usage : node scripts/videos/run.js 3.2
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
    if (res.ok) console.log(`[Video 3.2] Données de démo chargées.`);
  } catch (_) {}
}

async function scrollToCenter(page, locator) {
  try {
    await locator.evaluate(el => el.scrollIntoView({ behavior: 'smooth', block: 'center' }));
    await page.waitForTimeout(400);
  } catch (_) {}
}

async function runScenario(page, R) {
  console.log('▶ [Étape 1] Navigation vers cashflow.html');

  await page.goto(`${R.baseUrl}/cashflow.html`, { waitUntil: 'domcontentloaded' });
  await R.reinject();

  await R.subtitle(
    'Passons aux charges récurrentes :\ntout ce qui sort de votre compte chaque mois.',
    4500
  );

  const sectionCharges = page.getByText('Charges récurrentes', { exact: true }).locator('xpath=ancestor::div[.//table][1]');
  await sectionCharges.waitFor({ state: 'visible', timeout: 8000 }).catch(() => {});
  await page.waitForTimeout(1000);
  await R.hideSubtitle();
  await page.waitForTimeout(300);

  // ── 2. Explication de la règle d'inflation & saisie du Loyer ────────────────
  console.log('▶ [Étape 2] Saisie du Loyer (croissance 0 %)');

  await scrollToCenter(page, sectionCharges);
  await R.moveTo(sectionCharges);
  await R.zoomTo(sectionCharges, 1.15, 600);

  await R.subtitle(
    'Règle d\'or : toute charge sans taux saisi suit l\'inflation.\nPour geler un montant (ex: loyer fixe), mettez explicitement 0 %.',
    5200,
  );

  const btnAdd = sectionCharges.locator('button:has-text("Ajouter une ligne")').first();
  await R.clickOn(btnAdd);
  await page.waitForTimeout(400);

  const rowLoyer = sectionCharges.locator('table tbody tr').last();
  const inputsLoyer = rowLoyer.locator('input');

  await R.moveTo(inputsLoyer.nth(0));
  await R.fillField(inputsLoyer.nth(0), 'Loyer');

  await R.moveTo(inputsLoyer.nth(1));
  await R.fillField(inputsLoyer.nth(1), '950');

  // Croissance annuelle explicite à 0 %
  await R.moveTo(inputsLoyer.nth(2));
  await R.fillField(inputsLoyer.nth(2), '0');

  await R.subtitle(
    'Sans ce 0 %, le loyer augmenterait de 2 % chaque année avec l\'inflation,\nce qui fausserait les prévisions à long terme.',
    5000,
  );

  // ── 3. Saisie des Courses alimentaires (indexées sur l'inflation) ───────────
  console.log('▶ [Étape 3] Saisie des Courses alimentaires');

  await R.clickOn(btnAdd);
  await page.waitForTimeout(400);

  const rowCourses = sectionCharges.locator('table tbody tr').last();
  const inputsCourses = rowCourses.locator('input');

  await R.moveTo(inputsCourses.nth(0));
  await R.fillField(inputsCourses.nth(0), 'Courses alimentaires');

  await R.moveTo(inputsCourses.nth(1));
  await R.fillField(inputsCourses.nth(1), '600');

  // On laisse le taux vide ou placeholder pour suivre l'inflation
  await R.subtitle(
    'Pour les courses (600 €/mois), on laisse le taux vide :\nelles suivront fidèlement l\'inflation de 2 % par an.',
    4500,
  );

  // ── 4. Ajout rapide des autres postes ─────────────────────────────────────
  console.log('▶ [Étape 4] Autres charges courantes');

  const chargesExtras = [
    { label: 'Carburant', amount: '180', rate: '' },
    { label: 'Électricité / Gaz', amount: '120', rate: '' },
    { label: 'Assurances', amount: '90', rate: '0' },
    { label: 'Internet / Mobile', amount: '60', rate: '0' },
  ];

  for (const c of chargesExtras) {
    await btnAdd.click();
    await page.waitForTimeout(150);
    const r = sectionCharges.locator('table tbody tr').last();
    const inps = r.locator('input');
    await inps.nth(0).fill(c.label);
    await inps.nth(1).fill(c.amount);
    if (c.rate) await inps.nth(2).fill(c.rate);
  }

  await R.subtitle(
    'Carburant, énergie, assurances, abonnements...\nPlus votre inventaire est complet, plus le prévisionnel sera fiable.',
    4800,
  );

  await R.zoomOut(500);
  await R.pause(400);

  // ── 5. Graphique de trésorerie actualisé ───────────────────────────────────
  console.log('▶ [Étape 5] Graphique de trésorerie mis à jour');

  const canvasTreso = page.locator('canvas').first();
  await scrollToCenter(page, canvasTreso);
  await R.moveTo(canvasTreso);
  await R.highlight(canvasTreso);
  await R.zoomTo(canvasTreso, 1.2, 600);

  await R.subtitle(
    'La courbe de trésorerie prend en compte vos charges et vos revenus,\nmettant en évidence votre capacité d\'épargne réelle.',
    5000,
  );

  await R.clearHighlights();
  await R.zoomOut(500);
  await R.pause(400);

  // ── 6. Clôture ────────────────────────────────────────────────────────────
  console.log('▶ [Étape 6] Clôture');
  await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'smooth' }));
  await R.pause(500);
  await R.subtitle(
    'Charges courantes enregistrées !\nNous verrons ensuite comment gérer les mensualités de crédits.',
    4500,
  );
  await R.pause(1200);
}

(async () => {
  console.log('⏺  Vidéo 3.2 — Saisir ses charges récurrentes');
  const server = await detectServer();
  await loadDemoData(server.api);

  const { browser, context, page, recorder } = await createSession({
    outputDir: OUTPUT_DIR,
    baseUrl: server.front,
  });

  recorder.baseUrl = server.front;

  try {
    await runScenario(page, recorder);
  } catch (err) {
    console.error('Erreur pendant le scénario :', err);
  } finally {
    await finalizeVideo(context, page, 'video-32-charges-recurrentes.webm', OUTPUT_DIR);
    await browser.close();
  }
})();
