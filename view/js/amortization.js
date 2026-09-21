/**
 * Moteur de tableau d'amortissement d'un prêt (fonctions pures, sans React ni DOM).
 *
 * Deux modes, choisis automatiquement selon ce qui est renseigné sur le prêt :
 *  - « complet »  : capital emprunté + nombre d'échéances + date de dernière échéance. Le tableau
 *                   part de la 1re échéance (les échéances déjà payées apparaissent) ;
 *  - « restant »  : à défaut, CRD + date du dernier relevé. Échéancier des seules échéances à venir
 *                   (même convention que calculations.js#projectLoanCrdToDate : le mois du relevé
 *                   est inclus, le CRD est donc considéré avant l'échéance de ce mois).
 *
 * Hypothèses (rappelées sur le PDF) : taux fixe, intérêts du mois = CRD × taux / 12 arrondis au
 * centime, assurance constante, dernière échéance ajustée pour solder le capital.
 *
 * Mensualité lissée (« palier ») : si `stepDate` est renseignée, la mensualité actuelle vaut
 * jusqu'à cette date incluse ; ensuite elle est recalculée pour amortir le solde restant sur les
 * échéances restantes (c'est ce que fait la banque quand un prêt lissé avec un autre se termine).
 */
(function (exports) {
  'use strict';

  const MAX_ROWS = 720; // garde-fou : 60 ans

  // ---------------------------------------------------------------------------
  // Utilitaires
  // ---------------------------------------------------------------------------

  function round2(v) {
    return Math.round(v * 100) / 100;
  }
  function num(v) {
    const n = Number(v);
    return Number.isFinite(n) ? n : 0;
  }
  const eurFormat = new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' });
  function eurText(v) {
    return eurFormat.format(v);
  }
  function pad2(n) {
    return String(n).padStart(2, '0');
  }

  /** « 2026-05-17 » (ou « 2026-05 ») → { y, m (0-11), d } ; null si invalide. */
  function parseDate(text) {
    if (!text) return null;
    const match = /^(\d{4})-(\d{2})(?:-(\d{2}))?/.exec(String(text).trim());
    if (!match) return null;
    const m = Number(match[2]) - 1;
    if (m < 0 || m > 11) return null;
    return { y: Number(match[1]), m, d: match[3] ? Number(match[3]) : 1 };
  }
  function monthAbs(p) {
    return p.y * 12 + p.m;
  }
  function daysInMonth(y, m) {
    return new Date(Date.UTC(y, m + 1, 0)).getUTCDate();
  }
  /** Mois absolu + jour souhaité → « AAAA-MM-JJ » (jour ramené à la fin du mois si besoin). */
  function isoFromAbs(abs, day) {
    const y = Math.floor(abs / 12);
    const m = abs - y * 12;
    return `${y}-${pad2(m + 1)}-${pad2(Math.min(day, daysInMonth(y, m)))}`;
  }
  function todayISO() {
    const now = new Date();
    return `${now.getFullYear()}-${pad2(now.getMonth() + 1)}-${pad2(now.getDate())}`;
  }

  /** Mensualité (hors assurance) d'un prêt à annuités constantes. */
  function annuity(principal, annualRate, months) {
    if (!(months > 0)) return principal;
    const r = annualRate / 12;
    if (Math.abs(r) < 1e-12) return principal / months;
    return principal * r / (1 - Math.pow(1 + r, -months));
  }

  // ---------------------------------------------------------------------------
  // Préparation : que sait-on du prêt ?
  // ---------------------------------------------------------------------------

  function prepare(loan) {
    const rate = num(loan && loan.rate);
    const insurance = num(loan && loan.insurance);
    const monthly = num(loan && loan.monthly);
    const initialAmount = num(loan && loan.initialAmount);
    const totalInstallments = Math.trunc(num(loan && loan.totalInstallments));
    const crd = num(loan && loan.crd);
    const end = parseDate(loan && loan.endDate);
    const reference = parseDate(loan && loan.startDate);
    const step = parseDate(loan && loan.stepDate);
    const warnings = [];
    const netMonthly = monthly > 0 ? Math.max(0, monthly - insurance) : 0;

    let mode;
    let balance0;
    let firstAbs;
    let count;
    let day;
    if (initialAmount > 0 && totalInstallments > 0 && end) {
      mode = 'complet';
      balance0 = initialAmount;
      count = totalInstallments;
      firstAbs = monthAbs(end) - (count - 1);
      day = end.d;
    } else if (crd > 0 && reference) {
      mode = 'restant';
      balance0 = crd;
      firstAbs = monthAbs(reference);
      day = end ? end.d : reference.d;
      count = end ? monthAbs(end) - firstAbs + 1 : null;
      if (count !== null && count <= 0) {
        return { ok: false, reason: 'Ce prêt est arrivé à échéance : rien à amortir.' };
      }
      warnings.push('Capital emprunté ou nombre d\'échéances non renseigné : échéancier des seules échéances à venir, calculé depuis le CRD du dernier relevé.');
    } else {
      return {
        ok: false,
        reason: 'Renseignez le capital emprunté, le nombre d\'échéances et la date de dernière échéance (ou, à défaut, le CRD et la date du dernier relevé).'
      };
    }

    const lastAbs = count !== null ? firstAbs + count - 1 : null;
    let payment = netMonthly;
    let paymentComputed = false;
    if (!(payment > 0)) {
      if (count === null) {
        return { ok: false, reason: 'Renseignez la mensualité (ou la date de dernière échéance pour la calculer).' };
      }
      payment = round2(annuity(balance0, rate, count));
      paymentComputed = true;
      warnings.push('Mensualité non renseignée : mensualité théorique calculée à partir du taux et de la durée.');
    }

    let stepAbs = null;
    if (step) {
      const candidate = monthAbs(step);
      if (count !== null && candidate >= firstAbs && candidate < lastAbs) {
        stepAbs = candidate;
      } else {
        warnings.push('La fin de la mensualité lissée est hors de la durée du prêt : elle est ignorée.');
      }
    }

    return {
      ok: true,
      mode,
      rate,
      insurance,
      balance0,
      firstAbs,
      lastAbs,
      count,
      day,
      payment,
      paymentComputed,
      stepAbs,
      totalInstallments: totalInstallments > 0 ? totalInstallments : null,
      warnings
    };
  }

  // ---------------------------------------------------------------------------
  // Amortissement
  // ---------------------------------------------------------------------------

  /** Numéro d'échéance affichable (ex. 98 sur 120), quand le nombre total est connu. */
  function numberFor(p, abs) {
    if (p.totalInstallments === null || p.lastAbs === null) return null;
    return p.totalInstallments - (p.lastAbs - abs);
  }

  function amortize(p, stepAbs) {
    const rows = [];
    const notes = [];
    const monthlyRate = p.rate / 12;
    const limit = p.count !== null ? Math.min(p.count, MAX_ROWS) : MAX_ROWS;
    let balance = p.balance0;
    let payment = p.payment;
    let phase = 1;
    let payment2 = null;

    for (let i = 0; i < limit && balance > 0.004; i++) {
      const abs = p.firstAbs + i;
      if (stepAbs !== null && stepAbs !== undefined && phase === 1 && abs > stepAbs) {
        payment = round2(annuity(balance, p.rate, p.count - i));
        payment2 = payment;
        phase = 2;
      }
      const interest = round2(balance * monthlyRate);
      const isLast = p.count !== null && i === p.count - 1;
      let principal = round2(payment - interest);
      if (principal < 0) {
        if (!notes.includes('insuffisant')) notes.push('insuffisant');
        principal = 0;
      }
      if (isLast || principal > balance) principal = balance;
      const before = balance;
      balance = round2(balance - principal);
      rows.push({
        index: i + 1,
        number: numberFor(p, abs),
        abs,
        date: isoFromAbs(abs, p.day),
        phase,
        balanceBefore: before,
        interest,
        principal,
        payment: round2(interest + principal),
        insurance: p.insurance,
        total: round2(interest + principal + p.insurance),
        balanceAfter: balance
      });
      if (principal === 0 && interest === 0) break;
    }
    return { rows, payment2, notes };
  }

  function sum(rows, key) {
    return round2(rows.reduce((s, r) => s + r[key], 0));
  }

  function yearlyRecap(rows) {
    const byYear = new Map();
    for (const r of rows) {
      const year = Number(r.date.slice(0, 4));
      if (!byYear.has(year)) {
        byYear.set(year, { year, count: 0, interest: 0, principal: 0, insurance: 0, total: 0, balanceEnd: 0 });
      }
      const y = byYear.get(year);
      y.count += 1;
      y.interest += r.interest;
      y.principal += r.principal;
      y.insurance += r.insurance;
      y.total += r.total;
      y.balanceEnd = r.balanceAfter;
    }
    return Array.from(byYear.values()).map(y => ({
      ...y,
      interest: round2(y.interest),
      principal: round2(y.principal),
      insurance: round2(y.insurance),
      total: round2(y.total)
    }));
  }

  /** Compare le CRD du dernier relevé au CRD théorique du mois du relevé (avant / après échéance). */
  function checkReference(loan, p, rows) {
    const declared = num(loan.crd);
    const reference = parseDate(loan.startDate);
    if (p.mode !== 'complet' || !(declared > 0) || !reference || rows.length === 0) return null;
    const abs = monthAbs(reference);
    let before;
    let after;
    if (abs < rows[0].abs) {
      before = after = rows[0].balanceBefore;
    } else if (abs > rows[rows.length - 1].abs) {
      before = after = 0;
    } else {
      const row = rows.find(r => r.abs === abs);
      before = row.balanceBefore;
      after = row.balanceAfter;
    }
    const closest = Math.abs(declared - before) <= Math.abs(declared - after) ? 'before' : 'after';
    const theoretical = closest === 'before' ? before : after;
    const gap = round2(declared - theoretical);
    return {
      date: loan.startDate,
      declared: round2(declared),
      theoreticalBefore: before,
      theoreticalAfter: after,
      closest,
      theoretical,
      gap,
      consistent: Math.abs(gap) <= Math.max(25, declared * 0.001)
    };
  }

  /**
   * Construit le tableau d'amortissement d'un prêt.
   *
   * @param {object} loan  { initialAmount, totalInstallments, rate (fraction), monthly (assurance
   *                       incluse), insurance (€/mois), endDate, crd, startDate (date du relevé),
   *                       stepDate (dernière échéance à la mensualité actuelle) }
   * @param {object} [options]  { today: 'AAAA-MM-JJ' }
   * @returns {{ok: boolean, reason?: string, mode?: string, rows?: object[], yearly?: object[],
   *            summary?: object, warnings?: string[]}}
   */
  function buildSchedule(loan, options) {
    const p = prepare(loan);
    if (!p.ok) return p;
    const today = (options && options.today) || todayISO();
    const { rows, payment2, notes } = amortize(p, p.stepAbs);
    const warnings = p.warnings.slice();
    if (rows.length === 0) {
      return { ok: false, reason: 'Aucune échéance à afficher : capital nul ou déjà soldé.' };
    }

    if (notes.includes('insuffisant')) {
      warnings.push('La mensualité est inférieure aux intérêts du mois : le capital n\'est pas amorti (mensualité à vérifier).');
    }
    const last = rows[rows.length - 1];
    if (p.count === null && last.balanceAfter > 0.004) {
      warnings.push('Le prêt n\'est pas soldé au bout de 60 ans : mensualité insuffisante ou date de fin manquante.');
    }
    if (p.count !== null && rows.length < p.count && last.balanceAfter <= 0.004) {
      warnings.push(`Le capital est soldé dès l'échéance ${last.number || last.index} sur ${p.count} : la mensualité saisie est supérieure à celle qu'exige la durée.`);
    }
    const theoreticalPayment = p.count !== null ? round2(annuity(p.balance0, p.rate, p.count)) : null;
    if (p.stepAbs === null && !p.paymentComputed && theoreticalPayment !== null
        && Math.abs(p.payment - theoreticalPayment) > Math.max(2, theoreticalPayment * 0.01)) {
      warnings.push(`La mensualité saisie hors assurance (${eurText(p.payment)}) s'écarte de la mensualité théorique de ce prêt (${eurText(theoreticalPayment)} pour ${p.count} échéances au taux saisi) : dernière échéance ajustée à ${eurText(last.payment)}. Vérifiez le taux, le capital ou une éventuelle mensualité lissée.`);
    }

    const referenceCheck = checkReference(loan, p, rows);
    if (referenceCheck && !referenceCheck.consistent) {
      warnings.push(`Le CRD du relevé (${eurText(referenceCheck.declared)}) s'écarte de ${eurText(Math.abs(referenceCheck.gap))} du CRD théorique : capital emprunté, taux, mensualité ou nombre d'échéances à vérifier.`);
    }

    const paidCount = rows.filter(r => r.date <= today).length;
    const next = rows[paidCount] || null;
    const future = rows.slice(paidCount);
    const summary = {
      mode: p.mode,
      count: rows.length,
      totalInstallments: p.totalInstallments,
      firstDate: rows[0].date,
      lastDate: last.date,
      initialBalance: p.balance0,
      totalInterest: sum(rows, 'interest'),
      totalPrincipal: sum(rows, 'principal'),
      totalInsurance: sum(rows, 'insurance'),
      totalPayments: sum(rows, 'payment'),
      totalPaid: sum(rows, 'total'),
      payment1: p.payment,
      payment2,
      paymentComputed: p.paymentComputed,
      stepDate: payment2 !== null ? isoFromAbs(p.stepAbs, p.day) : null,
      theoreticalPayment,
      paidCount,
      nextIndex: next ? next.index : null,
      nextNumber: next ? next.number : null,
      nextDate: next ? next.date : null,
      balanceToday: paidCount > 0 ? rows[paidCount - 1].balanceAfter : rows[0].balanceBefore,
      remainingInterest: sum(future, 'interest'),
      remainingInsurance: sum(future, 'insurance'),
      referenceCheck
    };
    return { ok: true, mode: p.mode, rows, yearly: yearlyRecap(rows), summary, warnings };
  }

  /**
   * Cherche la dernière échéance à la mensualité actuelle telle que, ensuite, la nouvelle mensualité
   * (hors assurance) soit égale à l'actuelle + la mensualité (hors assurance) du prêt avec lequel
   * celui-ci est lissé : un lissage maintient la somme des mensualités constante.
   *
   * @param {object} loan             le prêt lissé (sans palier ou avec : il est ignoré ici)
   * @param {number} otherNetMonthly  mensualité hors assurance de l'autre prêt
   * @returns {{stepDate: string, payment2: number, target: number, gap: number}|null}
   */
  function estimateStep(loan, otherNetMonthly) {
    const p = prepare({ ...loan, stepDate: null });
    if (!p.ok || p.count === null || !(otherNetMonthly > 0)) return null;
    const target = round2(p.payment + otherNetMonthly);
    let best = null;
    for (let abs = p.firstAbs; abs < p.lastAbs; abs++) {
      const { payment2 } = amortize(p, abs);
      if (payment2 === null) continue;
      const gap = Math.abs(payment2 - target);
      if (!best || gap < best.gap) best = { abs, payment2, gap };
    }
    if (!best) return null;
    return {
      stepDate: isoFromAbs(best.abs, p.day),
      payment2: best.payment2,
      target,
      gap: round2(best.gap)
    };
  }

  /** Mensualité qui suivrait une fin de palier donnée (aperçu dans le formulaire). */
  function previewStep(loan) {
    const p = prepare(loan);
    if (!p.ok || p.stepAbs === null || p.count === null) return null;
    const { payment2 } = amortize(p, p.stepAbs);
    return payment2 === null ? null : { payment1: p.payment, payment2 };
  }

  exports.Amortization = {
    buildSchedule,
    estimateStep,
    previewStep,
    annuity,
    parseDate,
    round2
  };
})(typeof window !== 'undefined' ? (window.BudgetApp = window.BudgetApp || {}) : module.exports);
