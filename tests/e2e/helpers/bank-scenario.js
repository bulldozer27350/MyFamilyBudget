'use strict';

// Dataset bancaire complementaire du dataset de reference (VT-500, scenario F3).
//
// Le dataset canonique (fixtures/budget-familial.json) a un bankImport vide. L analyse se
// base sur la date du jour : ce scenario est donc construit au moment du test, relativement au
// mois courant (M0) et au mois precedent (M1), puis importe via POST /budget/import. Aucun
// fichier local : tout est genere ici, de facon deterministe (memes montants que le scenario
// backend BankPointageAnalyseScenarioTest, VT-120).
//
// Budget mensuel constant : Loyer 800, Courses 400 (charges), Salaire 2500 (revenu), Livret 200.
// M0 : tx_1 loyer -800 | tx_2 supermarche -150 | tx_3 salaire +2500 | tx_4 cinema -40 | tx_5 divers -25
//      operation en cours : Courses Drive -80 (ligne Courses)
// M1 : tx_6 loyer -800 | tx_7 supermarche -350 | tx_8 salaire +2500
//      tx_9 hypermarche -200 ventile : -120 alimentation (s1) / -80 loisirs (s2)

const { API } = require('./constants');

function pad2(n) {
  return String(n).padStart(2, '0');
}

/** Mois courant (M0) et precedent (M1) au format YYYY-MM, sur l horloge locale. */
function currentMonths(now = new Date()) {
  const y = now.getFullYear();
  const m = now.getMonth() + 1;
  const prev = new Date(y, now.getMonth() - 1, 1);
  return {
    m0: y + '-' + pad2(m),
    m1: prev.getFullYear() + '-' + pad2(prev.getMonth() + 1),
  };
}

/** Construit le BudgetDataDto du scenario bancaire pour les mois donnes. */
function buildBankScenario({ m0, m1 }) {
  const line = (id, label, monthly, categoryId) => ({
    id, label, monthly, start: '2000-01-01', end: '2100-12-31', growthRate: 0, categoryId,
  });
  return {
    settings: {
      birthYear: 1990, retireAge: 64, simulateUntilAge: 85,
      inflationRate: 0, pivotDate: '2026-01-01', pivotMode: 'manual',
      startBalance: 0, childExitAge: 21, taxAbattement: 0,
      pass2026: 47100, passGrowthRate: 0.015, sweepEnabled: false,
    },
    charges: [
      line('chg_loyer', 'Loyer', 800, 'cat_loyer'),
      line('chg_courses', 'Courses', 400, 'cat_courses'),
    ],
    incomes: [line('inc_salaire', 'Salaire', 2500, 'cat_salaire')],
    placements: [{
      id: 'plc_1', label: 'Livret', category: 'Livrets', balance: 1000, balanceDate: '2026-01-01',
      monthly: 200, monthlyFrom: '2000-01-01', monthlyUntil: '2100-12-31',
      ratePess: 0.01, rateCorr: 0.02, rateOpti: 0.03, excludedFromRetirement: false, notes: '',
    }],
    realEstate: [], taxChildren: [], taxBrackets: [], taxRateOverrides: [],
    taxActualOverrides: [], oneoff: [], transfers: [],
    variableIncomes: [], variableOverrides: [], assetCategories: [],
    bankImport: {
      categories: [
        { id: 'cat_loyer', label: 'Logement', kind: 'D\u00e9pense', compressible: 'Non' },
        { id: 'cat_courses', label: 'Alimentation', kind: 'D\u00e9pense', compressible: 'Oui' },
        { id: 'cat_loisirs', label: 'Loisirs', kind: 'D\u00e9pense', compressible: 'Oui' },
        { id: 'cat_salaire', label: 'Revenus', kind: 'Revenu', compressible: 'Non' },
      ],
      transactions: [
        { id: 'tx_1', date: m0 + '-05', label: 'Paiement Loyer', type: 'VIR', amount: -800, categoryId: 'cat_loyer' },
        { id: 'tx_2', date: m0 + '-10', label: 'Supermarche', type: 'CB', amount: -150, categoryId: 'cat_courses' },
        { id: 'tx_3', date: m0 + '-28', label: 'Virement Employeur', type: 'VIR', amount: 2500, categoryId: 'cat_salaire' },
        { id: 'tx_4', date: m0 + '-12', label: 'Cinema', type: 'CB', amount: -40, categoryId: 'cat_loisirs' },
        { id: 'tx_5', date: m0 + '-15', label: 'Divers', type: 'CB', amount: -25 },
        { id: 'tx_6', date: m1 + '-05', label: 'Paiement Loyer', type: 'VIR', amount: -800, categoryId: 'cat_loyer' },
        { id: 'tx_7', date: m1 + '-10', label: 'Supermarche', type: 'CB', amount: -350, categoryId: 'cat_courses' },
        { id: 'tx_8', date: m1 + '-28', label: 'Virement Employeur', type: 'VIR', amount: 2500, categoryId: 'cat_salaire' },
        {
          id: 'tx_9', date: m1 + '-15', label: 'Hypermarche', type: 'CB', amount: -200, categoryId: 'cat_courses',
          splits: [
            { id: 's1', categoryId: 'cat_courses', amount: -120, label: 'Alimentation' },
            { id: 's2', categoryId: 'cat_loisirs', amount: -80, label: 'Loisirs' },
          ],
        },
      ],
      matchings: [
        { month: m0, links: [
          { budgetLineId: 'chg_loyer', txIds: ['tx_1'] },
          { budgetLineId: 'chg_courses', txIds: ['tx_2'] },
          { budgetLineId: 'inc_salaire', txIds: ['tx_3'] },
        ] },
        { month: m1, links: [
          { budgetLineId: 'chg_loyer', txIds: ['tx_6'] },
          { budgetLineId: 'chg_courses', txIds: ['tx_7', 'tx_9#s1'] },
          { budgetLineId: 'inc_salaire', txIds: ['tx_8'] },
        ] },
      ],
      pendingOperations: [
        { id: 'pop_1', date: m0 + '-20', label: 'Courses Drive', amount: -80, type: 'cb',
          categoryId: 'cat_courses', status: 'pending', budgetLineId: 'chg_courses' },
      ],
    },
  };
}

/**
 * Importe le scenario bancaire (POST /budget/import) et retourne les mois utilises.
 * @param {import('@playwright/test').APIRequestContext} request
 * @returns {Promise<{m0: string, m1: string}>}
 */
async function importBankScenario(request) {
  const months = currentMonths();
  const res = await request.post(API + '/budget/import', {
    data: buildBankScenario(months),
    headers: { 'Content-Type': 'application/json' },
  });
  if (res.status() !== 200) {
    throw new Error('POST /budget/import (scenario bancaire) a retourne ' + res.status() + ' (200 attendu)');
  }
  return months;
}

module.exports = { currentMonths, buildBankScenario, importBankScenario };
