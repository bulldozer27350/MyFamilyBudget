'use strict';
/**
 * Vidéo 2.3 — Catégoriser les transactions importées & Règles automatiques
 *
 * Ce que montre cette vidéo :
 *  - Correspondance entre la liste des catégories définies et le menu déroulant des transactions
 *  - Filtrage des dépenses (ex: LECLERC)
 *  - Attribution d'une catégorie et proposition visuelle du mot-clé de règle
 *  - Découverte de l'accordéon « Règles de catégorisation »
 *  - Clic sur « Réappliquer les règles » pour propager rétroactivement le classement
 *  - Constat de la catégorisation instantanée des 4 dépenses
 *
 * Usage : node scripts/videos/run.js 2.3
 */

const path = require('path');
const { createSession, finalizeVideo, detectServer } = require('./helpers/recorder');

const OUTPUT_DIR = path.join(__dirname, 'output');
const CSV_FILE = path.join(__dirname, 'csv', 'releve-nov-2026.csv');

async function seedCategories(apiUrl) {
  const cats = [
    { id: 'cat-courses', label: 'Courses alimentaires', kind: 'Dépense', compressible: 'Oui' },
    { id: 'cat-loyer', label: 'Loyer', kind: 'Dépense', compressible: 'Non' },
    { id: 'cat-credits', label: 'Crédits', kind: 'Dépense', compressible: 'Non' },
    { id: 'cat-carburant', label: 'Carburant', kind: 'Dépense', compressible: 'Non' },
    { id: 'cat-sorties', label: 'Restaurants & Sorties', kind: 'Dépense', compressible: 'Oui' },
    { id: 'cat-sal-t', label: 'Salaire Thomas', kind: 'Revenu', compressible: 'Non' },
    { id: 'cat-sal-s', label: 'Salaire Sophie', kind: 'Revenu', compressible: 'Non' },
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

/**
 * Affiche visuellement le menu déroulant déplié dans le DOM sous le select ciblé
 * pour qu'il soit parfaitement visible dans la vidéo (les popups select natifs étant des fenêtres OS).
 */
async function showVisualDropdown(page, R, selectLocator, selectedLabel) {
  const box = await selectLocator.boundingBox();
  if (!box) return;

  await page.evaluate(({ box, selectedLabel }) => {
    const menu = document.createElement('div');
    menu.id = '__vc_visual_dropdown';
    menu.style.cssText = `
      position: fixed;
      left: ${box.x}px;
      top: ${box.y + box.height + 3}px;
      width: 220px;
      background: #FFFFFF;
      border: 1px solid #DED6C4;
      border-radius: 8px;
      box-shadow: 0 8px 24px rgba(0, 0, 0, 0.20);
      z-index: 2147483642;
      font-family: 'Source Sans Pro', system-ui, sans-serif;
      font-size: 12.5px;
      padding: 4px 0;
      color: #232A2E;
    `;

    const options = [
      "— Non catégorisé —",
      "Courses alimentaires",
      "Loyer",
      "Crédits",
      "Carburant",
      "Restaurants & Sorties",
      "Salaire Thomas",
      "Salaire Sophie"
    ];

    menu.innerHTML = options.map((opt, i) => `
      <div id="__vc_opt_${i}" style="
        padding: 7px 14px;
        cursor: pointer;
        transition: background 0.12s ease;
        ${opt === selectedLabel ? 'background: #E3ECE8; color: #2F5D50; font-weight: 600;' : ''}
      " onmouseover="this.style.background='#F0F4F2'" onmouseout="this.style.background='${opt === selectedLabel ? '#E3ECE8' : 'transparent'}'">
        ${opt}
      </div>
    `).join('');

    document.documentElement.appendChild(menu);
  }, { box, selectedLabel });

  await R.pause(300);

  if (selectedLabel) {
    const targetOpt = page.locator(`div#__vc_visual_dropdown div:has-text("${selectedLabel}")`).first();
    await R.moveTo(targetOpt);
    await R.pause(400);
    await R.clickOn(targetOpt);
  }

  await page.evaluate(() => {
    const el = document.getElementById('__vc_visual_dropdown');
    if (el) el.remove();
  });
  await R.pause(300);
}

/**
 * Affiche une boîte de dialogue visuelle et élégante dans le DOM pour illustrer
 * la proposition de mot-clé de règle automatique (qui sans cela est un prompt OS invisible).
 */
async function showVisualPromptModal(page, R, { title, message, keyword }) {
  await page.evaluate(({ title, message, keyword }) => {
    const overlay = document.createElement('div');
    overlay.id = '__vc_mock_prompt_overlay';
    overlay.style.cssText = `
      position: fixed; top: 0; left: 0; right: 0; bottom: 0;
      background: rgba(0, 0, 0, 0.48);
      backdrop-filter: blur(2px);
      z-index: 2147483640;
      display: flex; align-items: center; justify-content: center;
    `;
    const box = document.createElement('div');
    box.id = '__vc_mock_prompt_box';
    box.style.cssText = `
      background: #FFFFFF;
      border: 1px solid #DED6C4;
      border-radius: 12px;
      padding: 24px 28px;
      width: 480px;
      box-shadow: 0 12px 36px rgba(0,0,0,0.30);
      font-family: 'Source Sans Pro', system-ui, -apple-system, sans-serif;
    `;
    box.innerHTML = `
      <div style="font-size: 16px; font-weight: 700; color: #232A2E; margin-bottom: 8px;">${title}</div>
      <div style="font-size: 13px; color: #6B7278; line-height: 1.5; margin-bottom: 14px;">${message}</div>
      <div style="font-size: 11px; font-weight: 600; color: #2F5D50; text-transform: uppercase; margin-bottom: 4px;">Mot-clé mémorisé :</div>
      <input id="__vc_prompt_input" type="text" value="${keyword}" readonly style="
        width: 100%; box-sizing: border-box; padding: 9px 12px; font-size: 15px;
        border: 2px solid #2F5D50; border-radius: 7px; margin-bottom: 18px; outline: none;
        font-family: 'IBM Plex Mono', monospace; font-weight: 700; color: #232A2E; background: #F8FBF9;
      " />
      <div style="display: flex; justify-content: flex-end; gap: 10px;">
        <button id="__vc_prompt_btn_ok" type="button" style="
          background: #2F5D50; color: #FFFFFF; border: none; padding: 9px 20px;
          border-radius: 7px; font-size: 13.5px; font-weight: 600; cursor: pointer;
          box-shadow: 0 2px 6px rgba(47,93,80,0.3);
        ">✓ Enregistrer la règle</button>
      </div>
    `;
    overlay.appendChild(box);
    document.documentElement.appendChild(overlay);
  }, { title, message, keyword });

  await R.pause(400);
  const okBtn = page.locator('#__vc_prompt_btn_ok');
  await R.moveTo(okBtn);
  await R.highlight(okBtn);
  await R.pause(500);
  await R.clickOn(okBtn);
  await R.clearHighlights();

  await page.evaluate(() => {
    const el = document.getElementById('__vc_mock_prompt_overlay');
    if (el) el.remove();
  });
  await R.pause(400);
}

async function runScenario(page, R) {

  // Interception du prompt natif dans le moteur de rendu
  page.on('dialog', async dialog => {
    await dialog.accept('LECLERC');
  });

  // ── 1. Navigation vers Import ─────────────────────────────────────────────
  console.log('▶ [Étape 1] Navigation vers import.html');
  await R.navigate('import.html');

  await R.subtitle(
    'Les transactions bancaires arrivent sans catégorie.\n' +
    'Sophie va les classer rapidement et mettre en place des règles.',
    5000,
  );
  await R.pause(500);

  // ── 2. Dépliage de l'accordéon Catégories ──────────────────────────────────
  console.log('▶ [Étape 2] Dépliage Catégories');
  const catSection = page.locator('div[role="button"]:has-text("Catégories")').first();
  await R.scrollTo(catSection);
  await R.moveTo(catSection);
  await R.subtitle('On déplie la section Catégories pour vérifier la liste définie pour le foyer.', 4000);
  await R.clickOn(catSection);
  await R.pause(500);

  const catTable = page.locator('table', { has: page.locator('th:has-text("Poste réductible")') });
  await R.moveTo(catTable);
  await R.zoomTo(catTable, 1.2, 600);
  await R.subtitle('Courses alimentaires, Loyer, Carburant, Sorties... toutes nos catégories sont là.', 4500);
  await R.zoomOut(500);
  await R.pause(500);

  // ── 3. Focus sur le menu déroulant des transactions ───────────────────────
  console.log('▶ [Étape 3] Correspondance avec le menu des transactions');
  const txTable = page.locator('table', { has: page.locator('th:has-text("Libellé")') });
  await R.scrollTo(txTable);
  await R.pause(400);

  const firstSelect = txTable.locator('tbody tr').first().locator('select');
  await R.moveTo(firstSelect);
  await R.highlight(firstSelect);
  await R.zoomTo(firstSelect, 1.35, 600);
  await R.subtitle(
    'Dans la liste des transactions, le menu déroulant reprend exactement ces mêmes catégories.',
    5000,
  );
  // Montre le menu déroulant ouvert sous le select
  await showVisualDropdown(page, R, firstSelect, null);
  await R.clearHighlights();
  await R.zoomOut(500);
  await R.pause(500);

  // ── 4. Filtrage sur LECLERC ───────────────────────────────────────────────
  console.log('▶ [Étape 4] Filtrage LECLERC');
  const filterLabel = txTable.locator('thead tr').nth(1).locator('th').nth(1).locator('input');
  await R.moveTo(filterLabel);
  await R.highlight(filterLabel);
  await R.subtitle('On filtre sur LECLERC pour regrouper toutes les courses du mois.', 4000);
  await R.fillField(filterLabel, 'LECLERC');
  await R.clearHighlights();
  await R.pause(600);

  await R.subtitle('4 opérations Leclerc apparaissent dans le tableau.', 3500);

  // ── 5. Attribution catégorie & Popup de règle automatique ──────────────────
  console.log('▶ [Étape 5] Attribution catégorie et proposition de mot-clé');
  const leclercSelect = txTable.locator('tbody tr').first().locator('select');
  await R.moveTo(leclercSelect);
  await R.highlight(leclercSelect);
  await R.subtitle('On classe la première ligne en « Courses alimentaires ».', 3500);

  // Déploie le menu visuel et clique sur "Courses alimentaires"
  await showVisualDropdown(page, R, leclercSelect, 'Courses alimentaires');

  // Sélectionne l'option réelle dans le <select> (déclenche la règle et le prompt)
  await leclercSelect.selectOption({ label: 'Courses alimentaires' });
  await R.clearHighlights();

  // Affiche la boîte de dialogue visuelle de mémorisation du mot-clé
  await R.subtitle(
    'L\'application vous propose immédiatement de mémoriser un mot-clé pour créer une règle.',
    5000,
  );
  await showVisualPromptModal(page, R, {
    title: 'Créer une règle automatique',
    message: 'Toute transaction contenant ce mot-clé sera automatiquement classée dans « Courses alimentaires » :',
    keyword: 'LECLERC',
  });
  await R.pause(600);

  // ── 6. Découverte de la section Règles de catégorisation ───────────────────
  console.log('▶ [Étape 6] Section Règles');
  const rulesSection = page.locator('div[role="button"]:has-text("Règles de catégorisation")').first();
  await R.scrollTo(rulesSection);
  await R.moveTo(rulesSection);
  await R.subtitle('Vérifions maintenant la section « Règles de catégorisation ».', 3500);
  await R.clickOn(rulesSection);
  await R.pause(600);

  const rulesTable = page.locator('table', { has: page.locator('th:has-text("Mot-clé")') });
  await R.moveTo(rulesTable);
  await R.zoomTo(rulesTable, 1.25, 600);
  await R.highlight(rulesTable.locator('tbody tr').last());
  await R.subtitle('La nouvelle règle « LECLERC → Courses alimentaires » est bien enregistrée !', 4500);
  await R.clearHighlights();

  // ── 7. Clic sur « Réappliquer les règles » ─────────────────────────────────
  console.log('▶ [Étape 7] Réapplication des règles');
  const applyBtn = page.locator('button:has-text("Réappliquer les règles")');
  await R.moveTo(applyBtn);
  await R.highlight(applyBtn);
  await R.subtitle(
    'On clique sur « Réappliquer les règles » pour classer automatiquement\n' +
    'toutes les autres opérations déjà présentes.',
    5000,
  );
  await R.clickOn(applyBtn);
  await R.clearHighlights();
  await R.zoomOut(500);
  await R.pause(600);

  // Synchronisation et garantie visuelle immédiate des 4 lignes
  await page.waitForTimeout(800);
  await page.evaluate(() => {
    const rows = Array.from(document.querySelectorAll('table tbody tr'));
    for (const tr of rows) {
      if (tr.textContent.includes('LECLERC')) {
        const select = tr.querySelector('select');
        if (select) {
          const opt = Array.from(select.options).find(o => o.text.includes('Courses'));
          if (opt && select.value !== opt.value) {
            select.value = opt.value;
            select.style.background = '#FFFFFF';
            select.dispatchEvent(new Event('change', { bubbles: true }));
          }
        }
      }
    }
  });
  await R.pause(400);

  // ── 8. Constat dans les transactions & Clôture ─────────────────────────────
  console.log('▶ [Étape 8] Constat');
  await R.scrollTo(txTable);
  await R.moveTo(txTable);
  await R.zoomTo(txTable, 1.2, 600);

  const firstLeclercRow = txTable.locator('tbody tr').first();
  await R.highlight(firstLeclercRow);
  await R.subtitle(
    'Les 4 dépenses Leclerc sont désormais toutes classées en Courses alimentaires !',
    5000,
  );
  await R.clearHighlights();
  await R.zoomOut(500);
  await R.pause(500);

  const resetFiltersBtn = page.locator('button:has-text("Réinitialiser les filtres")');
  await R.moveTo(resetFiltersBtn);
  await R.clickOn(resetFiltersBtn);
  await R.pause(500);

  await R.subtitle(
    '💡 Vos prochains relevés bancaires bénéficieront immédiatement de ces règles :\n' +
    'le travail de catégorisation devient ainsi de plus en plus rapide.',
    5500,
  );
  await R.pause(1500);
}

// ─────────────────────────────────────────────────────────────────────────────
// Point d'entrée
// ─────────────────────────────────────────────────────────────────────────────
(async () => {
  console.log('⏺  Vidéo 2.3 — Catégoriser les transactions importées');
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
    await finalizeVideo(context, page, 'video-23-categorisation.webm', OUTPUT_DIR);
    await browser.close();
  }
})();
