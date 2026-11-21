'use strict';
/**
 * Vidéo 2.5 — Opérations en cours (solde réel disponible)
 *
 * Ce que montre cette vidéo :
 *  - Différence entre Solde Banque (Relevé) et Solde Disponible Réel
 *  - Saisie manuelle d'un chèque émis non encore encaissé (150 €)
 *  - Impact immédiat sur la trésorerie disponible
 *  - Présentation de l'import d'encours CB différé
 *
 * Usage : node scripts/videos/run.js 2.5
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
    if (res.ok) console.log(`[Video 2.5] App réinitialisée pour la démo.`);
  } catch (_) {}
}

async function seedImport(page, baseUrl) {
  await page.goto(`${baseUrl}/import.html`, { waitUntil: 'domcontentloaded' });
  const hasTx = await page.locator('tbody tr').first().isVisible().catch(() => false);
  if (hasTx) return;

  const fileInput = page.locator('input[type="file"][accept*=".csv"]');
  await fileInput.setInputFiles(CSV_FILE);
  const importBtn = page.locator('main button:has-text("Importer")');
  await importBtn.waitFor({ state: 'visible', timeout: 5000 });
  await importBtn.click();
  await page.locator('main :has-text("transaction(s) importée(s)")').first().waitFor({ state: 'visible', timeout: 5000 });
}

async function runScenario(page, R) {

  // ── 1. Navigation : on part de import.html puis on glisse vers pending.html ─
  console.log('▶ [Étape 1] Navigation import.html → pending.html');
  await R.navigate('import.html');
  await R.subtitle(
    'Votre solde bancaire ne reflète pas toujours votre argent réellement disponible.',
    4500,
  );
  await R.pause(300);

  // Clic sur le lien de navigation vers Opérations en cours dans la sidebar
  const pendingNavLink = page.locator('a[href="pending.html"]').first();
  await R.moveTo(pendingNavLink);
  await R.highlight(pendingNavLink);
  await R.pause(600);
  await R.clickOn(pendingNavLink);
  await R.clearHighlights();
  await page.waitForURL('**/pending.html', { timeout: 5000 }).catch(() => {});
  await page.waitForLoadState('domcontentloaded');
  await R.pause(500);

  await R.subtitle(
    'Thomas a signé un chèque de 150 € chez le médecin que la banque n\'a pas encore débité.',
    5000,
  );
  await R.pause(500);

  // ── 2. Distinction Solde Banque vs Solde Disponible ────────────────────────
  console.log('▶ [Étape 2] Analyse des KPIs');
  const kpiBanque = page.locator(':has-text("Solde Banque (Relevé)")').first();
  await R.moveTo(kpiBanque);
  await R.zoomTo(kpiBanque, 1.25, 600);
  await R.subtitle('Le Solde Banque affiche ce qui figure actuellement sur le compte.', 3500);

  const kpiDispo = page.locator(':has-text("Solde Disponible Réel")').first();
  await R.moveTo(kpiDispo);
  await R.subtitle(
    'Le Solde Disponible Réel retranche les dépenses déjà engagées mais pas encore prélevées.',
    5500,
  );
  await R.zoomOut(500);
  await R.pause(400);

  // ── 3. Saisie d'un chèque émis ─────────────────────────────────────────────
  console.log('▶ [Étape 3] Nouvelle opération engagée');
  const addBtn = page.locator('button:has-text("Nouvelle opération")');
  await R.moveTo(addBtn);
  await R.highlight(addBtn);
  await R.subtitle('Thomas enregistre son chèque pour ne pas l\'oublier.', 3500);
  await R.clickOn(addBtn);
  await R.clearHighlights();

  // Attendre l'ouverture de la modale
  const modal = page.locator('form').first();
  await modal.waitFor({ state: 'visible', timeout: 5000 });
  await R.zoomTo(modal, 1.15, 600);

  // "Chèque" est déjà la valeur par défaut du type — on se contente de le
  // mettre en évidence pour montrer ce champ, sans re-sélectionner.
  const typeSelect = modal.locator('select').nth(1); // 2e select = Type d'opération
  await R.moveTo(typeSelect);
  await R.highlight(typeSelect);
  await R.subtitle('Type : Chèque — c\'est déjà la valeur par défaut.', 2500);
  await R.clearHighlights();
  await R.pause(300);

  // Libellé / Bénéficiaire — input avec placeholder "Dr Martin, Peintre..."
  const labelInput = modal.locator('input[placeholder*="Dr"]');
  await R.moveTo(labelInput);
  await R.subtitle('Bénéficiaire : Dr Lefebvre — consultation.', 3000);
  await R.fillField(labelInput, 'Dr Lefebvre — consultation');
  await R.pause(300);

  // Montant — le seul input[type="number"] dans la modale
  const amountInput = modal.locator('input[type="number"]').first();
  await R.moveTo(amountInput);
  await R.subtitle('Montant : 150 € (dépense engagée).', 3000);
  await R.fillField(amountInput, '150');
  await R.pause(400);

  // Enregistrer
  const saveBtn = modal.locator('button[type="submit"]');
  await R.moveTo(saveBtn);
  await R.clickOn(saveBtn);
  await R.zoomOut(500);
  await R.pause(600);

  // ── 4. Constat de la mise à jour du Solde Disponible ───────────────────────
  console.log('▶ [Étape 4] Mise à jour Solde Disponible');
  await R.moveTo(kpiDispo);
  await R.highlight(kpiDispo);
  await R.subtitle(
    'Le solde réel disponible a été immédiatement déduit de 150 €.',
    4500,
  );
  await R.subtitle(
    'C\'est ce montant qui fait foi pour savoir si vous passerez le mois sans découvert.',
    4500,
  );
  await R.clearHighlights();
  await R.pause(500);

  // ── 5. Import encours CB différée & Clôture ────────────────────────────────
  console.log('▶ [Étape 5] Encours CB & Clôture');
  const cbBtn = page.locator('button:has-text("Importer encours CB")');
  await R.moveTo(cbBtn);
  await R.highlight(cbBtn);
  await R.subtitle(
    '💡 Si vous avez une carte à débit différé, le bouton « Importer encours CB »\n' +
    'permet d\'intégrer directement le fichier fourni par votre banque.',
    5500,
  );
  await R.clearHighlights();
  await R.pause(1500);
}

// ─────────────────────────────────────────────────────────────────────────────
// Point d'entrée
// ─────────────────────────────────────────────────────────────────────────────
(async () => {
  console.log('⏺  Vidéo 2.5 — Opérations en cours (solde réel disponible)');
  const server = await detectServer();

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
    await finalizeVideo(context, page, 'video-25-operations-en-cours.webm', OUTPUT_DIR);
    await browser.close();
  }
})();
