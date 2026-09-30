'use strict';

// VT-500 / F3 : Banque -> Pointage -> Analyse (16-tests.md).
// Dataset bancaire genere relativement au mois courant (helpers/bank-scenario.js), importe par
// POST /budget/import. Les montants attendus sont ceux du scenario backend VT-120 : ce test
// verifie que le front lit les memes chiffres, avec fallback JS desactive et contexte vierge.

const { test, expect } = require('@playwright/test');
const {
  API,
  resetBackendState,
  importBankScenario,
  waitForReactMount,
  expectBackendCall,
  disableJsFallback,
  gotoAndExpectBackend,
  expectFreshBrowserState,
} = require('./helpers');

const EUR = 2;

function byId(rows, id) {
  const row = rows.find(r => r.id === id);
  expect(row, 'element ' + id + ' absent').toBeTruthy();
  return row;
}

function linkOf(matchings, month, budgetLineId) {
  const matching = matchings.find(m => m.month === month);
  expect(matching, 'pointage du mois ' + month + ' absent').toBeTruthy();
  const link = matching.links.find(l => l.budgetLineId === budgetLineId);
  expect(link, 'lien ' + budgetLineId + ' absent').toBeTruthy();
  return link;
}

test.describe('F3 - Banque -> Pointage -> Analyse (VT-500)', () => {
  let months;

  test.beforeEach(async ({ page, request }) => {
    await resetBackendState(request);
    months = await importBankScenario(request);
    await disableJsFallback(page);
  });

  test('import bancaire relu par Pointage, pointage modifie, propage dans Analyse apres reload', async ({ page, request }) => {
    const { m0, m1 } = months;

    // Pointage : donnees importees restituees par le backend.
    const pointage = await gotoAndExpectBackend(page, '/pointage.html', '/pointage');
    expect(pointage.status()).toBe(200);
    await expectFreshBrowserState(page);
    const pointageBody = await pointage.json();
    expect(pointageBody.transactions).toHaveLength(9);
    expect(pointageBody.categories).toHaveLength(4);
    expect(pointageBody.matchings).toHaveLength(2);
    const loyer = byId(pointageBody.transactions, 'tx_1');
    expect(Number(loyer.amount)).toBeCloseTo(-800, EUR);
    expect(loyer.categoryId).toBe('cat_loyer');
    const split = byId(pointageBody.transactions, 'tx_9');
    expect(split.splits.map(s => s.id)).toEqual(['s1', 's2']);
    expect(linkOf(pointageBody.matchings, m1, 'chg_courses').txIds).toEqual(['tx_7', 'tx_9#s1']);
    await expect(page.locator('#root')).not.toBeEmpty();

    // Analyse : etat initial lu par la page (mois courant, pointage d origine).
    const analyse = await gotoAndExpectBackend(page, '/analyse.html', '/analyse');
    expect(analyse.status()).toBe(200);
    const analyseBody = await analyse.json();
    expect(analyseBody.data.bankImport.transactions).toHaveLength(9);
    expect(analyseBody.kpis.uncategorizedCount).toBe(1);
    expect(analyseBody.currentMonthISO).toBe(m0);
    const coursesBefore = byId(analyseBody.landingData, 'chg_courses');
    expect(Number(coursesBefore.budgeted)).toBeCloseTo(400, EUR);
    expect(Number(coursesBefore.reel)).toBeCloseTo(230, EUR);          // 150 pointes + 80 en cours
    expect(Number(coursesBefore.pendingContrib)).toBeCloseTo(80, EUR);
    await expect(page.locator('#root')).not.toContainText('Aucune transaction importée');

    // Valeurs de reference sur 12 mois, relues directement.
    const kpis = (await (await request.get(API + '/analyse?monthsBack=12')).json()).kpis;
    expect(Number(kpis.totalExpenses)).toBeCloseTo(2365, EUR);
    expect(Number(kpis.totalIncome)).toBeCloseTo(5000, EUR);
    expect(Number(kpis.compressibleTotal)).toBeCloseTo(740, EUR);

    // Sequence de pointage representative : tx_4 (cinema 40) rattachee a Courses sur M0.
    const update = await request.put(API + '/pointage/matchings/' + m0, {
      data: { links: [
        { budgetLineId: 'chg_loyer', txIds: ['tx_1'] },
        { budgetLineId: 'chg_courses', txIds: ['tx_2', 'tx_4'] },
        { budgetLineId: 'inc_salaire', txIds: ['tx_3'] },
      ] },
      headers: { 'Content-Type': 'application/json' },
    });
    expect(update.status(), 'PUT /pointage/matchings/' + m0).toBe(200);

    // Relecture directe de /pointage : M0 remplace, M1 inchange.
    const reread = await (await request.get(API + '/pointage')).json();
    expect(linkOf(reread.matchings, m0, 'chg_courses').txIds).toEqual(['tx_2', 'tx_4']);
    expect(linkOf(reread.matchings, m1, 'chg_courses').txIds).toEqual(['tx_7', 'tx_9#s1']);

    // Reload de l Analyse : la page lit la valeur persistee (150 + 40 + 80 en cours = 270).
    const afterCall = expectBackendCall(page, '/analyse', 'GET');
    await page.reload();
    await waitForReactMount(page);
    const afterBody = await (await afterCall).json();
    const coursesAfter = byId(afterBody.landingData, 'chg_courses');
    expect(Number(coursesAfter.reel)).toBeCloseTo(270, EUR);
    expect(Number(coursesAfter.pct)).toBeCloseTo(67.5, EUR);
    expect(afterBody.kpis.uncategorizedCount).toBe(1);

    // Les KPIs dependent des transactions, pas du pointage : inchanges.
    const kpisAfter = (await (await request.get(API + '/analyse?monthsBack=12')).json()).kpis;
    expect(Number(kpisAfter.totalExpenses)).toBeCloseTo(2365, EUR);
    expect(Number(kpisAfter.totalIncome)).toBeCloseTo(5000, EUR);
  });
});
