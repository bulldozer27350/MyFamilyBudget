/**
 * Smoke test de la façade api.js (Vue d'ensemble) hors navigateur.
 */
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const rootDir = path.resolve(__dirname, '..');
const scripts = [
  'js/tokens.js',
  'js/help-content.js',
  'js/models.js',
  'js/csv-parser.js',
  'js/calculations.js',
  'js/data-store.js',
  'js/service-metier.js',
  'js/api.js'
];

const store = {};
const sandbox = {
  window: { addEventListener: () => {}, removeEventListener: () => {}, confirm: () => true },
  document: { getElementById: id => ({ id }), createElement: tag => ({ tag }) },
  localStorage: {
    getItem: k => store[k] || null,
    setItem: (k, v) => { store[k] = String(v); },
    removeItem: k => { delete store[k]; }
  },
  React: { useMemo: (fn) => fn(), useState: () => [], useEffect: () => {}, useCallback: fn => fn },
  console
};
sandbox.window.window = sandbox.window;
sandbox.globalThis = sandbox;
vm.createContext(sandbox);

scripts.forEach(rel => {
  vm.runInContext(fs.readFileSync(path.join(rootDir, rel), 'utf8'), sandbox, { filename: rel });
});

const { BudgetApi } = sandbox.window.BudgetApp;

(async () => {
  const promise = BudgetApi.getVueDensemble({ useConstantEuros: false });
  if (typeof promise.then !== 'function') throw new Error('getVueDensemble ne retourne pas une Promise');
  const overview = await promise;
  const required = ['data', 'years', 'cashflow', 'patrimoine', 'retireYear', 'patrimoineActuel', 'fluxNetActuel', 'retireCharges', 'totalPensions', 'retirePatrimoine', 'fireRente', 'financialOnlyRente'];
  const missing = required.filter(k => overview[k] === undefined);
  if (missing.length) throw new Error('Champs manquants : ' + missing.join(', '));
  const unsubscribe = BudgetApi.onVueDensembleChanged(() => {});
  if (typeof unsubscribe !== 'function') throw new Error('onVueDensembleChanged doit retourner une fonction de désabonnement');
  unsubscribe();
  console.log('OK — années :', overview.years.length, '| retraite :', overview.retireYear, '| patrimoine actuel :', Math.round(overview.patrimoineActuel));
})().catch(err => {
  console.error('ECHEC :', err);
  process.exit(1);
});
