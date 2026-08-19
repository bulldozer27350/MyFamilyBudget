/**
 * Service métier — Vue d'ensemble, Trésorerie & Patrimoine
 *
 * Seule couche autorisée à accéder au stockage (localStorage via BudgetStore) et à
 * appliquer les règles métier des fonctionnalités migrées. Les composants React ne
 * doivent jamais l'utiliser directement : ils passent par la façade api.js.
 */
(function (exports) {
  'use strict';

  function deps() {
    return typeof window !== 'undefined' ? window.BudgetApp || exports : exports;
  }

  /* Projection du patrimoine financier mobilisable à l'année de retraite (3 scénarios) */
  function computeFinancialOnlyPatrimoine(data, patrimoine, years, retireYear, deflator) {
    const idx = years.indexOf(retireYear);
    const zero = { pess: 0, corr: 0, opti: 0 };
    if (idx === -1) return zero;
    const excludedLabels = new Set((data?.placements || []).filter(p => p.excludedFromRetirement).map(p => p.label));
    const nominal = (patrimoine?.perPlacement || []).reduce((acc, pp) => {
      if (excludedLabels.has(pp.label)) return acc;
      const row = pp.rows[idx] || zero;
      return {
        pess: acc.pess + row.pess,
        corr: acc.corr + row.corr,
        opti: acc.opti + row.opti
      };
    }, zero);
    return {
      pess: nominal.pess * deflator,
      corr: nominal.corr * deflator,
      opti: nominal.opti * deflator
    };
  }

  /* Valeur revalorisée de l'immobilier à l'année de retraite */
  function computeRealEstateAtRetire(data, retireYear, deflator) {
    const currentYear = new Date().getFullYear();
    return (data?.realEstate || []).reduce((s, r) => {
      const elapsed = retireYear - (Number(r.valuationYear) || currentYear);
      return s + (Number(r.currentValue) || 0) * Math.pow(1 + (Number(r.annualGrowthRate) || 0), Math.max(0, elapsed)) * deflator;
    }, 0);
  }

  /* Rente mensuelle issue de la règle des 4 % */
  function fourPercentRule(patrimoine) {
    return {
      pess: patrimoine.pess * 0.04 / 12,
      corr: patrimoine.corr * 0.04 / 12,
      opti: patrimoine.opti * 0.04 / 12
    };
  }

  /**
   * Construit le modèle de lecture complet de la Vue d'ensemble à partir des données stockées.
   */
  function buildOverview(options) {
    const { useConstantEuros = false } = options || {};
    const {
      BudgetStore,
      computeFinancialProjections,
      computePivotBalance,
      computeRetirementProjection
    } = deps();

    const data = BudgetStore.getData();
    const projections = computeFinancialProjections(data, useConstantEuros);
    const { years, cashflow, patrimoine } = projections;

    const retireYear = (Number(data?.settings?.birthYear) || 0) + (Number(data?.settings?.retireAge) || 0);
    const inflationRate = Number(data?.settings?.inflationRate) || 0;
    const retireDeflator = useConstantEuros ? Math.pow(1 / (1 + inflationRate), retireYear - (years[0] || retireYear)) : 1;

    const financialOnlyPatrimoine = computeFinancialOnlyPatrimoine(data, patrimoine, years, retireYear, retireDeflator);
    const realEstateAtRetire = computeRealEstateAtRetire(data, retireYear, retireDeflator);
    const retirePatrimoine = {
      pess: financialOnlyPatrimoine.pess + realEstateAtRetire,
      corr: financialOnlyPatrimoine.corr + realEstateAtRetire,
      opti: financialOnlyPatrimoine.opti + realEstateAtRetire
    };

    const retireYearData = (cashflow || []).find(c => c.year === retireYear);
    const retireCharges = retireYearData ? retireYearData.charges / 12 * retireDeflator : 0;
    const totalPensions = (data?.retirement?.people || []).reduce((s, person) => {
      const proj = computeRetirementProjection(data, person, retireYear);
      return s + (proj.pensionTotaleMensuelle || 0);
    }, 0) * retireDeflator;

    return {
      data,
      years,
      cashflow,
      patrimoine,
      useConstantEuros,
      retireYear,
      pivotBalance: computePivotBalance(data),
      patrimoineActuel: (data?.placements || []).reduce((s, p) => s + (Number(p.balance) || 0), 0),
      fluxNetActuel: cashflow[0]?.net || 0,
      retireCharges,
      totalPensions,
      retirePatrimoine,
      fireRente: fourPercentRule(retirePatrimoine),
      financialOnlyRente: fourPercentRule(financialOnlyPatrimoine)
    };
  }

  /* Notifie l'appelant lorsque les données persistées changent (autre onglet, import, saisie) */
  function subscribeOverview(listener) {
    return deps().BudgetStore.subscribe(listener);
  }

  exports.OverviewService = {
    buildOverview,
    subscribeOverview
  };

  const TRESORERIE_LISTS = ["incomes", "charges", "oneoff", "variableIncomes", "variableOverrides"];

  function buildTresorerieSuggestions(data) {
    const {
      computeRealAverages,
      chargeMonthlyForYear,
      incomeMonthlyForYear
    } = deps();
    const realAverages = computeRealAverages(data);
    const inflationRate = Number(data?.settings?.inflationRate) || 0.02;
    const currentYear = new Date().getFullYear();
    const lines = [];
    const addLine = (row, kind) => {
      const avg = realAverages[row.id];
      if (!avg || avg.avg3m === null) return;
      const budgeted = kind === "charge" ? chargeMonthlyForYear(row, currentYear, inflationRate) : kind === "revenu" ? incomeMonthlyForYear(row, currentYear) : Number(row.monthly) || 0;
      if (budgeted <= 0) return;
      const ecart = avg.avg3m - budgeted;
      const ecartPct = ecart / budgeted * 100;
      if (Math.abs(ecart) >= 10 || Math.abs(ecartPct) >= 5) {
        lines.push({
          id: row.id,
          label: kind === "placement" ? `Épargne : ${row.label}` : row.label,
          kind,
          budgeted,
          avg3m: avg.avg3m,
          avg12m: avg.avg12m,
          ecart,
          ecartPct,
          months: avg.months,
          suggested: Math.round(avg.avg3m * 100) / 100
        });
      }
    };
    (data?.charges || []).forEach(c => addLine(c, "charge"));
    (data?.incomes || []).forEach(i => addLine(i, "revenu"));
    (data?.placements || []).forEach(p => addLine(p, "placement"));
    return lines.sort((a, b) => Math.abs(b.ecart) - Math.abs(a.ecart));
  }

  function buildCategoryOptions(data) {
    const sortedCategories = [...(data?.bankImport?.categories || [])].sort((a, b) => (a.label || "").localeCompare(b.label || "", "fr", {
      sensitivity: "base"
    }));
    return [{
      value: "",
      label: "— Non liée —"
    }, ...sortedCategories.map(c => ({
      value: c.id,
      label: c.label
    }))];
  }

  function newTresorerieRow(listKey, data, retireYear, years) {
    const uid = deps().uid;
    if (listKey === "incomes") {
      return {
        id: uid(),
        label: "Nouveau revenu",
        monthly: 0,
        start: "2026-01-01",
        end: String(retireYear) + "-12-31",
        growthRate: 0,
        categoryId: "",
        notes: ""
      };
    }
    if (listKey === "charges") {
      return {
        id: uid(),
        label: "Nouvelle charge",
        monthly: 0,
        start: "2026-01-01",
        end: String(retireYear) + "-12-31",
        categoryId: "",
        notes: ""
      };
    }
    if (listKey === "oneoff") {
      return {
        id: uid(),
        label: "Nouvelle dépense",
        date: "2026-01-01",
        amount: 0,
        notes: ""
      };
    }
    if (listKey === "variableIncomes") {
      return {
        id: uid(),
        label: "Nouvelle prime",
        type: "Prime",
        refIncomeLabel: data?.incomes?.[0]?.label || "",
        rate: 0.05,
        startYear: years?.[0] || 2026,
        endYear: retireYear,
        taxable: "Oui",
        notes: ""
      };
    }
    if (listKey === "variableOverrides") {
      return {
        id: uid(),
        label: data?.variableIncomes?.[0]?.label || "",
        year: new Date().getFullYear(),
        amount: 0,
        taxable: "",
        notes: ""
      };
    }
    throw new Error("Liste Trésorerie inconnue : " + listKey);
  }

  /**
   * Construit le modèle de lecture de l'onglet Trésorerie à partir des données stockées.
   */
  function buildTresorerie(options) {
    const { useConstantEuros = false } = options || {};
    const {
      BudgetStore,
      computeFinancialProjections
    } = deps();

    const data = BudgetStore.getData();
    const projections = computeFinancialProjections(data, useConstantEuros);
    const retireYear = (Number(data?.settings?.birthYear) || 0) + (Number(data?.settings?.retireAge) || 0);

    return {
      incomes: data?.incomes || [],
      charges: data?.charges || [],
      oneoff: data?.oneoff || [],
      variableIncomes: data?.variableIncomes || [],
      variableOverrides: data?.variableOverrides || [],
      incomeLabels: (data?.incomes || []).map(i => i.label),
      variableIncomeLabels: (data?.variableIncomes || []).map(v => v.label),
      categoryOptions: buildCategoryOptions(data),
      suggestions: buildTresorerieSuggestions(data),
      retireYear,
      years: projections.years,
      cashflow: projections.cashflow,
      variablePreview: projections.variablePreview,
      previewYears: projections.previewYears
    };
  }

  function updateTresorerieLigne(listKey, id, field, value) {
    if (!TRESORERIE_LISTS.includes(listKey) && listKey !== "placements") {
      throw new Error("Liste Trésorerie inconnue : " + listKey);
    }
    deps().BudgetStore.setCell(listKey)(id, field, value);
  }

  function addTresorerieLigne(listKey, options) {
    if (!TRESORERIE_LISTS.includes(listKey)) {
      throw new Error("Liste Trésorerie inconnue : " + listKey);
    }
    const model = buildTresorerie(options);
    const data = deps().BudgetStore.getData();
    deps().BudgetStore.addRow(listKey, () => newTresorerieRow(listKey, data, model.retireYear, model.years));
  }

  function removeTresorerieLigne(listKey, id) {
    if (!TRESORERIE_LISTS.includes(listKey)) {
      throw new Error("Liste Trésorerie inconnue : " + listKey);
    }
    deps().BudgetStore.removeRow(listKey)(id);
  }

  function applyTresorerieAjustement(lineId, kind, newMonthly) {
    const listKey = kind === "charge" ? "charges" : kind === "revenu" ? "incomes" : "placements";
    updateTresorerieLigne(listKey, lineId, "monthly", String(newMonthly));
  }

  function subscribeTresorerie(listener) {
    return deps().BudgetStore.subscribe(listener);
  }

  exports.TresorerieService = {
    buildTresorerie,
    updateTresorerieLigne,
    addTresorerieLigne,
    removeTresorerieLigne,
    applyTresorerieAjustement,
    subscribeTresorerie
  };

  const PATRIMOINE_LISTS = ["placements", "transfers", "loans", "realEstate"];

  function newPatrimoineRow(listKey, data) {
    const uid = deps().uid;
    if (listKey === "placements") {
      return {
        id: uid(),
        label: "Nouveau placement",
        category: (data?.assetCategories || [])[0]?.name || "",
        balance: 0,
        balanceDate: new Date().toISOString().slice(0, 10),
        monthly: 0,
        monthlyFrom: "",
        monthlyUntil: "",
        ratePess: 0.01,
        rateCorr: 0.02,
        rateOpti: 0.03,
        sweepPriority: "",
        sweepCap: "",
        pauseTriggerBalance: "",
        pausePriority: "",
        notes: ""
      };
    }
    if (listKey === "transfers") {
      return {
        id: uid(),
        placement: data?.placements?.[0]?.label || "",
        date: "2026-01-01",
        amount: 0,
        notes: ""
      };
    }
    if (listKey === "loans") {
      return {
        id: uid(),
        label: "Nouveau crédit",
        crd: 0,
        rate: 0,
        monthly: 0,
        insurance: 0,
        startDate: "2026-01-01",
        endDate: "2046-01-01"
      };
    }
    if (listKey === "realEstate") {
      return {
        id: uid(),
        label: "Nouveau bien",
        type: "Résidence principale",
        currentValue: 0,
        valuationYear: new Date().getFullYear(),
        annualGrowthRate: 0.01,
        notes: ""
      };
    }
    throw new Error("Liste Patrimoine inconnue : " + listKey);
  }

  /**
   * Construit le modèle de lecture de l'onglet Patrimoine à partir des données stockées.
   */
  function buildPatrimoine(options) {
    const { useConstantEuros = false } = options || {};
    const {
      BudgetStore,
      computeFinancialProjections
    } = deps();

    const data = BudgetStore.getData();
    const projections = computeFinancialProjections(data, useConstantEuros);

    return {
      placements: data?.placements || [],
      transfers: data?.transfers || [],
      loans: data?.loans || [],
      realEstate: data?.realEstate || [],
      assetCategories: data?.assetCategories || [],
      bankCategories: data?.bankImport?.categories || [],
      patrimoine: projections.patrimoine
    };
  }

  function createPatrimoineLigne(listKey) {
    if (!PATRIMOINE_LISTS.includes(listKey)) {
      throw new Error("Liste Patrimoine inconnue : " + listKey);
    }
    return newPatrimoineRow(listKey, deps().BudgetStore.getData());
  }

  function updatePatrimoineLigne(listKey, id, field, value) {
    if (!PATRIMOINE_LISTS.includes(listKey)) {
      throw new Error("Liste Patrimoine inconnue : " + listKey);
    }
    deps().BudgetStore.setCell(listKey)(id, field, value);
  }

  function addPatrimoineLigne(listKey, row) {
    if (!PATRIMOINE_LISTS.includes(listKey)) {
      throw new Error("Liste Patrimoine inconnue : " + listKey);
    }
    const data = deps().BudgetStore.getData();
    const factory = row ? () => row : () => newPatrimoineRow(listKey, data);
    deps().BudgetStore.addRow(listKey, factory);
  }

  function removePatrimoineLigne(listKey, id) {
    if (!PATRIMOINE_LISTS.includes(listKey)) {
      throw new Error("Liste Patrimoine inconnue : " + listKey);
    }
    deps().BudgetStore.removeRow(listKey)(id);
  }

  function subscribePatrimoine(listener) {
    return deps().BudgetStore.subscribe(listener);
  }

  exports.PatrimoineService = {
    buildPatrimoine,
    createPatrimoineLigne,
    updatePatrimoineLigne,
    addPatrimoineLigne,
    removePatrimoineLigne,
    subscribePatrimoine
  };
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);
