/**
 * Moteurs de calcul financier, simulations pluriannuelles et projections
 */
(function (exports) {
  'use strict';

  const {
    yearOf,
    eur
  } = (typeof window !== 'undefined' && window.BudgetApp) ? window.BudgetApp : (exports.eur ? exports : {});
  function monthsActiveInYear(startISO, endISO, year) {
    if (!startISO || !endISO) return 0;
    const start = new Date(startISO),
      end = new Date(endISO);
    const yStart = new Date(year, 0, 1),
      yEnd = new Date(year, 11, 31);
    const s = start > yStart ? start : yStart;
    const e = end < yEnd ? end : yEnd;
    if (e < s) return 0;
    return (e.getFullYear() - s.getFullYear()) * 12 + (e.getMonth() - s.getMonth()) + 1;
  }
  function incomeAnnualForYear(row, year) {
    const startYear = yearOf(row.start) ?? year;
    const growth = Number(row.growthRate) || 0;
    const yearsElapsed = Math.max(0, year - startYear);
    const effectiveMonthly = (Number(row.monthly) || 0) * Math.pow(1 + growth, yearsElapsed);
    return effectiveMonthly * monthsActiveInYear(row.start, row.end, year);
  }
  function chargeEffectiveGrowth(row, defaultGrowth) {
    const raw = row.growthRate;
    const isUnset = raw === undefined || raw === null || raw === "";
    return isUnset ? Number(defaultGrowth) || 0 : Number(raw) || 0;
  }
  function chargeAnnualForYear(row, year, defaultGrowth) {
    const startYear = yearOf(row.start) ?? year;
    const growth = chargeEffectiveGrowth(row, defaultGrowth);
    const yearsElapsed = Math.max(0, year - startYear);
    const effectiveMonthly = (Number(row.monthly) || 0) * Math.pow(1 + growth, yearsElapsed);
    return effectiveMonthly * monthsActiveInYear(row.start, row.end, year);
  }
  function incomeMonthlyForYear(row, year) {
    const startYear = yearOf(row.start) ?? year;
    const growth = Number(row.growthRate) || 0;
    const yearsElapsed = Math.max(0, year - startYear);
    return (Number(row.monthly) || 0) * Math.pow(1 + growth, yearsElapsed);
  }
  function chargeMonthlyForYear(row, year, defaultGrowth) {
    const startYear = yearOf(row.start) ?? year;
    const growth = chargeEffectiveGrowth(row, defaultGrowth);
    const yearsElapsed = Math.max(0, year - startYear);
    return (Number(row.monthly) || 0) * Math.pow(1 + growth, yearsElapsed);
  }
  function placementsMonthlyAnnualForYear(placements, year) {
    return (placements || []).reduce((s, p) => {
      const monthly = Number(p.monthly) || 0;
      if (!monthly) return s;
      const fromDate = p.monthlyFrom ? new Date(p.monthlyFrom) : null;
      const untilDate = p.monthlyUntil ? new Date(p.monthlyUntil) : null;
      let months = 12;
      let startMonth = 0,
        endMonth = 11;
      if (fromDate) {
        const fromYear = fromDate.getFullYear();
        if (year < fromYear) return s;
        if (year === fromYear) startMonth = fromDate.getMonth();
      }
      if (untilDate) {
        const untilYear = untilDate.getFullYear();
        if (year > untilYear) return s;
        if (year === untilYear) endMonth = untilDate.getMonth();
      }
      months = Math.max(0, endMonth - startMonth + 1);
      return s + monthly * months;
    }, 0);
  }
  function partsForYear(children, exitAge, year) {
    const attached = (children || []).filter(c => year - Number(c.birthYear) < (Number(exitAge) || 21)).length;
    let parts = 2; // couple marié / pacsé
    for (let i = 1; i <= attached; i++) parts += i <= 2 ? 0.5 : 1;
    return parts;
  }
  function taxForOnePart(q, brackets) {
    let tax = 0;
    let prevThreshold = 0;
    const sorted = (brackets || []).map(b => ({
      upTo: b.upTo === "" || b.upTo == null ? Infinity : Number(b.upTo),
      rate: Number(b.rate) || 0
    })).sort((a, b) => a.upTo - b.upTo);
    for (const b of sorted) {
      if (q > prevThreshold) {
        const taxableInBracket = Math.min(q, b.upTo) - prevThreshold;
        tax += taxableInBracket * b.rate;
      }
      prevThreshold = b.upTo;
      if (q <= b.upTo) break;
    }
    return tax;
  }
  function variableIncomeDetailForYear(data, year) {
    let total = 0,
      taxable = 0;
    for (const v of data.variableIncomes || []) {
      if (year < Number(v.startYear) || year > Number(v.endYear)) continue;
      const refRow = (data.incomes || []).find(r => r.label === v.refIncomeLabel);
      const refAnnual = refRow ? incomeAnnualForYear(refRow, year) : 0;
      const forecast = refAnnual * (Number(v.rate) || 0);
      const override = (data.variableOverrides || []).find(o => o.label === v.label && Number(o.year) === year);
      const amount = override ? Number(override.amount) || 0 : forecast;
      const isTaxable = override && override.taxable === "Non" ? false : override && override.taxable === "Oui" ? true : v.taxable !== "Non";
      total += amount;
      if (isTaxable) taxable += amount;
    }
    return {
      total,
      taxable
    };
  }

  /* ============================== Retraite ============================== */
  const TRIMESTRES_REQUIS = 172;
  const AGE_TAUX_PLEIN_AUTO = 67;
  const DECOTE_PAR_TRIMESTRE = 0.00625;
  const SURCOTE_PAR_TRIMESTRE = 0.0125;
  const TAUX_PLEIN = 0.50;
  const TAUX_MINORE_PLANCHER = 0.375;
  const MAJORATION_3_ENFANTS = 0.10;
  function passForYear(data, year) {
    const base = Number(data.retirement?.pass2026) || 47100;
    const growth = Number(data.retirement?.passGrowthRate) ?? 0.015;
    return base * Math.pow(1 + growth, year - 2026);
  }
  function agircPointValueForYear(data, year) {
    const base = Number(data.retirement?.agircPointValue) || 1.4386;
    const baseYear = yearOf(data.retirement?.agircPointDateGlobal) || 2025;
    const growth = Number(data.retirement?.agircPointGrowthRate) ?? 0.01;
    return base * Math.pow(1 + growth, Math.max(0, year - baseYear));
  }
  function projectedAnnualSalary(data, person, year) {
    if (!person.incomeLabel) return 0;
    const row = (data.incomes || []).find(r => r.label === person.incomeLabel);
    if (!row) return 0;
    return incomeAnnualForYear(row, year);
  }
  function nbEnfants(data) {
    return (data.taxChildren || []).length;
  }
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
  function pensionIncomeRows(data, retireYear, lastYear) {
    const inflationRate = Number(data.settings.inflationRate) || 0;
    return (data.retirement?.people || []).map(person => {
      const proj = computeRetirementProjection(data, person, retireYear);
      if (!proj.pensionTotaleMensuelle || proj.pensionTotaleMensuelle <= 0) return null;
      return {
        id: `pension-${person.id}`,
        label: `Pension ${person.name || "retraite"} (auto)`,
        monthly: proj.pensionTotaleMensuelle,
        start: `${retireYear}-01-01`,
        end: `${Math.max(retireYear, lastYear)}-12-31`,
        growthRate: inflationRate
      };
    }).filter(Boolean);
  }

  /* ============================== Date Pivot ============================== */
  function computePivotBalance(data) {
    const {
      pivotDate,
      pivotMode,
      startBalance
    } = data?.settings || {};
    if (!pivotDate) return null;
    if (pivotMode === "manual") return Number(startBalance) || 0;
    const transactions = (data.bankImport || {}).transactions || [];
    const base = Number(startBalance) || 0;
    const sum = transactions.filter(t => t.date && t.date <= pivotDate).reduce((s, t) => s + (Number(t.amount) || 0), 0);
    return base + sum;
  }
  function latestTransactionDate(data) {
    const transactions = (data?.bankImport || {}).transactions || [];
    if (transactions.length === 0) return null;
    return transactions.reduce((best, t) => !best || t.date && t.date > best ? t.date : best, null);
  }

  /* ============================== Timeline Détaillée ============================== */
  function calculateDetailedFinancialTimeline(data, years, scenario = "corr", useConstantEuros = false) {
    if (!data || !years || years.length === 0) return {
      yearly: [],
      monthly: [],
      daily: [],
      events: []
    };
    const startYear = years[0];
    const endYear = years[years.length - 1];
    const placements = data.placements || [];
    const placementBalances = {};
    placements.forEach(p => {
      placementBalances[p.label] = Number(p.balance) || 0;
    });
    const realEstateItems = (data.realEstate || []).map(r => ({
      label: r.label,
      currentValue: Number(r.currentValue) || 0,
      annualGrowthRate: Number(r.annualGrowthRate) || 0,
      valuationYear: r.valuationYear ? Number(r.valuationYear) : startYear
    }));
    function realEstateValueAt(item, y) {
      const elapsed = y - item.valuationYear;
      return item.currentValue * Math.pow(1 + item.annualGrowthRate, Math.max(0, elapsed));
    }
    const pivotBalanceValue = computePivotBalance(data);
    let cashBalance = pivotBalanceValue !== null ? pivotBalanceValue : Number(data.settings.startBalance) || 0;
    const rateKey = scenario === "pess" ? "ratePess" : scenario === "opti" ? "rateOpti" : "rateCorr";
    const activeLoans = (data.loans || []).map(l => ({
      id: l.id,
      label: l.label,
      crd: Number(l.crd) || 0,
      rate: Number(l.rate) || 0,
      monthly: Number(l.monthly) || 0,
      insurance: Number(l.insurance) || 0,
      startDate: l.startDate || "2026-08-01",
      endDate: l.endDate || "2035-07-05"
    }));
    const taxMap = {};
    const children = data.taxChildren || [];
    const exitAge = data.settings.childExitAge ?? 21;
    const abattement = Number(data.settings.taxAbattement) || 0;
    const brackets = data.taxBrackets || [];
    const retireYear = (Number(data.settings.birthYear) || 1985) + (Number(data.settings.retireAge) || 64);
    const effectiveIncomes = [...(data.incomes || []), ...pensionIncomeRows(data, retireYear, endYear)];
    years.forEach(y => {
      const regularIncome = effectiveIncomes.reduce((s, i) => s + incomeAnnualForYear(i, y), 0);
      const varDetail = variableIncomeDetailForYear(data, y);
      const taxableIncome = (regularIncome + varDetail.taxable) * (1 - abattement);
      const parts = partsForYear(children, exitAge, y);
      const taxForecast = parts > 0 ? taxForOnePart(taxableIncome / parts, brackets) * parts : 0;
      const grossPayroll = regularIncome + varDetail.taxable;
      const rateForecast = grossPayroll > 0 ? taxForecast / grossPayroll : 0;
      const rateOverride = (data.taxRateOverrides || []).find(o => Number(o.year) === y);
      const ratePAS = rateOverride ? Number(rateOverride.rate) || 0 : rateForecast;
      const withheld = ratePAS * grossPayroll;
      taxMap[y] = withheld;
    });
    const dailyPoints = [];
    const monthlyPoints = [];
    const yearlyPoints = [];
    const movementEvents = [];
    const recordMovement = ({
      dateISO,
      source,
      target,
      amount,
      sourceBefore,
      sourceAfter,
      comment = "",
      type = "Virement"
    }) => {
      const a = Number(amount) || 0;
      if (a <= 0) return;
      movementEvents.push({
        dateISO,
        timestamp: new Date(dateISO).getTime(),
        source: source || "Compte courant",
        target: target || "",
        amount: Math.round(a * 100) / 100,
        sourceBefore: Math.round((Number(sourceBefore) || 0) * 100) / 100,
        sourceAfter: Math.round((Number(sourceAfter) || 0) * 100) / 100,
        comment: comment || "",
        type
      });
    };
    const oneoffByDate = {};
    (data.oneoff || []).forEach(o => {
      if (!o.date) return;
      if (!oneoffByDate[o.date]) oneoffByDate[o.date] = 0;
      oneoffByDate[o.date] += Number(o.amount) || 0;
    });
    const transfersByDate = {};
    (data.transfers || []).forEach(t => {
      if (!t.date) return;
      if (!transfersByDate[t.date]) transfersByDate[t.date] = [];
      transfersByDate[t.date].push(t);
    });
    const monthNames = ["Jan", "Fév", "Mar", "Avr", "Mai", "Juin", "Juil", "Août", "Sep", "Oct", "Nov", "Déc"];
    const sweepEnabled = !!data.settings.sweepEnabled;
    const cashCeiling = data.settings.cashCeiling !== undefined && data.settings.cashCeiling !== null && data.settings.cashCeiling !== "" ? Number(data.settings.cashCeiling) : Infinity;
    const cashFloor = Number(data.settings.cashFloor) || 0;
    const sweepAccounts = placements.filter(p => p.sweepPriority !== undefined && p.sweepPriority !== null && p.sweepPriority !== "").map(p => ({
      label: p.label,
      priority: Number(p.sweepPriority) || 0,
      cap: p.sweepCap !== undefined && p.sweepCap !== null && p.sweepCap !== "" ? Number(p.sweepCap) : Infinity
    })).sort((a, b) => a.priority - b.priority);
    function sweepExcessToPlacements(dateISO) {
      if (!sweepEnabled || !sweepAccounts.length) return;
      let excess = cashBalance - cashCeiling;
      if (excess <= 0) return;
      for (const acc of sweepAccounts) {
        if (excess <= 0) break;
        const cur = placementBalances[acc.label] || 0;
        const room = Math.max(0, acc.cap - cur);
        const deposit = Math.min(excess, room);
        if (deposit > 0) {
          const before = cashBalance;
          placementBalances[acc.label] = cur + deposit;
          cashBalance -= deposit;
          excess -= deposit;
          recordMovement({
            dateISO,
            source: "Compte courant",
            target: acc.label,
            amount: deposit,
            sourceBefore: before,
            sourceAfter: cashBalance,
            comment: `Excédent de trésorerie au-dessus de ${eur(cashCeiling)}${acc.cap !== Infinity ? ` ; plafond de ${eur(acc.cap)} respecté` : ""}`,
            type: "Virement automatique"
          });
        }
      }
    }
    function refillCashFromPlacements(dateISO) {
      if (!sweepEnabled || !sweepAccounts.length) return false;
      let deficit = cashFloor - cashBalance;
      if (deficit <= 0) return false;
      let withdrewAny = false;
      for (const acc of sweepAccounts) {
        if (deficit <= 0) break;
        const cur = placementBalances[acc.label] || 0;
        const withdraw = Math.min(deficit, Math.max(0, cur));
        if (withdraw > 0) {
          const before = cur;
          placementBalances[acc.label] = cur - withdraw;
          cashBalance += withdraw;
          deficit -= withdraw;
          withdrewAny = true;
          recordMovement({
            dateISO,
            source: acc.label,
            target: "Compte courant",
            amount: withdraw,
            sourceBefore: before,
            sourceAfter: placementBalances[acc.label],
            comment: `Trésorerie sous le seuil bas de ${eur(cashFloor)}`,
            type: "Virement automatique"
          });
        }
      }
      return withdrewAny;
    }
    const pausablePlacements = placements.filter(p => p.pausePriority !== undefined && p.pausePriority !== null && p.pausePriority !== "");
    const maxPauseLevel = pausablePlacements.length ? Math.max(...pausablePlacements.map(p => Number(p.pausePriority) || 0)) : 0;
    const bufferWatch = placements.filter(p => p.pauseTriggerBalance !== undefined && p.pauseTriggerBalance !== null && p.pauseTriggerBalance !== "").map(p => ({
      label: p.label,
      trigger: Number(p.pauseTriggerBalance) || 0
    }));
    let pauseLevelFromRefill = 0;
    let pauseLevel = 0;
    for (let y = startYear; y <= endYear; y++) {
      const totalTaxY = taxMap[y] || 0;
      const monthlyTax = totalTaxY / 12;
      for (let m = 0; m < 12; m++) {
        const monthNum = m + 1;
        const monthStr = String(monthNum).padStart(2, "0");
        const monthKey = `${y}-${monthStr}`;
        const daysInMonth = new Date(y, m + 1, 0).getDate();
        let refillNeededThisMonth = false;
        let monthlyIncome = 0;
        effectiveIncomes.forEach(i => {
          if (!i.start || !i.end) return;
          const sY = yearOf(i.start),
            eY = yearOf(i.end);
          if (y >= sY && y <= eY) {
            const growth = Number(i.growthRate) || 0;
            const yearsElapsed = Math.max(0, y - sY);
            monthlyIncome += (Number(i.monthly) || 0) * Math.pow(1 + growth, yearsElapsed);
          }
        });
        let variableIncomeM = 0;
        if (m === 4) {
          (data.variableIncomes || []).forEach(v => {
            if (y < Number(v.startYear) || y > Number(v.endYear)) return;
            const refRow = (data.incomes || []).find(r => r.label === v.refIncomeLabel);
            const refAnnual = refRow ? incomeAnnualForYear(refRow, y) : 0;
            const forecast = refAnnual * (Number(v.rate) || 0);
            const override = (data.variableOverrides || []).find(o => o.label === v.label && Number(o.year) === y);
            variableIncomeM += override ? Number(override.amount) || 0 : forecast;
          });
        }
        cashBalance += monthlyIncome + variableIncomeM;
        (data.charges || []).forEach(c => {
          if (!c.start || !c.end) return;
          const sY = yearOf(c.start),
            eY = yearOf(c.end);
          if (!(y >= sY && y <= eY)) return;
          const growth = chargeEffectiveGrowth(c, data.settings.inflationRate);
          const yearsElapsed = Math.max(0, y - sY);
          const amount = (Number(c.monthly) || 0) * Math.pow(1 + growth, yearsElapsed);
          if (amount <= 0) return;
          const before = cashBalance;
          cashBalance -= amount;
          recordMovement({
            dateISO: `${monthKey}-01`,
            source: "Compte courant",
            target: c.label,
            amount,
            sourceBefore: before,
            sourceAfter: cashBalance,
            type: "Paiement mensuel"
          });
        });
        if (monthlyTax > 0) {
          const before = cashBalance;
          cashBalance -= monthlyTax;
          recordMovement({
            dateISO: `${monthKey}-01`,
            source: "Compte courant",
            target: "Prélèvement à la source",
            amount: monthlyTax,
            sourceBefore: before,
            sourceAfter: cashBalance,
            comment: "PAS calculé par le modèle",
            type: "Impôt"
          });
        }
        placements.forEach(p => {
          const rate = Number(p[rateKey]) || 0;
          const monthlyRate = rate / 12;
          const cur = placementBalances[p.label] || 0;
          const interest = cur * monthlyRate;
          const monthlyFromYear = p.monthlyFrom ? new Date(p.monthlyFrom).getFullYear() : startYear;
          const monthlyFromMonth = p.monthlyFrom ? new Date(p.monthlyFrom).getMonth() : 0;
          const afterStart = y > monthlyFromYear || y === monthlyFromYear && m >= monthlyFromMonth;
          let beforeEnd = true;
          if (p.monthlyUntil) {
            const untilYear = new Date(p.monthlyUntil).getFullYear();
            const untilMonth = new Date(p.monthlyUntil).getMonth();
            beforeEnd = y < untilYear || y === untilYear && m <= untilMonth;
          }
          const contributionActive = afterStart && beforeEnd;
          const isPaused = p.pausePriority !== undefined && p.pausePriority !== null && p.pausePriority !== "" && Number(p.pausePriority) <= pauseLevel;
          const add = contributionActive && !isPaused ? Number(p.monthly) || 0 : 0;
          placementBalances[p.label] = cur + interest;
          if (add > 0) {
            const before = cashBalance;
            cashBalance -= add;
            placementBalances[p.label] += add;
            recordMovement({
              dateISO: `${monthKey}-01`,
              source: "Compte courant",
              target: p.label,
              amount: add,
              sourceBefore: before,
              sourceAfter: cashBalance,
              type: "Versement placement"
            });
          }
        });
        let totalCRD = 0;
        activeLoans.forEach(l => {
          if (l.crd > 0) {
            const startY = yearOf(l.startDate) || 2026;
            const startM = l.startDate ? new Date(l.startDate).getMonth() : 0;
            if (y > startY || y === startY && m >= startM) {
              const monthlyInterest = l.crd * (l.rate / 12);
              const netPayment = Math.max(0, l.monthly - l.insurance);
              const principalPaid = Math.min(l.crd, netPayment - monthlyInterest);
              l.crd = Math.max(0, l.crd - principalPaid);
              const endY = yearOf(l.endDate) || 2099;
              const endM = l.endDate ? new Date(l.endDate).getMonth() : 11;
              if (y > endY || y === endY && m >= endM) l.crd = 0;
            }
          }
          totalCRD += l.crd;
        });
        totalCRD = Math.round(totalCRD * 100) / 100;
        for (let d = 1; d <= daysInMonth; d++) {
          const dayStr = String(d).padStart(2, "0");
          const dateISO = `${monthKey}-${dayStr}`;
          if (transfersByDate[dateISO]) {
            transfersByDate[dateISO].forEach(t => {
              const amt = Number(t.amount) || 0;
              if (amt <= 0) return;
              if (t.placement && placementBalances[t.placement] !== undefined) {
                const before = placementBalances[t.placement];
                placementBalances[t.placement] = before - amt;
                const cashBefore = cashBalance;
                cashBalance += amt;
                recordMovement({
                  dateISO,
                  source: t.placement,
                  target: "Compte courant",
                  amount: amt,
                  sourceBefore: before,
                  sourceAfter: placementBalances[t.placement],
                  comment: t.notes || "",
                  type: "Retrait placement"
                });
              }
            });
          }
          if (oneoffByDate[dateISO]) {
            (data.oneoff || []).filter(o => o.date === dateISO).forEach(o => {
              const amt = Number(o.amount) || 0;
              if (amt <= 0) return;
              const before = cashBalance;
              cashBalance -= amt;
              recordMovement({
                dateISO,
                source: "Compte courant",
                target: o.label,
                amount: amt,
                sourceBefore: before,
                sourceAfter: cashBalance,
                comment: o.notes || "",
                type: "Dépense ponctuelle"
              });
            });
          }
          if (refillCashFromPlacements(dateISO)) refillNeededThisMonth = true;
          if (d === daysInMonth) {
            sweepExcessToPlacements(dateISO);
            if (refillNeededThisMonth) {
              pauseLevelFromRefill = Math.min(maxPauseLevel, pauseLevelFromRefill + 1);
            } else if (cashBalance >= cashCeiling) {
              pauseLevelFromRefill = Math.max(0, pauseLevelFromRefill - 1);
            }
            const alertCount = bufferWatch.length ? bufferWatch.filter(b => (placementBalances[b.label] || 0) < b.trigger).length : 0;
            const pauseLevelFromAlerts = Math.min(maxPauseLevel, alertCount);
            pauseLevel = Math.max(pauseLevelFromRefill, pauseLevelFromAlerts);
          }
          let sumPlacements = 0;
          const pSnap = {};
          placements.forEach(p => {
            const val = Math.round((placementBalances[p.label] || 0) * 100) / 100;
            pSnap[p.label] = val;
            sumPlacements += placementBalances[p.label] || 0;
          });
          const elapsedYears = y - startYear + (m + d / daysInMonth) / 12;
          const inflationRate = Number(data.settings.inflationRate) || 0;
          const deflator = useConstantEuros ? Math.pow(1 / (1 + inflationRate), elapsedYears) : 1;
          const realEstateTotal = realEstateItems.reduce((sum, r) => sum + realEstateValueAt(r, y), 0);
          const roundCash = Math.round(cashBalance * deflator * 100) / 100;
          const totalAvoirs = Math.round((cashBalance + sumPlacements + realEstateTotal) * deflator * 100) / 100;
          const passifCRD = Math.round(totalCRD * deflator * 100) / 100;
          const patrimoineNet = Math.round((totalAvoirs - passifCRD) * 100) / 100;
          for (const k in pSnap) pSnap[k] = Math.round(pSnap[k] * deflator * 100) / 100;
          const timestamp = new Date(y, m, d).getTime();
          const pt = {
            dateISO,
            timestamp,
            label: `${dayStr}/${monthStr}/${y}`,
            year: y,
            month: monthNum,
            day: d,
            cash: roundCash,
            totalAvoirs,
            passifCRD: totalCRD,
            patrimoineNet,
            ...pSnap
          };
          dailyPoints.push(pt);
        }
        const monthLastPt = dailyPoints[dailyPoints.length - 1];
        monthlyPoints.push({
          ...monthLastPt,
          label: `${monthNames[m]} ${y}`
        });
      }
      const yearLastPt = monthlyPoints[monthlyPoints.length - 1];
      yearlyPoints.push({
        ...yearLastPt,
        label: String(y)
      });
    }
    movementEvents.sort((a, b) => a.timestamp - b.timestamp);
    return {
      yearly: yearlyPoints,
      monthly: monthlyPoints,
      daily: dailyPoints,
      events: movementEvents
    };
  }

  /* ============================== Projections & Allocations ============================== */
  function projectPlacementBalanceAt(p, targetDateISO, rateKey, transfers) {
    const balanceDate = p.balanceDate ? new Date(p.balanceDate) : new Date(targetDateISO);
    const target = new Date(targetDateISO);
    let balance = Number(p.balance) || 0;
    if (isNaN(balanceDate.getTime()) || isNaN(target.getTime()) || target <= balanceDate) return balance;
    const monthlyRate = (Number(p[rateKey]) || 0) / 12;
    const monthlyContrib = Number(p.monthly) || 0;
    const monthlyFromRaw = p.monthlyFrom ? new Date(p.monthlyFrom) : balanceDate;
    const monthlyFrom = new Date(monthlyFromRaw.getFullYear(), monthlyFromRaw.getMonth(), 1);
    let cursor = new Date(balanceDate.getFullYear(), balanceDate.getMonth(), 1);
    const end = new Date(target.getFullYear(), target.getMonth(), 1);
    while (cursor < end) {
      balance = balance * (1 + monthlyRate);
      if (cursor >= monthlyFrom) balance += monthlyContrib;
      const withdrawn = (transfers || []).filter(t => {
        if (t.placement !== p.label || !t.date) return false;
        const td = new Date(t.date);
        return td.getFullYear() === cursor.getFullYear() && td.getMonth() === cursor.getMonth();
      }).reduce((s, t) => s + (Number(t.amount) || 0), 0);
      balance -= withdrawn;
      cursor = new Date(cursor.getFullYear(), cursor.getMonth() + 1, 1);
    }
    return Math.max(0, balance);
  }
  function classifyAllocation(placements, getBalance, initialCash, categories) {
    const C_REF = exports.C || window.BudgetApp && window.BudgetApp.C || {};
    const bucketByCategory = {};
    (categories || []).forEach(c => {
      bucketByCategory[c.name] = c.bucket;
    });
    const meta = {
      cash: {
        label: "Cash & Livrets réglementés",
        color: C_REF.pine || "#2F5D50"
      },
      fondsEuros: {
        label: "Fonds en Euros (Sécurité)",
        color: C_REF.navy || "#28394A"
      },
      immobilier: {
        label: "Immobilier (SCPI / SC)",
        color: C_REF.brick || "#A8503C"
      },
      actions: {
        label: "Actions Monde & Thématiques",
        color: C_REF.gold || "#93802E"
      },
      obligations: {
        label: "Obligations (Taux)",
        color: "#6B3FA0"
      },
      epargneSalariale: {
        label: "Épargne Salariale & PER",
        color: "#17A2B8"
      }
    };
    const buckets = {
      cash: initialCash || 0,
      fondsEuros: 0,
      actions: 0,
      obligations: 0,
      immobilier: 0,
      epargneSalariale: 0
    };
    const unmatched = [];
    (placements || []).forEach(p => {
      const val = getBalance(p);
      const bucket = bucketByCategory[p.category];
      if (bucket && buckets.hasOwnProperty(bucket)) {
        buckets[bucket] += val;
      } else {
        buckets.cash += val;
        if (val > 0.5) unmatched.push(p.label || "(sans nom)");
      }
    });
    const total = Object.keys(buckets).reduce((s, k) => s + buckets[k], 0);
    const allocation = Object.keys(buckets).map(k => ({
      key: k,
      label: meta[k].label,
      amount: buckets[k],
      color: meta[k].color,
      pct: total ? buckets[k] / total * 100 : 0
    })).filter(item => item.amount > 0);
    return {
      allocation,
      unmatched
    };
  }

  /**
   * Hook personnalisé React calculant toutes les projections macro
   */
  function useFinancialProjections(data, useConstantEuros) {
    const {
      useMemo
    } = React;
    const retireYear = useMemo(() => (Number(data?.settings.birthYear) || 1985) + (Number(data?.settings.retireAge) || 64), [data]);
    const startYear = useMemo(() => {
      return findEarliestYear(data);
    }, [data]);
    const years = useMemo(() => {
      if (!data) return [];
      const minEnd = retireYear + 3;
      const wantedEnd = (Number(data.settings.birthYear) || 1985) + (Number(data.settings.simulateUntilAge) || 0);
      const end = Math.max(minEnd, wantedEnd);
      const arr = [];
      for (let y = startYear; y <= end; y++) arr.push(y);
      return arr;
    }, [data, retireYear, startYear]);
    const effectiveIncomes = useMemo(() => {
      if (!data) return [];
      const lastYear = years.length ? years[years.length - 1] : retireYear;
      return [...(data.incomes || []), ...pensionIncomeRows(data, retireYear, lastYear)];
    }, [data, years, retireYear]);
    const taxYearly = useMemo(() => {
      if (!data) return [];
      const brackets = data.taxBrackets || [];
      const children = data.taxChildren || [];
      const exitAge = data.settings.childExitAge ?? 21;
      const abattement = Number(data.settings.taxAbattement) || 0;
      return years.map(year => {
        const regularIncome = effectiveIncomes.reduce((s, i) => s + incomeAnnualForYear(i, year), 0);
        const varDetail = variableIncomeDetailForYear(data, year);
        const taxableIncome = (regularIncome + varDetail.taxable) * (1 - abattement);
        const parts = partsForYear(children, exitAge, year);
        const taxForecast = parts > 0 ? taxForOnePart(taxableIncome / parts, brackets) * parts : 0;
        const taxOverride = (data.taxActualOverrides || []).find(o => Number(o.year) === year);
        const taxActual = taxOverride ? Number(taxOverride.amount) || 0 : taxForecast;
        const grossPayroll = regularIncome + varDetail.taxable;
        const rateForecast = grossPayroll > 0 ? taxForecast / grossPayroll : 0;
        const rateOverride = (data.taxRateOverrides || []).find(o => Number(o.year) === year);
        const ratePAS = rateOverride ? Number(rateOverride.rate) || 0 : rateForecast;
        const withheld = ratePAS * grossPayroll;
        return {
          year,
          parts,
          taxableIncome,
          taxForecast,
          taxActual,
          ratePAS,
          withheld
        };
      });
    }, [data, years, effectiveIncomes]);
    const cashflow = useMemo(() => {
      if (!data) return [];
      const pivotBalanceValue = computePivotBalance(data);
      let balance = pivotBalanceValue !== null ? pivotBalanceValue : Number(data.settings.startBalance) || 0;
      return years.map((year, idx) => {
        const income = effectiveIncomes.reduce((s, i) => s + incomeAnnualForYear(i, year), 0);
        const variableIncome = (data.variableIncomes || []).reduce((s, v) => {
          if (year < Number(v.startYear) || year > Number(v.endYear)) return s;
          const refRow = (data.incomes || []).find(r => r.label === v.refIncomeLabel);
          const refAnnual = refRow ? incomeAnnualForYear(refRow, year) : 0;
          const forecast = refAnnual * (Number(v.rate) || 0);
          const override = (data.variableOverrides || []).find(o => o.label === v.label && Number(o.year) === year);
          const amount = override ? Number(override.amount) || 0 : forecast;
          return s + amount;
        }, 0);
        const savings = placementsMonthlyAnnualForYear(data.placements, year);
        const charges = (data.charges || []).reduce((s, i) => s + chargeAnnualForYear(i, year, data.settings.inflationRate), 0);
        const oneoff = (data.oneoff || []).filter(o => yearOf(o.date) === year).reduce((s, o) => s + (Number(o.amount) || 0), 0);
        const transfersY = (data.transfers || []).filter(t => yearOf(t.date) === year).reduce((s, t) => s + (Number(t.amount) || 0), 0);
        const taxInfo = taxYearly[idx];
        const impots = taxInfo ? taxInfo.withheld : 0;
        const prevTax = idx > 0 ? taxYearly[idx - 1] : null;
        const regularisation = prevTax ? prevTax.taxActual - prevTax.withheld : 0;
        const net = income + variableIncome - savings - charges - oneoff + transfersY - impots - regularisation;
        balance += net;
        return {
          year,
          income,
          variableIncome,
          savings,
          charges,
          oneoff,
          transfersY,
          impots,
          regularisation,
          net,
          balance
        };
      });
    }, [data, years, taxYearly, effectiveIncomes]);
    const patrimoine = useMemo(() => {
      if (!data) return {
        perPlacement: [],
        totals: []
      };
      const perPlacement = (data.placements || []).map(p => {
        let pess = Number(p.balance) || 0,
          corr = Number(p.balance) || 0,
          opti = Number(p.balance) || 0;
        const monthlyFromYear = p.monthlyFrom ? new Date(p.monthlyFrom).getFullYear() : years[0] || 2026;
        const monthlyUntilYear = p.monthlyUntil ? new Date(p.monthlyUntil).getFullYear() : null;
        const rows = years.map(year => {
          const withdraw = (data.transfers || []).filter(t => t.placement === p.label && yearOf(t.date) === year).reduce((s, t) => s + (Number(t.amount) || 0), 0);
          const withinWindow = year >= monthlyFromYear && (monthlyUntilYear === null || year <= monthlyUntilYear);
          const monthlyContrib = withinWindow ? (Number(p.monthly) || 0) * 12 : 0;
          pess = pess * (1 + (Number(p.ratePess) || 0)) + monthlyContrib - withdraw;
          corr = corr * (1 + (Number(p.rateCorr) || 0)) + monthlyContrib - withdraw;
          opti = opti * (1 + (Number(p.rateOpti) || 0)) + monthlyContrib - withdraw;
          return {
            year,
            pess,
            corr,
            opti
          };
        });
        return {
          label: p.label,
          rows
        };
      });
      const inflationRate = Number(data.settings.inflationRate) || 0;
      const totals = years.map((year, idx) => {
        const deflator = useConstantEuros ? Math.pow(1 / (1 + inflationRate), year - years[0]) : 1;
        return {
          year,
          pess: perPlacement.reduce((s, pp) => s + pp.rows[idx].pess, 0) * deflator,
          corr: perPlacement.reduce((s, pp) => s + pp.rows[idx].corr, 0) * deflator,
          opti: perPlacement.reduce((s, pp) => s + pp.rows[idx].opti, 0) * deflator
        };
      });
      return {
        perPlacement,
        totals
      };
    }, [data, years, useConstantEuros]);
    const previewYears = useMemo(() => years.slice(0, 4), [years]);
    const variablePreview = useMemo(() => {
      if (!data) return [];
      return (data.variableIncomes || []).map(v => {
        const refRow = (data.incomes || []).find(r => r.label === v.refIncomeLabel);
        const cells = previewYears.map(year => {
          if (year < Number(v.startYear) || year > Number(v.endYear)) return {
            year,
            amount: null,
            isReal: false
          };
          const refAnnual = refRow ? incomeAnnualForYear(refRow, year) : 0;
          const forecast = refAnnual * (Number(v.rate) || 0);
          const override = (data.variableOverrides || []).find(o => o.label === v.label && Number(o.year) === year);
          return {
            year,
            amount: override ? Number(override.amount) || 0 : forecast,
            isReal: !!override
          };
        });
        return {
          label: v.label,
          cells
        };
      });
    }, [data, previewYears]);
    const taxPreview = useMemo(() => taxYearly.slice(0, 4), [taxYearly]);
    return {
      years,
      taxYearly,
      cashflow,
      patrimoine,
      previewYears,
      variablePreview,
      taxPreview
    };
  }

  /* ============================== Moyennes Réelles (Pointage/Analyse) ============================== */
  function computeRealAverages(data) {
    const matchings = (data?.bankImport || {}).matchings || [];
    const transactions = (data?.bankImport || {}).transactions || [];
    const txById = {};
    transactions.forEach(t => {
      txById[t.id] = t;
    });
    const lineKindMap = {};
    (data?.charges || []).forEach(c => {
      lineKindMap[c.id] = "charge";
    });
    (data?.incomes || []).forEach(i => {
      lineKindMap[i.id] = "revenu";
    });
    (data?.placements || []).forEach(p => {
      lineKindMap[p.id] = "placement";
    });
    const byLine = {};
    matchings.forEach(m => {
      const {
        month,
        links
      } = m;
      (links || []).forEach(l => {
        if (!l.budgetLineId || !(l.txIds || []).length) return;
        const kind = lineKindMap[l.budgetLineId] || "charge";
        const realAmount = (l.txIds || []).reduce((s, txId) => {
          const tx = txById[txId];
          if (!tx) return s;
          const amt = Number(tx.amount) || 0;
          return s + (kind === "revenu" ? amt : -amt);
        }, 0);
        if (!byLine[l.budgetLineId]) byLine[l.budgetLineId] = [];
        byLine[l.budgetLineId].push({
          month,
          realAmount
        });
      });
    });
    const todayISO = new Date().toISOString().slice(0, 7);
    const result = {};
    Object.entries(byLine).forEach(([lineId, entries]) => {
      const sorted = [...entries].sort((a, b) => b.month.localeCompare(a.month));
      const last3 = sorted.filter(e => e.month <= todayISO).slice(0, 3);
      const last12 = sorted.filter(e => e.month <= todayISO).slice(0, 12);
      const avg3m = last3.length > 0 ? last3.reduce((s, e) => s + e.realAmount, 0) / last3.length : null;
      const avg12m = last12.length > 0 ? last12.reduce((s, e) => s + e.realAmount, 0) / last12.length : null;
      result[lineId] = {
        avg3m,
        avg12m,
        months: last12.length,
        monthsData: sorted
      };
    });
    return result;
  }

  // Helper to find earliest date in the dataset
  function getEarliestDate(data) {
    if (!data) return "2026-01-01";
    const dates = [];
    (data.incomes || []).forEach(i => { if (i.start) dates.push(i.start); });
    (data.charges || []).forEach(c => { if (c.start) dates.push(c.start); });
    (data.placements || []).forEach(p => {
      if (p.monthlyFrom) dates.push(p.monthlyFrom);
      if (p.balanceDate) dates.push(p.balanceDate);
    });
    (data.oneoff || []).forEach(o => { if (o.date) dates.push(o.date); });
    (data.transfers || []).forEach(t => { if (t.date) dates.push(t.date); });
    if (data.settings && data.settings.pivotDate) dates.push(data.settings.pivotDate);
    ((data.bankImport || {}).transactions || []).forEach(t => { if (t.date) dates.push(t.date); });
    const validDates = dates.filter(d => d && !isNaN(new Date(d).getTime())).sort();
    return validDates.length ? validDates[0] : "2026-01-01";
  }

  // Helper to find earliest year in the dataset
  function findEarliestYear(data) {
    if (!data) return 2026;
    const earliestISO = getEarliestDate(data);
    const y = new Date(earliestISO).getFullYear();
    return !isNaN(y) ? y : 2026;
  }

  exports.monthsActiveInYear = monthsActiveInYear;
  exports.incomeAnnualForYear = incomeAnnualForYear;
  exports.chargeEffectiveGrowth = chargeEffectiveGrowth;
  exports.chargeAnnualForYear = chargeAnnualForYear;
  exports.incomeMonthlyForYear = incomeMonthlyForYear;
  exports.chargeMonthlyForYear = chargeMonthlyForYear;
  exports.placementsMonthlyAnnualForYear = placementsMonthlyAnnualForYear;
  exports.partsForYear = partsForYear;
  exports.taxForOnePart = taxForOnePart;
  exports.variableIncomeDetailForYear = variableIncomeDetailForYear;
  exports.passForYear = passForYear;
  exports.agircPointValueForYear = agircPointValueForYear;
  exports.projectedAnnualSalary = projectedAnnualSalary;
  exports.nbEnfants = nbEnfants;
  exports.computeRetirementProjection = computeRetirementProjection;
  exports.pensionIncomeRows = pensionIncomeRows;
  exports.computePivotBalance = computePivotBalance;
  exports.latestTransactionDate = latestTransactionDate;
  exports.calculateDetailedFinancialTimeline = calculateDetailedFinancialTimeline;
  exports.projectPlacementBalanceAt = projectPlacementBalanceAt;
  exports.classifyAllocation = classifyAllocation;
  exports.useFinancialProjections = useFinancialProjections;
  exports.computeRealAverages = computeRealAverages;
  exports.getEarliestDate = getEarliestDate;
  exports.findEarliestYear = findEarliestYear;
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);