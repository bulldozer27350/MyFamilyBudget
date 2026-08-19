/**
 * Smoke test for all BudgetApi services (Overview, Impots, Retraite, Cashflow, Patrimoine, Settings)
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
  console,
  setTimeout,
  clearTimeout
};
sandbox.window.window = sandbox.window;
sandbox.globalThis = sandbox;
vm.createContext(sandbox);

scripts.forEach(rel => {
  vm.runInContext(fs.readFileSync(path.join(rootDir, rel), 'utf8'), sandbox, { filename: rel });
});

const { BudgetApi } = sandbox.window.BudgetApp;

(async () => {
  console.log("Testing BudgetApi.getVueDensemble...");
  const overview = await BudgetApi.getVueDensemble({ useConstantEuros: false });
  console.log("✓ Overview OK");

  console.log("Testing BudgetApi.getImpots...");
  const impots = await BudgetApi.getImpots();
  console.log("✓ Impots data received:", {
    bracketsCount: impots.taxBrackets.length,
    previewCount: impots.taxPreview.length,
    childExitAge: impots.settings.childExitAge
  });

  console.log("Testing BudgetApi.resetDefaultTaxBrackets...");
  await BudgetApi.resetDefaultTaxBrackets();
  const impotsAfterReset = await BudgetApi.getImpots();
  if (impotsAfterReset.taxBrackets.length !== 5) {
    throw new Error("Expected 5 tax brackets after reset, got " + impotsAfterReset.taxBrackets.length);
  }
  console.log("✓ Reset tax brackets OK (5 brackets)");

  console.log("Testing BudgetApi.updateImpotsSettings...");
  await BudgetApi.updateImpotsSettings("childExitAge", 23);
  const impotsAfterUpdate = await BudgetApi.getImpots();
  if (impotsAfterUpdate.settings.childExitAge !== 23) {
    throw new Error("Expected childExitAge 23, got " + impotsAfterUpdate.settings.childExitAge);
  }
  console.log("✓ Update tax settings OK");

  console.log("Testing BudgetApi.getTresorerie...");
  const tresorerie = await BudgetApi.getTresorerie();
  console.log("✓ Tresorerie OK");

  console.log("Testing BudgetApi.getPatrimoine...");
  const patrimoine = await BudgetApi.getPatrimoine();
  console.log("✓ Patrimoine OK");

  console.log("Testing BudgetApi.getRetraite...");
  const retraite = await BudgetApi.getRetraite();
  console.log("✓ Retraite OK");

  console.log("Testing BudgetApi.getSettings...");
  const settings = await BudgetApi.getSettings();
  console.log("✓ Settings OK");

  console.log("\nALL API TESTS PASSED SUCCESSFULLY! 🎉");
})().catch(err => {
  console.error("TEST FAILED:", err);
  process.exit(1);
});
