/**
 * Service métier — Vue d'ensemble, Trésorerie, Patrimoine et Retraite
 *
 * Seule couche autorisée à accéder au stockage (localStorage via BudgetStore) et à
 * appliquer les règles métier des fonctionnalités migrées. Les composants React ne
 * doivent jamais l'utiliser directement : ils passent par la façade api.js.
 */
(function (exports) {
  'use strict';

  const STORAGE_KEY = "budget_familial_data_v1";

  // Constantes pour le calcul de retraite
  const TRIMESTRES_REQUIS = 172;
  const AGE_TAUX_PLEIN_AUTO = 67;
  const DECOTE_PAR_TRIMESTRE = 0.00625;
  const SURCOTE_PAR_TRIMESTRE = 0.0125;
  const TAUX_PLEIN = 0.50;
  const TAUX_MINORE_PLANCHER = 0.375;
  const MAJORATION_3_ENFANTS = 0.10;

function deps() {
    return typeof window !== 'undefined' ? window.BudgetApp || exports : exports;
  }

  /**
   * Compare la catégorisation (categoryId simple, ou ensemble des categoryId des splits) de deux
   * entités (une opération en attente et une transaction bancaire, dans n'importe quel ordre).
   * Deux entités sans aucune catégorisation (ni categoryId, ni splits) sont considérées identiques
   * (rien à réconcilier). Une entité avec des splits n'est JAMAIS considérée identique à une entité
   * en catégorie simple, même si celle-ci est vide : toute différence, y compris "un seul des deux
   * côtés est catégorisé", doit être arbitrée par l'opérateur plutôt que devinée par l'application.
   * Les montants des splits sont ignorés dans la comparaison (le montant de l'opération en attente
   * n'est qu'une estimation du montant bancaire réel).
   */
  function categorizationsMatch(entityA, entityB) {
    const hasSplitsA = Array.isArray(entityA?.splits) && entityA.splits.length > 0;
    const hasSplitsB = Array.isArray(entityB?.splits) && entityB.splits.length > 0;
    if (hasSplitsA !== hasSplitsB) return false;
    if (hasSplitsA) {
      const idsA = Array.from(new Set(entityA.splits.map(s => s.categoryId).filter(Boolean))).sort();
      const idsB = Array.from(new Set(entityB.splits.map(s => s.categoryId).filter(Boolean))).sort();
      return idsA.length === idsB.length && idsA.every((id, i) => id === idsB[i]);
    }
    return (entityA?.categoryId || "") === (entityB?.categoryId || "");
  }

  /**
   * Réajuste une liste de splits pour que leur somme corresponde exactement à targetAmount (le
   * montant réel de la transaction bancaire), en conservant le poids relatif de chaque split. L'écart
   * d'arrondi est absorbé par le dernier split.
   */
  function rescaleSplitsToAmount(splits, targetAmount) {
    if (!Array.isArray(splits) || splits.length === 0) return [];
    const { uid } = deps();
    const sum = splits.reduce((s, sp) => s + (Number(sp.amount) || 0), 0);
    let out;
    if (Math.abs(sum) > 0.001 && Math.abs(targetAmount - sum) > 0.001) {
      const ratio = targetAmount / sum;
      out = splits.map(sp => ({
        ...sp,
        id: sp.id || (uid ? uid() : `split_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`),
        amount: Math.round((Number(sp.amount) || 0) * ratio * 100) / 100
      }));
      const newSum = out.reduce((s, sp) => s + sp.amount, 0);
      const diff = Math.round((targetAmount - newSum) * 100) / 100;
      if (diff !== 0 && out.length > 0) {
        out[out.length - 1] = { ...out[out.length - 1], amount: Math.round((out[out.length - 1].amount + diff) * 100) / 100 };
      }
    } else {
      out = splits.map(sp => ({
        ...sp,
        id: sp.id || (uid ? uid() : `split_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`),
        amount: Number(sp.amount) || 0
      }));
    }
    return out;
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
      fluxNetActuel: ((cashflow || []).find(c => c.year === new Date().getFullYear()) || cashflow[0] || {}).net || 0,
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
   * Récupère les données complètes depuis le BudgetStore ou localStorage
   * @returns {Object} Données complètes de l'application
   */
  function loadFullData() {
    if (deps().BudgetStore && typeof deps().BudgetStore.getData === 'function') {
      return deps().BudgetStore.getData();
    }
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? JSON.parse(raw) : (deps().DEFAULT_DATA || {});
    } catch (err) {
      console.error("Erreur de chargement localStorage :", err);
      return deps().DEFAULT_DATA || {};
    }
  }

  /**
   * Sauvegarde les données complètes via BudgetStore ou dans le localStorage
   * @param {Object} data - Données complètes à sauvegarder
   */
  function saveFullData(data) {
    if (deps().BudgetStore && typeof deps().BudgetStore.setData === 'function') {
      deps().BudgetStore.setData(data);
      return;
    }
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
    } catch (err) {
      console.error("Erreur de sauvegarde localStorage :", err);
    }
  }

  /**
   * Calcule le PASS pour une année donnée
   * @param {Object} data - Données complètes
   * @param {number} year - Année
   * @returns {number} Valeur du PASS
   */
  function passForYear(data, year) {
    const base = Number(data.retirement?.pass2026) || 47100;
    const growth = Number(data.retirement?.passGrowthRate) ?? 0.015;
    return base * Math.pow(1 + growth, year - 2026);
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
   * Calcule la valeur du point Agirc-Arrco pour une année donnée
   * @param {Object} data - Données complètes
   * @param {number} year - Année
   * @returns {number} Valeur du point
   */
  function agircPointValueForYear(data, year) {
    const base = Number(data.retirement?.agircPointValue) || 1.4386;
    const baseYear = yearOf(data.retirement?.agircPointDateGlobal) || 2025;
    const growth = Number(data.retirement?.agircPointGrowthRate) ?? 0.01;
    return base * Math.pow(1 + growth, Math.max(0, year - baseYear));
  }

  /**
   * Calcule le salaire annuel projeté pour une personne et une année
   * @param {Object} data - Données complètes
   * @param {Object} person - Personne
   * @param {number} year - Année
   * @returns {number} Salaire annuel projeté
   */
  function projectedAnnualSalary(data, person, year) {
    if (!person.incomeLabel) return 0;
    const row = (data.incomes || []).find(r => r.label === person.incomeLabel);
    if (!row) return 0;
    return incomeAnnualForYear(row, year);
  }

  /**
   * Calcule le nombre d'enfants
   * @param {Object} data - Données complètes
   * @returns {number} Nombre d'enfants
   */
  function nbEnfants(data) {
    return (data.taxChildren || []).length;
  }

  /**
   * Fonction utilitaire pour extraire l'année d'une date
   * @param {string} dateStr - Date au format ISO
   * @returns {number|null} Année ou null
   */
  function yearOf(dateStr) {
    if (!dateStr) return null;
    const date = new Date(dateStr);
    if (isNaN(date.getTime())) return null;
    return date.getFullYear();
  }

  /**
   * Calcule le revenu annuel pour une année donnée
   * @param {Object} row - Ligne de revenu
   * @param {number} year - Année
   * @returns {number} Revenu annuel
   */
  function incomeAnnualForYear(row, year) {
    const startYear = yearOf(row.start) ?? year;
    const growth = Number(row.growthRate) || 0;
    const yearsElapsed = Math.max(0, year - startYear);
    const effectiveMonthly = (Number(row.monthly) || 0) * Math.pow(1 + growth, yearsElapsed);
    return effectiveMonthly * monthsActiveInYear(row.start, row.end, year);
  }

  /**
   * Calcule le nombre de mois actifs dans une année
   * @param {string} startISO - Date de début ISO
   * @param {string} endISO - Date de fin ISO
   * @param {number} year - Année
   * @returns {number} Nombre de mois actifs
   */
  function monthsActiveInYear(startISO, endISO, year) {
    if (!startISO || !endISO) return 0;
    const start = new Date(startISO);
    const end = new Date(endISO);
    const yStart = new Date(year, 0, 1);
    const yEnd = new Date(year, 11, 31);
    const s = start > yStart ? start : yStart;
    const e = end < yEnd ? end : yEnd;
    if (e < s) return 0;
    return (e.getFullYear() - s.getFullYear()) * 12 + (e.getMonth() - s.getMonth()) + 1;
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

  /**
   * Calcule la projection de retraite pour une personne
   * @param {Object} data - Données complètes
   * @param {Object} person - Personne
   * @param {number} retireYear - Année de retraite
   * @returns {Object} Projection de retraite
   */
  function computeRetirementProjection(data, person, retireYear) {
    const birthYear = Number(person.birthYear) || Number(data.settings.birthYear) || 1985;
    const trimestresValides = Number(person.trimestresValides) || 0;
    const trimestresDateYear = yearOf(person.trimestresDate) || new Date().getFullYear() - 1;
    let trimestresFuturs = 0;
    for (let y = trimestresDateYear + 1; y <= retireYear; y++) {
      if (projectedAnnualSalary(data, person, y) > 0) trimestresFuturs += 4;
    }
    const trimestresEstimesDepart = trimestresValides + trimestresFuturs;
    const ageDepart = retireYear - birthYear;
    const trimestresJusquTauxPleinAuto = Math.max(0, (AGE_TAUX_PLEIN_AUTO - ageDepart) * 4);
    let tauxAppliqué = TAUX_PLEIN,
      decote = 0,
      surcote = 0;
    if (trimestresEstimesDepart < TRIMESTRES_REQUIS) {
      const manquants = TRIMESTRES_REQUIS - trimestresEstimesDepart;
      const trimestresDecote = Math.min(manquants, trimestresJusquTauxPleinAuto);
      decote = trimestresDecote * DECOTE_PAR_TRIMESTRE;
      tauxAppliqué = Math.max(TAUX_PLEIN - decote, TAUX_MINORE_PLANCHER);
    } else if (trimestresEstimesDepart > TRIMESTRES_REQUIS) {
      surcote = (trimestresEstimesDepart - TRIMESTRES_REQUIS) * SURCOTE_PAR_TRIMESTRE;
      tauxAppliqué = TAUX_PLEIN + surcote;
    }
    const historyYears = (person.salaryHistory || []).map(h => ({
      year: Number(h.year),
      salary: Number(h.salary) || 0
    })).filter(h => h.salary > 0);
    const futureYears = [];
    for (let y = trimestresDateYear + 1; y <= retireYear - 1; y++) {
      const s = projectedAnnualSalary(data, person, y);
      if (s > 0) futureYears.push({
        year: y,
        salary: s
      });
    }
    const byYear = new Map();
    for (const h of historyYears) byYear.set(h.year, h.salary);
    for (const f of futureYears) if (!byYear.has(f.year)) byYear.set(f.year, f.salary);
    const allEntries = Array.from(byYear.entries()).map(([year, salary]) => ({
      year,
      salary
    }));
    allEntries.sort((a, b) => b.year - a.year);
    const last25 = allEntries.slice(0, 25);
    const cappedSalaries = last25.map(h => Math.min(h.salary, passForYear(data, h.year)));
    const SAM = cappedSalaries.length ? cappedSalaries.reduce((s, v) => s + v, 0) / cappedSalaries.length : 0;
    const majoration = nbEnfants(data) >= 3 ? 1 + MAJORATION_3_ENFANTS : 1;
    const ratioTrimestres = Math.min(trimestresEstimesDepart, TRIMESTRES_REQUIS) / TRIMESTRES_REQUIS;
    const pensionBaseAnnuelle = SAM * tauxAppliqué * ratioTrimestres * majoration;
    const pointsActuels = Number(person.agircPoints) || 0;
    const ratioPointsParEuro = Number(person.ratioPointsParEuro) || 0.0051;
    const pointsFuturs = futureYears.reduce((s, h) => s + h.salary * ratioPointsParEuro, 0);
    const pointsEstimes = pointsActuels + pointsFuturs;
    const valeurPointDepart = agircPointValueForYear(data, retireYear);
    const pensionComplementaireAnnuelle = pointsEstimes * valeurPointDepart * majoration;
    return {
      ageDepart,
      trimestresValides,
      trimestresEstimesDepart,
      trimestresRequis: TRIMESTRES_REQUIS,
      manqueTauxPlein: trimestresEstimesDepart < TRIMESTRES_REQUIS,
      tauxAppliqué,
      decote,
      surcote,
      SAM,
      majoration,
      pensionBaseAnnuelle,
      pointsEstimes,
      valeurPointDepart,
      pensionComplementaireAnnuelle,
      pensionTotaleAnnuelle: pensionBaseAnnuelle + pensionComplementaireAnnuelle,
      pensionTotaleMensuelle: (pensionBaseAnnuelle + pensionComplementaireAnnuelle) / 12
    };
  }

  /**
   * Récupère les données de retraite avec les projections calculées
   * @returns {Object} Données de retraite
   */
  function getRetraiteDataFromService() {
    const data = loadFullData();
    const retireYear = (Number(data.settings?.birthYear) || 1985) + (Number(data.settings?.retireAge) || 64);
    
    // Calculer les projections pour chaque personne
    const peopleWithProjections = (data.retirement?.people || []).map(person => ({
      ...person,
      projection: computeRetirementProjection(data, person, retireYear)
    }));

    return {
      retirement: {
        ...data.retirement,
        people: peopleWithProjections
      },
      retireYear,
      incomes: data.incomes || [],
      settings: data.settings || {}
    };
  }

  /**
   * Sauvegarde les données de retraite
   * @param {Object} retirementData - Données de retraite à sauvegarder
   */
  function saveRetraiteDataToService(retirementData) {
    const currentData = loadFullData();
    const updatedData = {
      ...currentData,
      retirement: retirementData
    };
    saveFullData(updatedData);
  }

  /**
   * Construit le modèle de lecture de l'onglet Impôts à partir des données stockées.
   * @returns {Object} modèle de lecture de l'onglet Impôts
   */
  function buildImpots() {
    const {
      computeFinancialProjections
    } = deps();

    const data = loadFullData();
    const projections = computeFinancialProjections(data, false);
    
    return {
      taxChildren: data?.taxChildren || [],
      taxBrackets: data?.taxBrackets || [],
      taxRateOverrides: data?.taxRateOverrides || [],
      taxActualOverrides: data?.taxActualOverrides || [],
      settings: data?.settings || {},
      taxPreview: projections.taxPreview || []
    };
  }

  /**
   * Met à jour une cellule d'une ligne d'Impôts.
   * @param {string} listKey - Clé de la liste (taxChildren, taxBrackets, taxRateOverrides, taxActualOverrides)
   * @param {string} id - ID de la ligne
   * @param {string} field - Champ à modifier
   * @param {*} value - Nouvelle valeur
   */
  function updateImpotsLigne(listKey, id, field, value) {
    const IMPOTS_LISTS = ["taxChildren", "taxBrackets", "taxRateOverrides", "taxActualOverrides"];
    if (!IMPOTS_LISTS.includes(listKey)) {
      throw new Error("Liste Impôts inconnue : " + listKey);
    }
    const currentData = loadFullData();
    const list = currentData[listKey] || [];
    const rowIndex = list.findIndex(row => row.id === id);
    if (rowIndex === -1) {
      throw new Error("Ligne non trouvée : " + id);
    }
    list[rowIndex][field] = value;
    currentData[listKey] = list;
    saveFullData(currentData);
  }

  /**
   * Ajoute une ligne d'Impôts.
   * @param {string} listKey - Clé de la liste
   * @param {Function} rowFactory - Fonction créant la nouvelle ligne
   */
  function addImpotsLigne(listKey, rowFactory) {
    const IMPOTS_LISTS = ["taxChildren", "taxBrackets", "taxRateOverrides", "taxActualOverrides"];
    if (!IMPOTS_LISTS.includes(listKey)) {
      throw new Error("Liste Impôts inconnue : " + listKey);
    }
    const currentData = loadFullData();
    const list = currentData[listKey] || [];
    const newRow = rowFactory();
    list.push(newRow);
    currentData[listKey] = list;
    saveFullData(currentData);
  }

  /**
   * Supprime une ligne d'Impôts.
   * @param {string} listKey - Clé de la liste
   * @param {string} id - ID de la ligne à supprimer
   */
  function removeImpotsLigne(listKey, id) {
    const IMPOTS_LISTS = ["taxChildren", "taxBrackets", "taxRateOverrides", "taxActualOverrides"];
    if (!IMPOTS_LISTS.includes(listKey)) {
      throw new Error("Liste Impôts inconnue : " + listKey);
    }
    const currentData = loadFullData();
    const list = currentData[listKey] || [];
    const filteredList = list.filter(row => row.id !== id);
    currentData[listKey] = filteredList;
    saveFullData(currentData);
  }

  /**
   * Met à jour les settings globaux liés aux impôts.
   * @param {string} field - Champ à modifier
   * @param {*} value - Nouvelle valeur
   */
  function updateImpotsSettings(field, value) {
    const currentData = loadFullData();
    if (!currentData.settings) {
      currentData.settings = {};
    }
    currentData.settings[field] = value;
    saveFullData(currentData);
  }

  /**
   * Réinitialise les tranches d'imposition au barème légal standard.
   * @returns {void}
   */
  function resetDefaultTaxBrackets() {
    const defaultBrackets = [
      { id: "tb_1", upTo: 11294, rate: 0 },
      { id: "tb_2", upTo: 28797, rate: 0.11 },
      { id: "tb_3", upTo: 82341, rate: 0.30 },
      { id: "tb_4", upTo: 177106, rate: 0.41 },
      { id: "tb_5", upTo: "", rate: 0.45 }
    ];
    const currentData = loadFullData();
    currentData.taxBrackets = defaultBrackets;
    saveFullData(currentData);
  }

  /**
   * S'abonne aux changements des données d'Impôts.
   * @returns {Function} fonction de désabonnement
   */
  function subscribeImpots(listener) {
    return deps().BudgetStore.subscribe(listener);
  }

  exports.ImpotsService = {
    buildImpots,
    updateImpotsLigne,
    addImpotsLigne,
    removeImpotsLigne,
    updateImpotsSettings,
    resetDefaultTaxBrackets,
    subscribeImpots
  };

  /**
   * 
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

  /**
   * Construit le modèle de lecture de l'onglet Paramètres à partir des données stockées.
   * @returns {Object} modèle de lecture de l'onglet Paramètres
   */
  function buildSettings() {
    const data = loadFullData();
    const birthYear = Number(data?.settings?.birthYear) || 1985;
    const retireAge = Number(data?.settings?.retireAge) || 64;
    const simulateUntilAge = Number(data?.settings?.simulateUntilAge) || 85;
    const retireYear = birthYear + retireAge;
    const years = [];
    for (let y = retireYear; y <= birthYear + simulateUntilAge; y++) {
      years.push(y);
    }

    return {
      settings: data?.settings || {},
      assetCategories: data?.assetCategories || [],
      retireYear,
      years,
      bankImport: data?.bankImport || {}
    };
  }

  /**
   * Met à jour un champ des settings.
   * @param {string} field - Champ à modifier
   * @param {*} value - Nouvelle valeur
   */
  function updateSettingsField(field, value) {
    const currentData = loadFullData();
    if (!currentData.settings) {
      currentData.settings = {};
    }
    currentData.settings[field] = value;
    saveFullData(currentData);
  }

  /**
   * Met à jour une cellule d'une ligne d'assetCategories.
   * @param {string} id - ID de la ligne
   * @param {string} field - Champ à modifier
   * @param {*} value - Nouvelle valeur
   */
  function updateAssetCategory(id, field, value) {
    const currentData = loadFullData();
    const list = currentData.assetCategories || [];
    const rowIndex = list.findIndex(row => row.id === id);
    if (rowIndex === -1) {
      throw new Error("Catégorie d'actif non trouvée : " + id);
    }
    list[rowIndex][field] = value;
    currentData.assetCategories = list;
    saveFullData(currentData);
  }

  /**
   * Ajoute une nouvelle catégorie d'actif.
   * @param {Object} row - Nouvelle ligne (optionnel)
   */
  function addAssetCategory(row) {
    const currentData = loadFullData();
    const uid = deps().uid;
    const list = currentData.assetCategories || [];
    const newRow = row || {
      id: uid(),
      icon: "📁",
      name: "Nouvelle catégorie",
      bucket: "cash"
    };
    list.push(newRow);
    currentData.assetCategories = list;
    saveFullData(currentData);
  }

  /**
   * Supprime une catégorie d'actif.
   * @param {string} id - ID de la ligne à supprimer
   */
  function removeAssetCategory(id) {
    const currentData = loadFullData();
    const list = currentData.assetCategories || [];
    const filteredList = list.filter(row => row.id !== id);
    currentData.assetCategories = filteredList;
    saveFullData(currentData);
  }

  /**
   * S'abonne aux changements des données de Paramètres.
   * @returns {Function} fonction de désabonnement
   */
  function subscribeSettings(listener) {
    return deps().BudgetStore.subscribe(listener);
  }

  exports.SettingsService = {
    buildSettings,
    updateSettingsField,
    updateAssetCategory,
    addAssetCategory,
    removeAssetCategory,
    subscribeSettings
  };

  /**
   * Construit le modèle de lecture de l'onglet Import Bancaire.
   * @returns {Object} modèle de lecture de l'onglet Import
   */
  function buildBankImport() {
    const data = loadFullData();
    const bankImport = data?.bankImport || {};
    
    return {
      columnMapping: bankImport.columnMapping || {
        delimiter: ";",
        dateFormat: "DD/MM/YYYY",
        hasHeader: true,
        dateCol: null,
        labelCol: null,
        typeCol: null,
        amountCol: null
      },
      categories: bankImport.categories || [],
      rules: bankImport.rules || [],
      transactions: bankImport.transactions || []
    };
  }

  /**
   * Met à jour le mapping de colonnes pour l'import bancaire.
   * @param {Object} newMapping - Nouveau mapping de colonnes
   */
  function updateBankImportMapping(newMapping) {
    const currentData = loadFullData();
    if (!currentData.bankImport) {
      currentData.bankImport = {};
    }
    currentData.bankImport.columnMapping = {
      ...currentData.bankImport.columnMapping,
      ...newMapping
    };
    saveFullData(currentData);
  }

  /**
   * Ajoute une nouvelle catégorie d'import bancaire.
   * @param {Object} category - Nouvelle catégorie
   */
  function addBankImportCategory(category) {
    const currentData = loadFullData();
    if (!currentData.bankImport) {
      currentData.bankImport = {};
    }
    if (!currentData.bankImport.categories) {
      currentData.bankImport.categories = [];
    }
    const uid = deps().uid;
    const newCategory = category || {
      id: uid(),
      label: "Nouvelle catégorie",
      kind: "Dépense",
      compressible: "Non"
    };
    currentData.bankImport.categories.push(newCategory);
    saveFullData(currentData);
  }

  /**
   * Met à jour une catégorie d'import bancaire.
   * @param {string} id - ID de la catégorie
   * @param {string} field - Champ à modifier
   * @param {*} value - Nouvelle valeur
   */
  function updateBankImportCategory(id, field, value) {
    const currentData = loadFullData();
    if (!currentData.bankImport) {
      currentData.bankImport = {};
    }
    if (!currentData.bankImport.categories) {
      currentData.bankImport.categories = [];
    }
    const categories = currentData.bankImport.categories;
    const categoryIndex = categories.findIndex(c => c.id === id);
    if (categoryIndex === -1) {
      throw new Error("Catégorie non trouvée : " + id);
    }
    categories[categoryIndex][field] = value;
    currentData.bankImport.categories = categories;
    saveFullData(currentData);
  }

  /**
   * Supprime une catégorie d'import bancaire.
   * @param {string} id - ID de la catégorie à supprimer
   */
  function removeBankImportCategory(id) {
    const currentData = loadFullData();
    if (!currentData.bankImport) {
      currentData.bankImport = {};
    }
    if (!currentData.bankImport.categories) {
      currentData.bankImport.categories = [];
    }
    currentData.bankImport.categories = currentData.bankImport.categories.filter(c => c.id !== id);
    saveFullData(currentData);
  }

  /**
   * Ajoute une nouvelle règle de catégorisation.
   * @param {Object} rule - Nouvelle règle
   */
  function addBankImportRule(rule) {
    const currentData = loadFullData();
    if (!currentData.bankImport) {
      currentData.bankImport = {};
    }
    if (!currentData.bankImport.rules) {
      currentData.bankImport.rules = [];
    }
    const uid = deps().uid;
    const newRule = rule || {
      id: uid(),
      matchText: "",
      categoryId: currentData.bankImport.categories?.[0]?.id || ""
    };
    currentData.bankImport.rules.push(newRule);
    saveFullData(currentData);
  }

  /**
   * Met à jour une règle de catégorisation.
   * @param {string} id - ID de la règle
   * @param {string} field - Champ à modifier
   * @param {*} value - Nouvelle valeur
   */
  function updateBankImportRule(id, field, value) {
    const currentData = loadFullData();
    if (!currentData.bankImport) {
      currentData.bankImport = {};
    }
    if (!currentData.bankImport.rules) {
      currentData.bankImport.rules = [];
    }
    const rules = currentData.bankImport.rules;
    const ruleIndex = rules.findIndex(r => r.id === id);
    if (ruleIndex === -1) {
      throw new Error("Règle non trouvée : " + id);
    }
    rules[ruleIndex][field] = value;
    currentData.bankImport.rules = rules;
    saveFullData(currentData);
  }

  /**
   * Supprime une règle de catégorisation.
   * @param {string} id - ID de la règle à supprimer
   */
  function removeBankImportRule(id) {
    const currentData = loadFullData();
    if (!currentData.bankImport) {
      currentData.bankImport = {};
    }
    if (!currentData.bankImport.rules) {
      currentData.bankImport.rules = [];
    }
    currentData.bankImport.rules = currentData.bankImport.rules.filter(r => r.id !== id);
    saveFullData(currentData);
  }

  /**
   * Réapplique les règles aux transactions non catégorisées.
   */
  function recalculateBankImportRules() {
    const currentData = loadFullData();
    if (!currentData.bankImport) {
      currentData.bankImport = {};
    }
    const { applyRulesToTransactions } = deps();
    currentData.bankImport.transactions = applyRulesToTransactions(
      currentData.bankImport.transactions || [],
      currentData.bankImport.rules || []
    );
    saveFullData(currentData);
  }

  /**
   * Met à jour la catégorie d'une transaction et optionnellement crée une règle.
   * @param {string} txId - ID de la transaction
   * @param {string} categoryId - ID de la catégorie
   * @param {string} ruleKeyword - Mot-clé pour la règle (optionnel)
   */
  function setBankImportTransactionCategory(txId, categoryId, ruleKeyword) {
    const currentData = loadFullData();
    if (!currentData.bankImport) {
      currentData.bankImport = {};
    }
    if (!currentData.bankImport.transactions) {
      currentData.bankImport.transactions = [];
    }
    if (!currentData.bankImport.rules) {
      currentData.bankImport.rules = [];
    }

    let newRules = currentData.bankImport.rules;
    if (ruleKeyword) {
      const key = ruleKeyword.trim().toUpperCase();
      const existing = currentData.bankImport.rules.find(r => r.matchText.trim().toUpperCase() === key);
      const uid = deps().uid;
      newRules = existing 
        ? currentData.bankImport.rules.map(r => r.matchText.trim().toUpperCase() === key ? { ...r, categoryId } : r)
        : [...currentData.bankImport.rules, { id: uid(), matchText: ruleKeyword.trim(), categoryId }];
    }

    const { applyRulesToTransactions } = deps();
    const updatedTransactions = currentData.bankImport.transactions.map(t => 
      t.id === txId ? { ...t, categoryId } : t
    );
    
    currentData.bankImport.rules = newRules;
    currentData.bankImport.transactions = applyRulesToTransactions(updatedTransactions, newRules);
    saveFullData(currentData);
  }

  /**
   * Met à jour la ventilation (splits) d'une transaction bancaire.
   * @param {string} txId - ID de la transaction
   * @param {Array} splits - Liste des sous-lignes [{ id, categoryId, amount, label }]
   */
  function updateBankTransactionSplits(txId, splits) {
    const currentData = loadFullData();
    if (!currentData.bankImport) {
      currentData.bankImport = {};
    }
    if (!currentData.bankImport.transactions) {
      currentData.bankImport.transactions = [];
    }
    const cleanSplits = Array.isArray(splits) && splits.length > 0 ? splits : null;
    currentData.bankImport.transactions = currentData.bankImport.transactions.map(t => {
      if (t.id === txId) {
        return {
          ...t,
          splits: cleanSplits
        };
      }
      return t;
    });
    saveFullData(currentData);
  }

  /**
   * Force l'import d'une transaction marquée comme doublon.
   * @param {Object} tx - Transaction à importer
   */
  function forceImportBankTransaction(tx) {
    const currentData = loadFullData();
    if (!currentData.bankImport) {
      currentData.bankImport = {};
    }
    if (!currentData.bankImport.transactions) {
      currentData.bankImport.transactions = [];
    }
    if (!currentData.bankImport.rules) {
      currentData.bankImport.rules = [];
    }

    const { applyRulesToTransactions } = deps();
    const categorizedTx = applyRulesToTransactions([tx], currentData.bankImport.rules)[0] || tx;
    currentData.bankImport.transactions.push(categorizedTx);
    saveFullData(currentData);
  }

  /**
   * Importe des transactions depuis un fichier CSV traité.
   * @param {Array} rawRows - Lignes du CSV déjà parsées
   * @param {Object} colRoles - Rôles assignés aux colonnes
   * @param {Object} mapping - Configuration de mapping
   * @returns {Object} Résumé de l'import
   */
  function importBankTransactions(rawRows, colRoles, mapping) {
    const currentData = loadFullData();
    if (!currentData.bankImport) {
      currentData.bankImport = {};
    }
    if (!currentData.bankImport.transactions) {
      currentData.bankImport.transactions = [];
    }
    if (!currentData.bankImport.rules) {
      currentData.bankImport.rules = [];
    }

    const { parseDateWithFormat, parseAmountText, transactionDedupeKey, applyRulesToTransactions, uid } = deps();
    
    const dateCol = colRoles.indexOf("date");
    const labelCol = colRoles.indexOf("label");
    const typeCol = colRoles.indexOf("type");
    const amountCol = colRoles.indexOf("amount");

    if (dateCol === -1 || labelCol === -1 || amountCol === -1) {
      return {
        error: "Il faut au minimum assigner les rôles Date, Libellé et Montant à une colonne."
      };
    }

    const newMapping = {
      ...mapping,
      dateCol,
      labelCol,
      typeCol,
      amountCol
    };

    const existingCounts = {};
    currentData.bankImport.transactions.forEach(t => {
      const k = transactionDedupeKey(t);
      existingCounts[k] = (existingCounts[k] || 0) + 1;
    });

    const fileKeyCounts = {};
    let imported = [];
    let ignoredDuplicates = [];

    rawRows.forEach(row => {
      const dateISO = parseDateWithFormat(row[dateCol], mapping.dateFormat);
      if (!dateISO) return;
      
      const t = {
        id: uid(),
        date: dateISO,
        label: (row[labelCol] || "").trim(),
        type: typeCol !== -1 ? (row[typeCol] || "").trim() : "",
        amount: parseAmountText(row[amountCol]),
        categoryId: ""
      };

      const key = transactionDedupeKey(t);
      fileKeyCounts[key] = (fileKeyCounts[key] || 0) + 1;
      const currentStored = existingCounts[key] || 0;
      
      if (fileKeyCounts[key] <= currentStored) {
        ignoredDuplicates.push(t);
      } else {
        existingCounts[key] = (existingCounts[key] || 0) + 1;
        imported.push(t);
      }
    });

    imported = applyRulesToTransactions(imported, currentData.bankImport.rules);
    const autoCategorized = imported.filter(t => t.categoryId).length;

    currentData.bankImport.columnMapping = newMapping;
    currentData.bankImport.transactions = [...currentData.bankImport.transactions, ...imported];
    saveFullData(currentData);

    return {
      imported: imported.length,
      duplicates: ignoredDuplicates.length,
      autoCategorized,
      ignoredDuplicates
    };
  }

  /**
   * S'abonne aux changements des données d'Import Bancaire.
   * @returns {Function} fonction de désabonnement
   */
  function subscribeBankImport(listener) {
    return deps().BudgetStore.subscribe(listener);
  }

  /* =========================================================================
   * Opérations en cours (Chèques, CB différées & Rapprochement bancaire)
   * ========================================================================= */

  /**
   * Construit le modèle de lecture complet pour la vue Opérations en cours.
   */
  function buildPendingOperations() {
    const data = loadFullData();
    return {
      pendingOperations: data?.bankImport?.pendingOperations || [],
      transactions: data?.bankImport?.transactions || [],
      categories: data?.bankImport?.categories || [],
      rules: data?.bankImport?.rules || [],
      charges: data?.charges || [],
      incomes: data?.incomes || [],
      oneoff: data?.oneoff || [],
      settings: data?.settings || {}
    };
  }

  /**
   * Crée ou met à jour une opération en cours (chèque, CB, virement, etc.)
   */
  function savePendingOperation(opData, opId) {
    const currentData = loadFullData();
    if (!currentData.bankImport) currentData.bankImport = {};
    if (!currentData.bankImport.pendingOperations) currentData.bankImport.pendingOperations = [];

    const { uid } = deps();
    const ops = [...currentData.bankImport.pendingOperations];
    let savedOp = null;

    if (opId) {
      const idx = ops.findIndex(o => o.id === opId);
      if (idx !== -1) {
        ops[idx] = {
          ...ops[idx],
          ...opData,
          id: opId
        };
        savedOp = ops[idx];
      } else {
        savedOp = {
          id: opId,
          status: opData.status || "pending",
          linkedTxId: opData.linkedTxId || null,
          clearedDate: opData.clearedDate || null,
          ...opData
        };
        ops.push(savedOp);
      }
    } else {
      savedOp = {
        id: opData.id || (uid ? uid() : `op_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`),
        status: opData.status || "pending",
        linkedTxId: opData.linkedTxId || null,
        clearedDate: opData.clearedDate || null,
        ...opData
      };
      ops.push(savedOp);
    }

    currentData.bankImport.pendingOperations = ops;
    saveFullData(currentData);
    return savedOp || ops;
  }

  /**
   * Supprime une opération en cours.
   */
  function deletePendingOperation(opId) {
    const currentData = loadFullData();
    if (!currentData.bankImport?.pendingOperations) return { success: true };
    currentData.bankImport.pendingOperations = currentData.bankImport.pendingOperations.filter(o => o.id !== opId);
    saveFullData(currentData);
    return { success: true, opId };
  }

  /**
   * Remet une opération rapprochée en circulation (statut pending).
   */
  function unlinkPendingOperation(opId) {
    const currentData = loadFullData();
    if (!currentData.bankImport?.pendingOperations) return null;
    let unlinked = null;
    currentData.bankImport.pendingOperations = currentData.bankImport.pendingOperations.map(o => {
      if (o.id === opId) {
        unlinked = {
          ...o,
          status: "pending",
          linkedTxId: null,
          clearedDate: null
        };
        return unlinked;
      }
      return o;
    });
    saveFullData(currentData);
    return unlinked;
  }

  /**
   * Lie manuellement une opération en cours avec une transaction bancaire (rapprochement).
   *
   * Les deux entités (opération en attente et transaction bancaire) sont deux représentations de la
   * même donnée : leur catégorisation doit redevenir cohérente à l'issue du rapprochement.
   * - Si les catégorisations sont déjà identiques (voir categorizationsMatch), rien à arbitrer : si
   *   l'opération porte des splits, ils sont transférés et réajustés sur le montant réel de la
   *   transaction (comportement historique).
   * - Si elles diffèrent (y compris quand un seul des deux côtés est catégorisé), l'appelant DOIT
   *   fournir categorizationChoice ("pending" ou "import") pour indiquer quel côté a été retenu par
   *   l'opérateur ; l'application ne choisit jamais à sa place. Le côté "perdant" est aligné sur le
   *   côté "gagnant" (categoryId ou splits, selon le cas), sans jamais aplatir un split en catégorie
   *   unique ni l'inverse.
   *
   * Si l'opération possède un budgetLineId, il est automatiquement propagé dans les matchings.
   *
   * @param {string} opId
   * @param {string} txId
   * @param {string} txDate
   * @param {"pending"|"import"|null} [categorizationChoice] Côté à retenir en cas de conflit de catégorisation.
   */
  function linkPendingOperation(opId, txId, txDate, categorizationChoice) {
    const currentData = loadFullData();
    if (!currentData.bankImport?.pendingOperations) return null;
    const allTx = currentData.bankImport.transactions || [];
    const matchedTx = allTx.find(t => t.id === txId) || null;

    let linked = null;
    let targetOp = null;
    currentData.bankImport.pendingOperations = currentData.bankImport.pendingOperations.map(o => {
      if (o.id === opId) {
        targetOp = o;
        return o; // finalized below once we know the resolved categorization
      }
      return o;
    });
    if (!targetOp) return null;

    const isEqual = matchedTx ? categorizationsMatch(targetOp, matchedTx) : true;
    let opCategoryOverride = null; // { categoryId, splits } applied to the pending op, if any
    let txCategoryOverride = null; // { categoryId, splits } applied to the bank transaction, if any

    if (isEqual) {
      if (Array.isArray(targetOp.splits) && targetOp.splits.length > 0 && matchedTx) {
        txCategoryOverride = { categoryId: "", splits: rescaleSplitsToAmount(targetOp.splits, Number(matchedTx.amount) || 0) };
      }
    } else if (categorizationChoice === "pending") {
      if (Array.isArray(targetOp.splits) && targetOp.splits.length > 0) {
        txCategoryOverride = { categoryId: "", splits: rescaleSplitsToAmount(targetOp.splits, Number(matchedTx?.amount) || 0) };
      } else {
        txCategoryOverride = { categoryId: targetOp.categoryId || "", splits: [] };
      }
    } else if (categorizationChoice === "import") {
      if (matchedTx && Array.isArray(matchedTx.splits) && matchedTx.splits.length > 0) {
        opCategoryOverride = { categoryId: "", splits: matchedTx.splits };
      } else {
        opCategoryOverride = { categoryId: matchedTx?.categoryId || "", splits: [] };
      }
    }
    // else: conflict left unresolved by the caller - link anyway (status/linkedTxId still set below)
    // but neither side's categorization is touched, so the discrepancy remains visible for a later fix.

    currentData.bankImport.pendingOperations = currentData.bankImport.pendingOperations.map(o => {
      if (o.id === opId) {
        linked = {
          ...o,
          ...(opCategoryOverride || {}),
          status: "cleared",
          linkedTxId: txId,
          clearedDate: txDate
        };
        return linked;
      }
      return o;
    });

    if (txCategoryOverride && currentData.bankImport.transactions) {
      currentData.bankImport.transactions = currentData.bankImport.transactions.map(t =>
        t.id === txId ? { ...t, ...txCategoryOverride } : t
      );
    }

    // Propagation automatique du budgetLineId dans les matchings de pointage
    if (linked && linked.budgetLineId && txId && txDate) {
      const monthISO = String(txDate).slice(0, 7);
      if (!currentData.bankImport.matchings) currentData.bankImport.matchings = [];
      const others = currentData.bankImport.matchings.filter(m => m.month !== monthISO);
      const existing = currentData.bankImport.matchings.find(m => m.month === monthISO) || { month: monthISO, links: [] };
      const otherLinks = (existing.links || []).filter(l => l.budgetLineId !== linked.budgetLineId);
      const thisLink = (existing.links || []).find(l => l.budgetLineId === linked.budgetLineId) || { budgetLineId: linked.budgetLineId, txIds: [] };
      const updatedLink = { ...thisLink, txIds: [...(thisLink.txIds || []).filter(id => id !== txId), txId] };
      currentData.bankImport.matchings = [...others, { ...existing, links: [...otherLinks, updatedLink] }];
    }

    saveFullData(currentData);
    return linked;
  }


  /**
   * Algorithme métier de rapprochement automatique entre opérations en cours et relevés bancaires.
   *
   * Une paire candidate n'est rapprochée automatiquement que si les catégorisations des deux côtés
   * sont déjà identiques (voir categorizationsMatch). Si elles diffèrent - y compris quand un seul des
   * deux côtés est catégorisé - l'opération reste "en attente" (non liée) : l'application ne choisit
   * jamais une catégorie à la place de l'opérateur. Ces paires sont comptabilisées dans
   * needsReviewCount et devront être traitées via le rapprochement manuel, qui proposera un choix.
   */
  function autoMatchPendingOperations() {
    const currentData = loadFullData();
    const pendingOps = currentData?.bankImport?.pendingOperations || [];
    let transactions = currentData?.bankImport?.transactions || [];

    let matchCount = 0;
    let needsReviewCount = 0;
    const currentlyLinked = new Set();
    pendingOps.forEach(op => {
      if (op.linkedTxId) currentlyLinked.add(op.linkedTxId);
    });

    const updated = pendingOps.map(op => {
      if (op.status === "cleared" && op.linkedTxId) return op;
      const opTargetAmt = Number(op.amount) || 0;

      let matchedTx = null;

      // 1. Recherche par référence exacte dans le libellé si référence >= 3 caractères
      if (op.refNumber && op.refNumber.length >= 3) {
        const foundByRef = transactions.find(t =>
          !currentlyLinked.has(t.id) &&
          (t.label || "").toLowerCase().includes(op.refNumber.toLowerCase()) &&
          Math.abs(Math.abs(Number(t.amount) || 0) - Math.abs(opTargetAmt)) < 0.01
        );
        if (foundByRef) {
          matchedTx = foundByRef;
        }
      }

      if (!matchedTx) {
        const opDateMs = op.date ? new Date(op.date).getTime() : 0;
        const exactCandidates = transactions.filter(t => {
          if (currentlyLinked.has(t.id)) return false;
          const txAmt = Number(t.amount) || 0;
          if (Math.abs(Math.abs(txAmt) - Math.abs(opTargetAmt)) >= 0.01) return false;
          if (opDateMs && t.date) {
            const tDateMs = new Date(t.date).getTime();
            const diffDays = Math.abs(tDateMs - opDateMs) / (1000 * 60 * 60 * 24);
            if (diffDays > 90) return false;
          }
          return true;
        });
        if (exactCandidates.length === 1) {
          matchedTx = exactCandidates[0];
        }
      }

      if (matchedTx) {
        if (!categorizationsMatch(op, matchedTx)) {
          needsReviewCount++;
          return op;
        }

        currentlyLinked.add(matchedTx.id);
        matchCount++;

        // Transfert des ventilations si présentes (catégorisations déjà identiques, seuls les
        // montants doivent être réajustés sur le montant réel de la transaction bancaire).
        if (Array.isArray(op.splits) && op.splits.length > 0) {
          const rescaled = rescaleSplitsToAmount(op.splits, Number(matchedTx.amount) || 0);
          transactions = transactions.map(t => t.id === matchedTx.id ? { ...t, splits: rescaled } : t);
        }

        return {
          ...op,
          status: "cleared",
          linkedTxId: matchedTx.id,
          clearedDate: matchedTx.date
        };
      }

      return op;
    });

    if (matchCount > 0) {
      currentData.bankImport.pendingOperations = updated;
      currentData.bankImport.transactions = transactions;
      saveFullData(currentData);
    }

    return { matchCount, needsReviewCount, updatedPendingOperations: updated };
  }

  /**
   * Importe des opérations CB différées depuis un fichier CSV parsé.
   */
  function importPendingCB(rawRows, colRoles, config) {
    const currentData = loadFullData();
    if (!currentData.bankImport) currentData.bankImport = {};
    if (!currentData.bankImport.pendingOperations) currentData.bankImport.pendingOperations = [];
    if (!currentData.bankImport.rules) currentData.bankImport.rules = [];

    const { parseDateWithFormat, parseAmountText, transactionDedupeKey, applyRulesToTransactions, uid } = deps();

    const dateCol = colRoles.indexOf("date");
    const labelCol = colRoles.indexOf("label");
    const amountCol = colRoles.indexOf("amount");

    if (dateCol === -1 || labelCol === -1 || amountCol === -1) {
      return {
        error: "Il faut au minimum assigner les rôles Date, Libellé et Montant à une colonne."
      };
    }

    const dateFormat = config?.dateFormat || "DD-MM-YYYY";
    const usePurchaseDate = !!config?.usePurchaseDate;

    const existingCounts = {};
    currentData.bankImport.pendingOperations.forEach(op => {
      const k = transactionDedupeKey(op);
      existingCounts[k] = (existingCounts[k] || 0) + 1;
    });

    const fileKeyCounts = {};
    let imported = [];
    let ignoredDuplicates = [];

    const parsePurchaseDateFromLabel = (label) => {
      if (!label) return null;
      const m = label.match(/DU\s+(\d{2})(\d{2})(\d{2,4})/i);
      if (m) {
        const d = parseInt(m[1], 10);
        const mo = parseInt(m[2], 10);
        let y = parseInt(m[3], 10);
        if (y < 100) y = 2000 + y;
        if (d >= 1 && d <= 31 && mo >= 1 && mo <= 12) {
          return `${y}-${String(mo).padStart(2, "0")}-${String(d).padStart(2, "0")}`;
        }
      }
      return null;
    };

    rawRows.forEach(row => {
      const rawDate = row[dateCol];
      let dateISO = parseDateWithFormat(rawDate, dateFormat) || parseDateWithFormat(rawDate, "DD/MM/YYYY") || parseDateWithFormat(rawDate, "YYYY-MM-DD");
      if (!dateISO) return;

      const rawLabel = (row[labelCol] || "").trim();
      if (!rawLabel) return;

      const amt = parseAmountText(row[amountCol]);
      const purchaseDate = parsePurchaseDateFromLabel(rawLabel);
      let finalOpDate = dateISO;
      let expectedDebitDate = dateISO;

      if (usePurchaseDate && purchaseDate) {
        finalOpDate = purchaseDate;
        expectedDebitDate = dateISO;
      }

      const op = {
        id: uid(),
        date: finalOpDate,
        expectedDate: expectedDebitDate,
        type: "cb",
        refNumber: "",
        label: rawLabel,
        amount: amt,
        categoryId: "",
        status: "pending",
        linkedTxId: null,
        clearedDate: null,
        notes: purchaseDate && !usePurchaseDate ? `Achat le ${purchaseDate.split("-").reverse().join("/")}` : ""
      };

      const key = transactionDedupeKey(op);
      fileKeyCounts[key] = (fileKeyCounts[key] || 0) + 1;
      const currentStored = existingCounts[key] || 0;

      if (fileKeyCounts[key] <= currentStored) {
        ignoredDuplicates.push(op);
      } else {
        existingCounts[key] = (existingCounts[key] || 0) + 1;
        imported.push(op);
      }
    });

    imported = applyRulesToTransactions(imported, currentData.bankImport.rules);
    const autoCategorized = imported.filter(op => op.categoryId).length;

    if (imported.length > 0) {
      currentData.bankImport.pendingOperations = [...currentData.bankImport.pendingOperations, ...imported];
      saveFullData(currentData);
    }

    return {
      imported: imported.length,
      duplicates: ignoredDuplicates.length,
      autoCategorized,
      ignoredDuplicates,
      firstOpDate: imported.length > 0 ? imported[0].date : null
    };
  }

  /**
   * Force l'import d'une opération en doublon.
   */
  function forceImportPendingOperation(op) {
    const currentData = loadFullData();
    if (!currentData.bankImport) currentData.bankImport = {};
    if (!currentData.bankImport.pendingOperations) currentData.bankImport.pendingOperations = [];
    if (!currentData.bankImport.rules) currentData.bankImport.rules = [];

    const { applyRulesToTransactions } = deps();
    const categorizedOp = applyRulesToTransactions([op], currentData.bankImport.rules)[0] || op;
    currentData.bankImport.pendingOperations.push(categorizedOp);
    saveFullData(currentData);
    return categorizedOp;
  }

  /**
   * Fusionne une saisie manuelle avec une opération importée (CB différé).
   * Conserve l'ID et la catégorie manuelle, met à jour les champs officiels et conserve le statut "pending" (non rapproché).
   */
  function mergePendingOperation(manualOpId, bankOp) {
    const currentData = loadFullData();
    if (!currentData.bankImport) currentData.bankImport = {};
    if (!currentData.bankImport.pendingOperations) currentData.bankImport.pendingOperations = [];
    const ops = currentData.bankImport.pendingOperations;
    const manualOp = ops.find(o => o.id === manualOpId);
    const cat = manualOp && manualOp.categoryId ? manualOp.categoryId : (bankOp?.categoryId || "");

    currentData.bankImport.pendingOperations = ops.filter(o => o.id !== bankOp?.id).map(o => {
      if (o.id === manualOpId) {
        return {
          ...o,
          date: bankOp?.date || o.date,
          expectedDate: bankOp?.expectedDate || o.expectedDate,
          label: bankOp?.label || o.label,
          amount: bankOp?.amount !== undefined ? bankOp.amount : o.amount,
          categoryId: cat,
          status: "pending",
          linkedTxId: null,
          clearedDate: null,
          notes: bankOp?.notes || o.notes
        };
      }
      return o;
    });
    saveFullData(currentData);
    return { success: true };
  }

  function subscribePendingOperations(listener) {
    return deps().BudgetStore.subscribe(listener);
  }

  function buildPointage() {
    const data = loadFullData();
    return {
      transactions: data?.bankImport?.transactions || [],
      categories: data?.bankImport?.categories || [],
      matchings: data?.bankImport?.matchings || [],
      pendingOperations: data?.bankImport?.pendingOperations || [],
      charges: data?.charges || [],
      incomes: data?.incomes || [],
      placements: data?.placements || [],
      settings: data?.settings || {}
    };
  }

  function savePointageMatching(monthISO, newLinks) {
    const currentData = loadFullData();
    if (!currentData.bankImport) currentData.bankImport = {};
    const others = (currentData.bankImport.matchings || []).filter(m => m.month !== monthISO);
    currentData.bankImport.matchings = [
      ...others,
      {
        month: monthISO,
        links: newLinks
      }
    ];
    saveFullData(currentData);
    return currentData.bankImport.matchings;
  }

  function subscribePointage(listener) {
    return deps().BudgetStore.subscribe(listener);
  }

  function buildAnalyse() {
    const data = loadFullData();
    return {
      data,
      bankImport: data?.bankImport || {},
      pendingOperations: data?.bankImport?.pendingOperations || [],
      charges: data?.charges || [],
      incomes: data?.incomes || [],
      placements: data?.placements || [],
      settings: data?.settings || {}
    };
  }

  function subscribeAnalyse(listener) {
    return deps().BudgetStore.subscribe(listener);
  }

  exports.BankImportService = {
    buildBankImport,
    updateBankImportMapping,
    addBankImportCategory,
    updateBankImportCategory,
    removeBankImportCategory,
    addBankImportRule,
    updateBankImportRule,
    removeBankImportRule,
    recalculateBankImportRules,
    setBankImportTransactionCategory,
    updateBankTransactionSplits,
    forceImportBankTransaction,
    importBankTransactions,
    subscribeBankImport
  };

  exports.PendingOperationsService = {
    buildPendingOperations,
    savePendingOperation,
    deletePendingOperation,
    unlinkPendingOperation,
    linkPendingOperation,
    autoMatchPendingOperations,
    importPendingCB,
    mergePendingOperation,
    forceImportPendingOperation,
    subscribePendingOperations
  };

  exports.PointageService = {
    buildPointage,
    savePointageMatching,
    subscribePointage
  };

  exports.AnalyseService = {
    buildAnalyse,
    subscribeAnalyse
  };

  // Export des fonctions pour utilisation par api.js
  exports.getRetraiteDataFromService = getRetraiteDataFromService;
  exports.saveRetraiteDataToService = saveRetraiteDataToService;
  exports.computeRetirementProjection = computeRetirementProjection;

})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);
