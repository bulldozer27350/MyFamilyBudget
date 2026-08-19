/**
 * Modèles de données, valeurs par défaut et normalisation
 */
(function (exports) {
  'use strict';

  const uid = exports.uid || (() => Math.random().toString(36).slice(2, 10));
  const STORAGE_KEY = "budget_familial_data_v1";
  const DEFAULT_DATA = {
    settings: {
      birthYear: 1985,
      retireAge: 64,
      simulateUntilAge: 90,
      startBalance: 0,
      childExitAge: 21,
      taxAbattement: 0.10,
      inflationRate: 0.02,
      pivotDate: "",
      pivotMode: "manual"
    },
    retirement: {
      pass2026: 47100,
      passGrowthRate: 0.015,
      agircPointValue: 1.4386,
      agircPointDateGlobal: "2025-11-01",
      agircPointGrowthRate: 0.01,
      people: []
    },
    incomes: [],
    savings: [],
    charges: [],
    oneoff: [],
    transfers: [],
    variableIncomes: [],
    variableOverrides: [],
    taxChildren: [],
    taxBrackets: [],
    taxRateOverrides: [],
    taxActualOverrides: [],
    placements: [],
    loans: [],
    realEstate: [],
    assetCategories: [],
    bankImport: {
      columnMapping: {
        delimiter: ";",
        hasHeader: true,
        dateCol: 0,
        labelCol: 1,
        typeCol: -1,
        amountCol: 2,
        dateFormat: "DD/MM/YYYY"
      },
      categories: [],
      rules: [],
      transactions: [],
      matchings: [],
      pendingOperations: []
    }
  };

  /**
   * Convertit l'ancienne structure (retirement.marco / retirement.nathy) en
   * une liste générique de personnes.
   */
  function migrateRetirementPeople(retirement) {
    if (!retirement) return [];
    if (Array.isArray(retirement.people)) {
      return retirement.people.map(p => ({
        ...p,
        salaryHistory: Array.isArray(p.salaryHistory) ? p.salaryHistory : []
      }));
    }
    const legacy = [];
    if (retirement.marco) legacy.push({
      id: uid(),
      name: "Marco",
      incomeLabel: "Salaire Marco",
      birthYear: "",
      salaryHistory: [],
      ...retirement.marco
    });
    if (retirement.nathy) legacy.push({
      id: uid(),
      name: "Nathy",
      incomeLabel: "Salaire Nath",
      birthYear: "",
      salaryHistory: [],
      ...retirement.nathy
    });
    return legacy;
  }

  /**
   * Normalise les données brutes lues depuis le JSON ou localStorage
   */
  function normalizeData(raw) {
    const base = raw && typeof raw === "object" ? raw : {};
    return {
      settings: {
        ...DEFAULT_DATA.settings,
        ...(base.settings || {})
      },
      retirement: {
        ...DEFAULT_DATA.retirement,
        ...(base.retirement || {}),
        people: migrateRetirementPeople(base.retirement)
      },
      incomes: Array.isArray(base.incomes) ? base.incomes : DEFAULT_DATA.incomes,
      savings: Array.isArray(base.savings) ? base.savings : DEFAULT_DATA.savings,
      charges: Array.isArray(base.charges) ? base.charges : DEFAULT_DATA.charges,
      oneoff: Array.isArray(base.oneoff) ? base.oneoff : DEFAULT_DATA.oneoff,
      transfers: Array.isArray(base.transfers) ? base.transfers : DEFAULT_DATA.transfers,
      placements: Array.isArray(base.placements) ? base.placements : DEFAULT_DATA.placements,
      loans: Array.isArray(base.loans) ? base.loans : DEFAULT_DATA.loans,
      variableIncomes: Array.isArray(base.variableIncomes) ? base.variableIncomes : DEFAULT_DATA.variableIncomes,
      variableOverrides: Array.isArray(base.variableOverrides) ? base.variableOverrides : [],
      taxChildren: Array.isArray(base.taxChildren) ? base.taxChildren : DEFAULT_DATA.taxChildren,
      taxBrackets: Array.isArray(base.taxBrackets) ? base.taxBrackets : DEFAULT_DATA.taxBrackets,
      taxRateOverrides: Array.isArray(base.taxRateOverrides) ? base.taxRateOverrides : [],
      taxActualOverrides: Array.isArray(base.taxActualOverrides) ? base.taxActualOverrides : [],
      realEstate: Array.isArray(base.realEstate) ? base.realEstate : [],
      assetCategories: Array.isArray(base.assetCategories) ? base.assetCategories : DEFAULT_DATA.assetCategories,
      bankImport: {
        columnMapping: {
          ...DEFAULT_DATA.bankImport.columnMapping,
          ...((base.bankImport || {}).columnMapping || {})
        },
        categories: Array.isArray((base.bankImport || {}).categories) ? base.bankImport.categories : [],
        rules: Array.isArray((base.bankImport || {}).rules) ? base.bankImport.rules : [],
        transactions: Array.isArray((base.bankImport || {}).transactions) ? base.bankImport.transactions : [],
        matchings: Array.isArray((base.bankImport || {}).matchings) ? base.bankImport.matchings : [],
        pendingOperations: Array.isArray((base.bankImport || {}).pendingOperations) ? base.bankImport.pendingOperations : []
      }
    };
  }
  exports.STORAGE_KEY = STORAGE_KEY;
  exports.DEFAULT_DATA = DEFAULT_DATA;
  exports.migrateRetirementPeople = migrateRetirementPeople;
  exports.normalizeData = normalizeData;
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);