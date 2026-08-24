const fs = require('fs');
const path = require('path');
const vm = require('vm');

async function runTests() {
  console.log("=== VERIFYING RUNTIME EXECUTION OF ALL MODULAR PAGES ===");

  const rootDir = path.resolve(__dirname, '..');
  const jsonPath = fs.existsSync(path.join(rootDir, 'budget-familial.json')) 
    ? path.join(rootDir, 'budget-familial.json') 
    : path.join(__dirname, '..', '..', 'budget-familial.json');
  const sampleData = fs.existsSync(jsonPath) ? JSON.parse(fs.readFileSync(jsonPath, 'utf8')) : {};

  const coreScripts = [
    'js/tokens.js',
    'js/help-content.js',
    'js/models.js',
    'js/csv-parser.js',
    'js/calculations.js',
    'js/data-store.js',
    'js/service-metier.js',
    'js/api.js',
    'js/components/ui-base.js',
    'js/components/charts.js',
    'js/components/help-modal.js',
    'js/components/app-layout.js',
    'js/views/overview-view.js',
    'js/views/cashflow-view.js',
    'js/views/patrimoine-view.js',
    'js/views/retraite-view.js',
    'js/views/impots-view.js',
    'js/views/settings-view.js',
    'js/views/import-view.js',
    'js/views/pending-view.js',
    'js/views/pointage-view.js',
    'js/views/analyse-view.js',
  ];

  function createFreshPageContext(pageName) {
    const localStorageStore = {
      budget_familial_data_v1: JSON.stringify(sampleData)
    };
    const mockLocalStorage = {
      getItem: (k) => localStorageStore[k] || null,
      setItem: (k, v) => { localStorageStore[k] = String(v); },
      removeItem: (k) => { delete localStorageStore[k]; },
      clear: () => { Object.keys(localStorageStore).forEach(k => delete localStorageStore[k]); }
    };

    const sandbox = {
      window: {
        location: { href: `file:///D:/Depots/gestion%20comptes/${pageName}` },
        addEventListener: () => {},
        removeEventListener: () => {},
        confirm: () => true,
        alert: (m) => console.log("[ALERT]", m)
      },
      document: {
        getElementById: (id) => ({ id }),
        createElement: (tag) => ({ tag }),
      },
      localStorage: mockLocalStorage,
      BroadcastChannel: class {
        constructor(name) { this.name = name; }
        postMessage() {}
        close() {}
      },
      console: console,
      Intl: Intl,
      Math: Math,
      Date: Date,
      JSON: JSON,
      setTimeout: setTimeout,
      clearTimeout: clearTimeout
    };
    sandbox.window.localStorage = mockLocalStorage;
    sandbox.window.document = sandbox.document;
    sandbox.window.window = sandbox.window;

    // React mock
    const React = {
      useState: (init) => [typeof init === 'function' ? init() : init, () => {}],
      useMemo: (fn) => fn(),
      useCallback: (fn) => fn,
      useEffect: (fn) => {},
      useRef: (init) => ({ current: init }),
      createElement: (type, props, ...children) => ({ type, props: props || {}, children }),
      createContext: () => ({ Provider: () => {}, Consumer: () => {} })
    };
    sandbox.React = React;
    sandbox.window.React = React;

    sandbox.ReactDOM = {
      createRoot: (el) => ({
        render: (element) => {
          sandbox._lastRendered = element;
          if (typeof element.type === 'function') {
            const output = element.type(element.props);
            if (output && output.props && typeof output.props.children === 'function') {
              output.props.children({ openHelp: () => {} });
            }
          }
        }
      })
    };
    sandbox.window.ReactDOM = sandbox.ReactDOM;

    vm.createContext(sandbox);

    for (const script of coreScripts) {
      const code = fs.readFileSync(path.join(rootDir, script), 'utf8');
      vm.runInContext(code, sandbox);
    }

    sandbox.BudgetApp = sandbox.window.BudgetApp;
    return sandbox;
  }

  console.log("Testing all 10 HTML pages in independent browser environments...");
  const htmlFiles = [
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

  let passed = 0;
  for (const htmlFile of htmlFiles) {
    const sandbox = createFreshPageContext(htmlFile);
    const content = fs.readFileSync(path.join(rootDir, htmlFile), 'utf8');
    const scriptMatch = content.match(/<script>([\s\S]*?)<\/script>/);
    if (!scriptMatch) {
      console.error(`  ✗ No <script> found in ${htmlFile}`);
      continue;
    }
    const inlineCode = scriptMatch[1];
    try {
      vm.runInContext(inlineCode, sandbox);
      console.log(`  ✓ PASS: ${htmlFile} (Component rendered successfully)`);
      passed++;
    } catch (e) {
      console.error(`  ✗ FAIL: ${htmlFile} failed to execute:`, e);
    }
  }

  console.log(`\n=== FINAL RESULT: ${passed}/${htmlFiles.length} pages passed runtime verification! ===`);
}

runTests().catch(console.error);
