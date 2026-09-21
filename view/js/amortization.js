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

  /**
   * @param {object} p                   paramètres préparés (prepare)
   * @param {number|null} stepAbs        dernier mois à la mensualité actuelle (palier), ou null
   * @param {number} [firstInterestExtra] intérêts supplémentaires de la 1re échéance (recalage sur
   *                                     le relevé : à capital égal, la 1re période est souvent
   *                                     plus longue qu'un mois)
   * @param {{dateISO:string, amount:number, mode:string}|null} [event] remboursement anticipé
   *        partiel, appliqué à la 1re échéance dont la date est ≥ dateISO, après le paiement de
   *        celle-ci. mode « duree » : mensualité inchangée, prêt raccourci ; « mensualite » :
   *        durée inchangée, mensualité recalculée.
   */
  function amortize(p, stepAbs, firstInterestExtra, event) {
    const rows = [];
    const notes = [];
    let eventDone = false;
    let eventInfo = null;
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
      const interest = round2(balance * monthlyRate + (i === 0 && firstInterestExtra ? firstInterestExtra : 0));
      const isLast = p.count !== null && i === p.count - 1;
      let principal = round2(payment - interest);
      if (principal < 0) {
        if (!notes.includes('insuffisant')) notes.push('insuffisant');
        principal = 0;
      }
      if (isLast || principal > balance) principal = balance;
      const before = balance;
      balance = round2(balance - principal);
      const date = isoFromAbs(abs, p.day);
      let extraPrincipal = 0;
      if (event && !eventDone && date >= event.dateISO && balance > 0.004) {
        eventDone = true;
        extraPrincipal = Math.min(round2(event.amount), balance);
        balance = round2(balance - extraPrincipal);
        eventInfo = { index: i + 1, date, amount: extraPrincipal, mode: event.mode, balanceAfter: balance, payment: null };
        if (event.mode === 'mensualite' && p.count !== null && balance > 0.004 && p.count - (i + 1) > 0) {
          payment = round2(annuity(balance, p.rate, p.count - (i + 1)));
          eventInfo.payment = payment;
        }
      }
      rows.push({
        index: i + 1,
        number: numberFor(p, abs),
        abs,
        date,
        phase,
        balanceBefore: before,
        interest,
        principal,
        extraPrincipal,
        payment: round2(interest + principal),
        insurance: p.insurance,
        total: round2(interest + principal + p.insurance),
        balanceAfter: balance
      });
      if (principal === 0 && interest === 0) break;
    }
    return { rows, payment2, notes, event: eventInfo };
  }

  function sum(rows, key) {
    return round2(rows.reduce((s, r) => s + r[key], 0));
  }

  function yearlyRecap(rows) {
    const byYear = new Map();
    for (const r of rows) {
      const year = Number(r.date.slice(0, 4));
      if (!byYear.has(year)) {
        byYear.set(year, { year, count: 0, interest: 0, principal: 0, early: 0, insurance: 0, total: 0, balanceEnd: 0 });
      }
      const y = byYear.get(year);
      y.count += 1;
      y.interest += r.interest;
      y.principal += r.principal + r.extraPrincipal;
      y.early += r.extraPrincipal;
      y.insurance += r.insurance;
      y.total += r.total + r.extraPrincipal;
      y.balanceEnd = r.balanceAfter;
    }
    return Array.from(byYear.values()).map(y => ({
      ...y,
      interest: round2(y.interest),
      principal: round2(y.principal),
      early: round2(y.early),
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
    const tolerance = Math.max(25, declared * 0.001);
    const consistent = Math.abs(gap) <= tolerance;
    const check = {
      date: loan.startDate,
      declared: round2(declared),
      theoreticalBefore: before,
      theoreticalAfter: after,
      closest,
      theoretical,
      gap,
      consistent,
      totalInstallments: p.totalInstallments,
      calibrated: false,
      shift: null,
      message: null
    };
    if (!consistent) {
      check.shift = findShift(rows, abs, closest, declared, tolerance);
      check.message = referenceMessage(check);
    }
    return check;
  }

  /**
   * Le CRD déclaré correspond-il à une autre échéance du tableau (décalage de quelques mois entre
   * la date saisie et le rang réel : nombre d'échéances ou date de fin décalés) ?
   * Position d'un CRD = « après l'échéance du mois X » ; « avant l'échéance X » = « après X-1 ».
   */
  function findShift(rows, referenceAbs, closest, declared, tolerance) {
    const referencePosition = closest === 'after' ? referenceAbs : referenceAbs - 1;
    let best = null;
    for (const row of rows) {
      const months = row.abs - referencePosition;
      if (months === 0 || Math.abs(months) > 12) continue;
      const gap = Math.abs(declared - row.balanceAfter);
      if (gap <= tolerance && (!best || gap < best.gap)) {
        best = { months, gap, number: row.number, index: row.index, date: row.date, balance: row.balanceAfter };
      }
    }
    return best ? { ...best, gap: round2(best.gap) } : null;
  }

  function referenceMessage(check) {
    const declared = eurText(check.declared);
    if (check.shift) {
      const s = check.shift;
      const rank = s.number !== null ? `n° ${s.number}` : `n° ${s.index} du tableau`;
      const direction = s.months > 0 ? `${s.months} mois plus tard` : `${-s.months} mois plus tôt`;
      const reliquat = s.months === 1 && check.totalInstallments
        ? ` Si votre banque annonce ${check.totalInstallments} échéances mais une date de fin un mois plus tard, une dernière échéance de reliquat est probable : essayez ${check.totalInstallments + 1} échéances.`
        : '';
      return `Le CRD du relevé (${declared}) correspond à la situation après l'échéance du ${s.date.split('-').reverse().join('/')} (${rank}), soit ${direction} que la date saisie : le nombre d'échéances, la date de dernière échéance ou la date du relevé sont probablement décalés.${reliquat}`;
    }
    return `Le CRD du relevé (${declared}) s'écarte de ${eurText(Math.abs(check.gap))} du CRD théorique (${eurText(check.theoretical)}) : capital emprunté, taux, mensualité, nombre d'échéances ou date de fin à vérifier.`;
  }

  /**
   * Recale le tableau sur le CRD du relevé quand il s'en écarte légèrement.
   *
   * Un tableau reconstitué depuis le capital emprunté s'écarte souvent de quelques euros de celui de
   * la banque : la 1re période court en général de la mise à disposition des fonds à la 1re
   * échéance, soit plus (ou moins) d'un mois d'intérêts. L'écart est imputé aux intérêts de la
   * 1re échéance (mensualité inchangée), ce qui décale ensuite chaque CRD de cet écart capitalisé :
   * le tableau passe alors exactement par le CRD du relevé, et la dernière échéance (reliquat)
   * s'en déduit. Réservé aux écarts faibles : au-delà, c'est une erreur de saisie à corriger.
   *
   * @returns {{delta: number, result: object}|null}
   */
  function calibrateOnReference(loan, p, rows, check) {
    if (p.mode !== 'complet' || !check || !check.consistent) return null;
    const reference = parseDate(loan.startDate);
    const paymentsMade = monthAbs(reference) - rows[0].abs + (check.closest === 'after' ? 1 : 0);
    if (paymentsMade < 1 || paymentsMade > rows.length || rows[paymentsMade - 1].phase !== 1) return null;
    const declared = check.declared;
    const growth = Math.pow(1 + p.rate / 12, paymentsMade - 1);
    const limit = Math.max(50, p.balance0 * 0.002);
    let delta = 0;
    let result = { rows, payment2: null, notes: [] };
    let gap = check.gap;
    for (let attempt = 0; attempt < 4 && Math.abs(gap) >= 0.005; attempt++) {
      delta += gap / growth;
      if (Math.abs(delta) > limit) return null;
      result = amortize(p, p.stepAbs, delta);
      if (result.rows.length < paymentsMade) return null;
      gap = round2(declared - result.rows[paymentsMade - 1].balanceAfter);
    }
    return Math.abs(delta) < 0.005 ? null : { delta: round2(delta), result };
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
    const early = normalizeEarlyRepayment(options && options.earlyRepayment);
    const forcedExtra = options && typeof options.firstInterestExtra === 'number' ? options.firstInterestExtra : null;
    let { rows, payment2, notes, event } = amortize(p, p.stepAbs, forcedExtra || 0, early);
    const warnings = p.warnings.slice();
    if (early && !event) {
      warnings.push('Le remboursement anticipé simulé est sans effet : sa date est postérieure à la dernière échéance ou le capital est déjà soldé.');
    }
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
    if (!early && p.count !== null && rows.length < p.count && last.balanceAfter <= 0.004) {
      warnings.push(`Le capital est soldé dès l'échéance ${last.number || last.index} sur ${p.count} : la mensualité saisie est supérieure à celle qu'exige la durée.`);
    }
    const theoreticalPayment = p.count !== null ? round2(annuity(p.balance0, p.rate, p.count)) : null;
    if (!early && p.stepAbs === null && !p.paymentComputed && theoreticalPayment !== null
        && Math.abs(p.payment - theoreticalPayment) > Math.max(2, theoreticalPayment * 0.01)) {
      warnings.push(`La mensualité saisie hors assurance (${eurText(p.payment)}) s'écarte de la mensualité théorique de ce prêt (${eurText(theoreticalPayment)} pour ${p.count} échéances au taux saisi) : dernière échéance ajustée à ${eurText(last.payment)}. Vérifiez le taux, le capital ou une éventuelle mensualité lissée.`);
    }

    // Le contrôle du relevé (referenceCheck, avec son message) est présenté à part par l'écran et
    // le rapport : il n'est volontairement pas dupliqué dans `warnings`.
    // Une simulation (remboursement anticipé) ne se recale pas et ne se contrôle pas sur le relevé.
    let referenceCheck = early || forcedExtra !== null ? null : checkReference(loan, p, rows);
    let calibration = null;
    const calibrated = early || forcedExtra !== null ? null : calibrateOnReference(loan, p, rows, referenceCheck);
    if (calibrated) {
      calibration = { adjustment: calibrated.delta, gapBefore: referenceCheck.gap };
      rows = calibrated.result.rows;
      payment2 = calibrated.result.payment2;
      referenceCheck = { ...checkReference(loan, p, rows), calibrated: true };
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
      totalPrincipal: round2(sum(rows, 'principal') + sum(rows, 'extraPrincipal')),
      totalEarly: sum(rows, 'extraPrincipal'),
      earlyRepayment: event,
      totalInsurance: sum(rows, 'insurance'),
      totalPayments: sum(rows, 'payment'),
      totalPaid: round2(sum(rows, 'total') + sum(rows, 'extraPrincipal')),
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
      referenceCheck,
      calibration
    };
    return { ok: true, mode: p.mode, rows, yearly: yearlyRecap(rows), summary, warnings };
  }

  /** Demande de remboursement anticipé -> événement pour amortize, ou null si inexploitable. */
  function normalizeEarlyRepayment(request) {
    if (!request) return null;
    const amount = num(request.amount);
    const date = parseDate(request.date);
    if (!(amount > 0) || !date) return null;
    return {
      dateISO: isoFromAbs(monthAbs(date), date.d),
      amount,
      mode: request.mode === 'mensualite' ? 'mensualite' : 'duree'
    };
  }

  /**
   * Simule un remboursement anticipé PARTIEL et le compare au tableau sans remboursement.
   *
   * Le montant est appliqué à la 1re échéance dont la date est ≥ à la date demandée, après le
   * paiement de celle-ci. Mode « duree » : mensualité inchangée, prêt raccourci. Mode « mensualite » :
   * durée inchangée, mensualité recalculée. L'assurance est supposée constante (elle baisse en
   * pratique avec le CRD sur certains contrats : l'économie d'assurance est alors sous-estimée).
   * L'indemnité est estimée au moindre de 6 mois d'intérêts et 3 % du capital remboursé, sauf si
   * `request.indemnity` est fournie.
   *
   * @param {object} loan
   * @param {{date:string, amount:number, mode?:'duree'|'mensualite', indemnity?:number|null}} request
   * @param {object} [options]  { today }
   */
  function simulateEarlyRepayment(loan, request, options) {
    const base = buildSchedule(loan, options);
    if (!base.ok) return { ok: false, reason: base.reason };
    if (!(num(request && request.amount) > 0)) {
      return { ok: false, reason: 'Indiquez le montant remboursé par anticipation.' };
    }
    if (!parseDate(request && request.date)) {
      return { ok: false, reason: 'Indiquez la date du remboursement anticipé.' };
    }
    const delta = base.summary.calibration ? base.summary.calibration.adjustment : 0;
    const after = buildSchedule(loan, { ...(options || {}), earlyRepayment: request, firstInterestExtra: delta });
    if (!after.ok) return { ok: false, reason: after.reason };
    const event = after.summary.earlyRepayment;
    if (!event) {
      return { ok: false, reason: 'Aucune échéance à cette date ou après : le remboursement anticipé serait sans effet.' };
    }
    const rate = num(loan.rate);
    const estimatedIndemnity = round2(event.amount * Math.min(rate / 2, 0.03));
    const customIndemnity = request.indemnity !== null && request.indemnity !== undefined && request.indemnity !== ''
      && Number.isFinite(Number(request.indemnity)) && Number(request.indemnity) >= 0;
    const indemnity = customIndemnity ? round2(Number(request.indemnity)) : estimatedIndemnity;
    const interestSaved = round2(base.summary.totalInterest - after.summary.totalInterest);
    const insuranceSaved = round2(base.summary.totalInsurance - after.summary.totalInsurance);
    const nextBefore = base.rows[event.index] || null;
    const nextAfter = after.rows[event.index] || null;
    return {
      ok: true,
      base,
      after,
      comparison: {
        applyDate: event.date,
        installmentIndex: event.index,
        amount: event.amount,
        cappedAmount: event.amount < round2(num(request.amount)),
        mode: event.mode,
        balanceAfterEvent: event.balanceAfter,
        indemnity,
        indemnityEstimated: !customIndemnity,
        interestSaved,
        insuranceSaved,
        monthsSaved: base.summary.count - after.summary.count,
        lastDateBefore: base.summary.lastDate,
        lastDateAfter: after.summary.lastDate,
        paymentBefore: nextBefore ? nextBefore.payment : null,
        paymentAfter: nextAfter ? nextAfter.payment : null,
        netGain: round2(interestSaved + insuranceSaved - indemnity)
      }
    };
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
    simulateEarlyRepayment,
    annuity,
    parseDate,
    round2
  };
})(typeof window !== 'undefined' ? (window.BudgetApp = window.BudgetApp || {}) : module.exports);
