const fs = require('fs');
const path = require('path');
const vm = require('vm');

const baseDir = path.resolve(__dirname, '..');

console.log("=== VERIFYING REFACTORED BUDGET FILES ===");

// Check that all HTML files exist
const htmlFiles = [
  'index.html',
  'overview.html',
  'cashflow.html',
  'patrimoine.html',
  'retraite.html',
  'impots.html',
  'settings.html',
  'import.html',
  'pending.html',
  'pointage.html',
  'analyse.html'
];

htmlFiles.forEach(file => {
  const fullPath = path.join(baseDir, file);
  if (!fs.existsSync(fullPath)) {
    throw new Error(`Missing HTML file: ${file}`);
  }
  console.log(`✓ HTML file exists: ${file}`);
});

// Check that all CSS files exist
const cssFiles = [
  'css/variables.css',
  'css/base.css',
  'css/layout.css',
  'css/components.css',
  'css/style.css'
];

cssFiles.forEach(file => {
  const fullPath = path.join(baseDir, file);
  if (!fs.existsSync(fullPath)) {
    throw new Error(`Missing CSS file: ${file}`);
  }
  console.log(`✓ CSS file exists: ${file}`);
});

// Test loading JS modules in a simulated window environment
const sandbox = {
  window: {},
  document: {
    createElement: () => ({ href: '', download: '', click: () => {} }),
    head: { appendChild: () => {} },
    body: { appendChild: () => {} }
  },
  localStorage: {
    store: {},
    getItem(k) { return this.store[k] || null; },
    setItem(k, v) { this.store[k] = String(v); },
    removeItem(k) { delete this.store[k]; }
  },
  console: console,
  React: {
    useState: (init) => [init, () => {}],
    useMemo: (fn) => fn(),
    useEffect: () => {},
    useCallback: (fn) => fn,
    useRef: (init) => ({ current: init }),
    createContext: () => ({ Provider: () => {}, Consumer: () => {} }),
    useContext: () => ({})
  },
  Chart: function() { return { destroy: () => {} }; },
  BroadcastChannel: function() { return { postMessage: () => {}, close: () => {} }; }
};
sandbox.window = sandbox;
sandbox.window.BudgetApp = sandbox.BudgetApp = {};

const jsFiles = [
  'js/tokens.js',
  'js/help-content.js',
  'js/models.js',
  'js/csv-parser.js',
  'js/calculations.js',
  'js/data-store.js'
];

jsFiles.forEach(file => {
  const fullPath = path.join(baseDir, file);
  const code = fs.readFileSync(fullPath, 'utf8');
  vm.createScript(code, { filename: file }).runInNewContext(sandbox);
  console.log(`✓ JS Core script evaluated without error: ${file}`);
});

// Test with budget-familial.json
const jsonPath = path.join(baseDir, 'budget-familial.json');
const rawJSON = fs.readFileSync(jsonPath, 'utf8');
const budgetData = JSON.parse(rawJSON);

console.log(`Loaded budget-familial.json (${rawJSON.length} bytes)`);

const normalized = sandbox.BudgetApp.normalizeData(budgetData);
console.log(`✓ Normalized budgetData: charges=${normalized.charges.length}, incomes=${normalized.incomes.length}, placements=${normalized.placements.length}`);

// Test projections calculation
const proj = sandbox.BudgetApp.calculateDetailedFinancialTimeline(normalized, false);
console.log(`✓ Calculated Projections: ${proj.years.length} years simulated, cashflow count=${proj.cashflow.length}, patrimoine count=${proj.patrimoine.length}`);
console.log(`  Initial net cashflow: ${proj.cashflow[0]?.net}`);
console.log(`  Year 2026 total patrimoine: ${proj.patrimoine[0]?.total}`);

// Test tax calculation
const taxRes = sandbox.BudgetApp.calculateProgressiveTax(60000, 2);
console.log(`✓ Tax calculation (60k€, 2 parts): ${taxRes.impotNet}€`);

// Test retirement
if (normalized.people && normalized.people.length > 0) {
  const retRes = sandbox.BudgetApp.computeRetirementProjection(normalized.people[0], normalized.settings.inflationRate);
  console.log(`✓ Retirement projection calculated for ${normalized.people[0].name}: pensionNetteMois=${retRes.pensionNetteMois}€/mois`);
}

// Test Help Content
const helpKeys = Object.keys(sandbox.BudgetApp.HELP_CONTENT || {});
console.log(`✓ Help Content verified: ${helpKeys.length} sections defined (${helpKeys.join(', ')})`);

console.log("\n=== ALL TESTS PASSED SUCCESSFULLY! ===");
