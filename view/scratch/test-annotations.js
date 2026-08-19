const fs = require('fs');
const path = require('path');
const vm = require('vm');

const rootDir = path.resolve(__dirname, '..');
const sampleData = JSON.parse(fs.readFileSync(path.join(rootDir, 'budget-familial.json'), 'utf8'));

const sandbox = {
  window: {},
  console: console,
  Intl: Intl,
  Math: Math,
  Date: Date,
  JSON: JSON
};
sandbox.window.window = sandbox.window;

const coreScripts = [
  'js/tokens.js',
  'js/help-content.js',
  'js/models.js',
  'js/csv-parser.js',
  'js/calculations.js',
  'js/data-store.js'
];

for (const script of coreScripts) {
  const code = fs.readFileSync(path.join(rootDir, script), 'utf8');
  vm.runInNewContext(code, sandbox);
}

const BudgetApp = sandbox.window.BudgetApp;
console.log("findEarliestYear:", BudgetApp.findEarliestYear ? BudgetApp.findEarliestYear(sampleData) : 'not exported');
console.log("computePivotBalance:", BudgetApp.computePivotBalance(sampleData));
console.log("settings pivotDate:", sampleData.settings.pivotDate);

const earliestYear = BudgetApp.findEarliestYear ? BudgetApp.findEarliestYear(sampleData) : 2025;
const years = [];
for (let y = earliestYear; y <= 2030; y++) years.push(y);

const timeline = BudgetApp.calculateDetailedFinancialTimeline(sampleData, years, 'corr', false);
console.log("timeline yearly count:", timeline.yearly.length, "first year:", timeline.yearly[0]?.label);
console.log("timeline daily count:", timeline.daily.length, "first date:", timeline.daily[0]?.dateISO, "last date:", timeline.daily[timeline.daily.length - 1]?.dateISO);
