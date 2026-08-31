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

  console.log("Testing BudgetApi.getPointage...");
  const pointage = await BudgetApi.getPointage();
  if (!pointage || !Array.isArray(pointage.transactions)) {
    throw new Error("Invalid pointage data");
  }
  console.log("✓ Pointage OK");

  console.log("Testing BudgetApi.savePointageMatching...");
  await BudgetApi.savePointageMatching("2026-05", [{ budgetLineId: "c1", txIds: ["tx1"] }]);
  const pointageAfterSave = await BudgetApi.getPointage();
  if (!pointageAfterSave.matchings.some(m => m.month === "2026-05")) {
    throw new Error("Failed to save pointage matching");
  }
  console.log("✓ Save pointage matching OK");

  console.log("Testing BudgetApi.getPendingOperations & savePendingOperation (saisie manuelle)...");
  const pending = await BudgetApi.getPendingOperations();
  if (!pending || !Array.isArray(pending.pendingOperations)) {
    throw new Error("Invalid pending operations data");
  }
  const savedManual = await BudgetApi.savePendingOperation({
    date: "2026-06-12",
    expectedDate: "2026-06-18",
    type: "cheque",
    refNumber: "CHQ-778899",
    label: "Saisie Manuelle Test Cheque",
    amount: -45.50,
    categoryId: "",
    notes: "Note de test"
  });
  if (!savedManual || !savedManual.id) {
    throw new Error("Failed to save manual pending operation");
  }
  const pendingAfterManual = await BudgetApi.getPendingOperations();
  if (!pendingAfterManual.pendingOperations.some(op => op.refNumber === "CHQ-778899")) {
    throw new Error("Manual pending operation not found in getPendingOperations result");
  }

  // Test modifying existing pending operation with splits, category, and notes
  console.log("Testing BudgetApi.savePendingOperation (modification with splits, category, notes)...");
  const modifiedManual = await BudgetApi.savePendingOperation({
    date: "2026-06-14",
    expectedDate: "2026-06-22",
    type: "cheque",
    refNumber: "CHQ-778899-MOD",
    label: "Saisie Manuelle Test Cheque Modifié",
    amount: -75.00,
    categoryId: "cat_brico",
    notes: "Note mise à jour avec ventilation",
    splits: [
      { id: "sp_1", categoryId: "cat_brico", amount: -50.00, label: "Partie Bricolage" },
      { id: "sp_2", categoryId: "cat_jardin", amount: -25.00, label: "Partie Jardinage" }
    ]
  }, savedManual.id);

  if (!modifiedManual || modifiedManual.id !== savedManual.id) {
    throw new Error("Failed to modify existing pending operation");
  }
  if (modifiedManual.categoryId !== "cat_brico" || modifiedManual.notes !== "Note mise à jour avec ventilation") {
    throw new Error("Modified pending operation fields (category or notes) not updated");
  }
  if (!Array.isArray(modifiedManual.splits) || modifiedManual.splits.length !== 2) {
    throw new Error("Modified pending operation splits not preserved");
  }

  const pendingAfterEdit = await BudgetApi.getPendingOperations();
  const checkOp = pendingAfterEdit.pendingOperations.find(op => op.id === savedManual.id);
  if (!checkOp) {
    throw new Error("Modified operation not found in getPendingOperations");
  }
  if (checkOp.categoryId !== "cat_brico" || checkOp.notes !== "Note mise à jour avec ventilation" || checkOp.amount !== -75.00) {
    throw new Error("getPendingOperations did not return modified fields");
  }
  if (!Array.isArray(checkOp.splits) || checkOp.splits.length !== 2) {
    throw new Error("getPendingOperations did not return modified splits");
  }
  console.log("✓ Modify Pending Operation (Category, Notes, Splits) OK");
  console.log("✓ Pending Operations & Manual Entry OK");

  console.log("Testing BudgetApi.importPendingCB & mergePendingOperation...");
  const importSummary = await BudgetApi.importPendingCB(
    [["15/01/2026", "CB TEST RESTAURANT", "-25.00"]],
    ["date", "label", "amount"],
    { dateFormat: "DD/MM/YYYY", usePurchaseDate: false }
  );
  if (!importSummary || importSummary.imported !== 1) {
    throw new Error("Failed to import pending operation");
  }
  const pendingAfterImport = await BudgetApi.getPendingOperations();
  if (!pendingAfterImport.pendingOperations.some(op => op.label.includes("CB TEST RESTAURANT"))) {
    throw new Error("Imported pending operation not found in getPendingOperations result");
  }

  // Test merging manual op with imported bank op (must stay with status "pending")
  const importedOp = pendingAfterImport.pendingOperations.find(op => op.label.includes("CB TEST RESTAURANT"));
  await BudgetApi.mergePendingOperation(savedManual.id, importedOp);
  const pendingAfterMerge = await BudgetApi.getPendingOperations();
  const mergedOp = pendingAfterMerge.pendingOperations.find(op => op.id === savedManual.id);
  if (!mergedOp) {
    throw new Error("Merged operation not found by manual ID");
  }
  if (mergedOp.status !== "pending") {
    throw new Error("Expected merged operation status to be 'pending', got: " + mergedOp.status);
  }
  if (mergedOp.linkedTxId != null || mergedOp.clearedDate != null) {
    throw new Error("Merged operation should not have linkedTxId or clearedDate set before account debit");
  }
  console.log("✓ Import Pending CB & Merge (status pending) OK");

  console.log("Testing BudgetApi budgetLineId assignment, propagation, and Analyse integration...");
  // 1. Enregistrer une charge
  await BudgetApi.addTresorerieLigne("charges", { id: "chg_test_loyer", label: "Loyer Test", monthly: 800, kind: "charge" });
  
  // 2. Créer une pending avec budgetLineId
  const pendingOpWithBudget = await BudgetApi.savePendingOperation({
    date: "2026-06-05",
    type: "cheque",
    refNumber: "CHQ-999",
    label: "Chèque Loyer Juin",
    amount: -800.00,
    categoryId: "cat_logement",
    budgetLineId: "chg_test_loyer"
  });
  if (pendingOpWithBudget.budgetLineId !== "chg_test_loyer") {
    throw new Error("Expected budgetLineId 'chg_test_loyer' on saved pending op");
  }

  // 3. Link pending operation to a bank transaction -> should auto-propagate to matchings
  await BudgetApi.linkPendingOperation(pendingOpWithBudget.id, "tx_bank_loyer_123", "2026-06-10");
  const pointageAfterLink = await BudgetApi.getPointage();
  const juneMatching = (pointageAfterLink.matchings || []).find(m => m.month === "2026-06");
  if (!juneMatching) {
    throw new Error("Expected matchings entry for 2026-06 after linkPendingOperation with budgetLineId");
  }
  const loyerLink = (juneMatching.links || []).find(l => l.budgetLineId === "chg_test_loyer");
  if (!loyerLink || !loyerLink.txIds.includes("tx_bank_loyer_123")) {
    throw new Error("Expected automatic propagation of tx_bank_loyer_123 into matchings for chg_test_loyer");
  }
  console.log("✓ Budget line assignment & Auto-propagation to Pointage Matchings OK");

  console.log("Testing BudgetApi.getBudgetFull & importJSON & resetData...");
  const fullBudget = await BudgetApi.getBudgetFull();
  if (!fullBudget || !fullBudget.settings) {
    throw new Error("Invalid full budget data");
  }
  await BudgetApi.importJSON(fullBudget);
  const resetBudget = await BudgetApi.resetData();
  if (!resetBudget || !resetBudget.settings) {
    throw new Error("Invalid reset budget data");
  }
  console.log("✓ Full budget export/import/reset OK");

  console.log("\nALL API TESTS PASSED SUCCESSFULLY! 🎉");
})().catch(err => {
  console.error("TEST FAILED:", err);
  process.exit(1);
});
