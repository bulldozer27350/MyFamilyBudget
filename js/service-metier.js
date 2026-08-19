/**
 * Service métier — Vue d'ensemble
 *
 * Seule couche autorisée à accéder au stockage (localStorage via BudgetStore) et à
 * appliquer les règles métier de la Vue d'ensemble. Les composants React ne doivent
 * jamais l'utiliser directement : ils passent par la façade api.js.
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
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);
