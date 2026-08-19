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
   * Récupère les données complètes depuis le localStorage
   * @returns {Object} Données complètes de l'application
   */
  function loadFullData() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? JSON.parse(raw) : {};
    } catch (err) {
      console.error("Erreur de chargement localStorage :", err);
      return {};
    }
  }

  /**
   * Sauvegarde les données complètes dans le localStorage
   * @param {Object} data - Données complètes à sauvegarder
   */
  function saveFullData(data) {
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

  // Export des fonctions pour utilisation par api.js
  exports.getRetraiteDataFromService = getRetraiteDataFromService;
  exports.saveRetraiteDataToService = saveRetraiteDataToService;
  exports.computeRetirementProjection = computeRetirementProjection;

})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);
