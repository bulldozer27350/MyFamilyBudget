/**
 * Tests du moteur de tableau d'amortissement (view/js/amortization.js).
 * Lancement : node view/scratch/test-amortization.js
 */
const assert = require('assert');
const path = require('path');

const { Amortization } = require(path.join(__dirname, '..', 'js', 'amortization.js'));
const { projectLoanCrdToDate } = require(path.join(__dirname, '..', 'js', 'calculations.js'));
const { buildAmortizationHTML } = require(path.join(__dirname, '..', 'js', 'components', 'amortization-report.js'));
const { buildSchedule, estimateStep, previewStep, annuity, simulateEarlyRepayment } = Amortization;

let passed = 0;
function test(name, fn) {
  try {
    fn();
    passed += 1;
    console.log('✓ ' + name);
  } catch (e) {
    console.error('✗ ' + name);
    throw e;
  }
}
function near(actual, expected, tolerance, message) {
  assert.ok(Math.abs(actual - expected) <= tolerance, `${message || ''} attendu ≈ ${expected}, obtenu ${actual}`);
}

const TODAY = '2026-09-21';
const classic = {
  initialAmount: 200000,
  totalInstallments: 240,
  rate: 0.03,
  monthly: 1109.2 + 30,
  insurance: 30,
  endDate: '2046-01-15'
};

test('annuité : 200 000 € à 3 % sur 240 mois = 1 109,20 €', () => {
  near(annuity(200000, 0.03, 240), 1109.2, 0.01);
});

test('prêt classique : dates, première ligne et solde final', () => {
  const s = buildSchedule(classic, { today: TODAY });
  assert.ok(s.ok, s.reason);
  assert.strictEqual(s.mode, 'complet');
  assert.strictEqual(s.rows.length, 240);
  assert.strictEqual(s.rows[0].date, '2026-02-15');
  assert.strictEqual(s.rows[239].date, '2046-01-15');
  assert.strictEqual(s.rows[0].number, 1);
  assert.strictEqual(s.rows[239].number, 240);
  assert.strictEqual(s.rows[0].interest, 500);
  near(s.rows[0].principal, 609.2, 0.01);
  assert.strictEqual(s.rows[239].balanceAfter, 0);
  near(s.summary.totalInterest, 240 * 1109.2 - 200000, 15, 'intérêts totaux');
  assert.strictEqual(s.summary.totalInsurance, 240 * 30);
});

test('prêt classique : le capital amorti total vaut le capital emprunté', () => {
  const s = buildSchedule(classic, { today: TODAY });
  near(s.summary.totalPrincipal, 200000, 0.01);
  near(s.summary.totalPaid, s.summary.totalPayments + s.summary.totalInsurance, 0.01);
});

test('prêt classique : échéance en cours et CRD du jour', () => {
  const s = buildSchedule(classic, { today: TODAY });
  // 2026-02 -> 2026-09 : 8 échéances payées (la 8e le 15/09), la 9e est la suivante.
  assert.strictEqual(s.summary.paidCount, 8);
  assert.strictEqual(s.summary.nextNumber, 9);
  assert.strictEqual(s.summary.nextDate, '2026-10-15');
  assert.strictEqual(s.summary.balanceToday, s.rows[7].balanceAfter);
});

test('mensualité absente : mensualité théorique calculée, avec avertissement', () => {
  const s = buildSchedule({ ...classic, monthly: 0, insurance: 0 }, { today: TODAY });
  assert.ok(s.ok);
  assert.strictEqual(s.summary.paymentComputed, true);
  near(s.summary.payment1, 1109.2, 0.01);
  assert.ok(s.warnings.some(w => w.includes('Mensualité non renseignée')));
});

test('mensualité saisie incohérente avec la durée : avertissement et dernière échéance ajustée', () => {
  const s = buildSchedule({ ...classic, monthly: 1000 + 30 }, { today: TODAY });
  assert.ok(s.warnings.some(w => w.includes('s\'écarte de la mensualité théorique')));
  assert.strictEqual(s.rows[s.rows.length - 1].balanceAfter, 0);
});

test('taux à 0 % : capital / durée', () => {
  const s = buildSchedule({ initialAmount: 12000, totalInstallments: 12, rate: 0, monthly: 1000, insurance: 0, endDate: '2027-06-01' }, { today: TODAY });
  assert.strictEqual(s.rows.length, 12);
  assert.ok(s.rows.every(r => r.interest === 0));
  assert.strictEqual(s.summary.totalInterest, 0);
});

test('jour de prélèvement en fin de mois : ramené au dernier jour des mois courts', () => {
  const s = buildSchedule({ initialAmount: 3000, totalInstallments: 3, rate: 0.02, monthly: 1000, insurance: 0, endDate: '2026-04-30' }, { today: '2026-01-01' });
  assert.deepStrictEqual(s.rows.map(r => r.date), ['2026-02-28', '2026-03-30', '2026-04-30']);
});

test('relevé cohérent : écart faible avec le CRD théorique', () => {
  const full = buildSchedule(classic, { today: TODAY });
  const row = full.rows[5]; // juillet 2026
  const s = buildSchedule({ ...classic, crd: row.balanceAfter + 3.2, startDate: row.date }, { today: TODAY });
  assert.ok(s.summary.referenceCheck.consistent);
  assert.strictEqual(s.summary.referenceCheck.closest, 'after');
  // Écart faible : le tableau est recalé sur le relevé (intérêts de la 1re échéance ajustés).
  assert.strictEqual(s.summary.referenceCheck.calibrated, true);
  near(s.summary.calibration.gapBefore, 3.2, 0.01);
  near(s.summary.calibration.adjustment, 3.2 / Math.pow(1 + classic.rate / 12, 5), 0.05);
  near(s.summary.referenceCheck.gap, 0, 0.005);
  near(s.rows[5].balanceAfter, row.balanceAfter + 3.2, 0.005);
  assert.ok(!s.warnings.some(w => w.includes('CRD du relevé')));
});

test('recalage : un relevé exact ne modifie pas le tableau', () => {
  const full = buildSchedule(classic, { today: TODAY });
  const s = buildSchedule({ ...classic, crd: full.rows[5].balanceAfter, startDate: full.rows[5].date }, { today: TODAY });
  assert.strictEqual(s.summary.calibration, null);
  assert.strictEqual(s.rows[0].interest, full.rows[0].interest);
});

test('recalage : reproduit un tableau de banque à première période longue et reliquat final', () => {
  // « Banque » simulée : 50 000 € à 0,75 %, 120 échéances régulières dès le 05/11/2020, intérêts de la
  // 1re échéance majorés de 8,14 € (période plus longue), puis une dernière échéance de reliquat.
  const rate = 0.0075;
  const insurance = 10.5;
  const payment = Math.round(annuity(50000, rate, 120) * 100) / 100;
  const bank = [];
  let balance = 50000;
  for (let i = 0; i < 121 && balance > 0.004; i++) {
    const interest = Math.round((balance * rate / 12 + (i === 0 ? 8.14 : 0)) * 100) / 100;
    const principal = i === 120 ? balance : Math.min(balance, Math.round((payment - interest) * 100) / 100);
    const before = balance;
    balance = Math.round((balance - principal) * 100) / 100;
    bank.push({ interest, principal, balanceBefore: before, balanceAfter: balance });
  }
  assert.strictEqual(bank.length, 121);
  const reliquat = bank[120].interest + bank[120].principal;
  assert.ok(reliquat > 1 && reliquat < 30, 'reliquat final plausible : ' + reliquat);

  // Relevé : CRD juste avant la 72e échéance (05/10/2026), tel que la banque l'affiche.
  const loan = { initialAmount: 50000, totalInstallments: 121, rate, monthly: payment + insurance, insurance, endDate: '2030-11-05', crd: bank[71].balanceBefore, startDate: '2026-10-05' };
  const s = buildSchedule(loan, { today: TODAY });
  assert.strictEqual(s.rows.length, 121);
  near(s.summary.calibration.adjustment, 8.14, 0.06, 'écart de 1re période retrouvé');
  bank.forEach((b, i) => {
    near(s.rows[i].balanceAfter, b.balanceAfter, 0.06, 'CRD échéance ' + (i + 1));
  });
  near(s.rows[120].payment, reliquat, 0.06, 'reliquat final');
  near(s.rows[71].interest, bank[71].interest, 0.02, 'intérêts de l\'échéance du relevé');
});

test('recalage : un écart trop grand n\'est pas absorbé (erreur de saisie à corriger)', () => {
  const s = buildSchedule({ ...classic, crd: buildSchedule(classic, { today: TODAY }).rows[5].balanceAfter + 900, startDate: '2026-07-15' }, { today: TODAY });
  assert.strictEqual(s.summary.calibration, null);
  assert.strictEqual(s.summary.referenceCheck.consistent, false);
});

test('décalage de rang : suggestion d\'une échéance de reliquat quand le décalage est d\'un mois', () => {
  const loan = { initialAmount: 50000, totalInstallments: 120, rate: 0.0075, monthly: 0, insurance: 10, endDate: '2030-11-05' };
  const table = buildSchedule(loan, { today: TODAY });
  const r71 = table.rows.find(r => r.date === '2026-10-05');
  const r72 = table.rows.find(r => r.date === '2026-11-05');
  const s = buildSchedule({ ...loan, crd: r72.balanceAfter, startDate: r71.date }, { today: TODAY });
  assert.ok(s.summary.referenceCheck.message.includes('essayez 121 échéances'));
});

test('relevé incohérent : message dans le contrôle, pas de doublon dans les avertissements', () => {
  const s = buildSchedule({ ...classic, crd: 150000, startDate: '2026-07-01' }, { today: TODAY });
  assert.strictEqual(s.summary.referenceCheck.consistent, false);
  assert.ok(s.summary.referenceCheck.message.includes('CRD du relevé'));
  assert.strictEqual(s.summary.referenceCheck.shift, null);
  assert.ok(!s.warnings.some(w => w.includes('CRD du relevé')));
});

test('relevé décalé d\'un mois : le décalage est diagnostiqué', () => {
  // Prêt de 50 000 € sur 120 échéances se terminant le 05/11/2030 : la 71e échéance est au 05/10/2026.
  const loan = { initialAmount: 50000, totalInstallments: 120, rate: 0.0075, monthly: 0, insurance: 10, endDate: '2030-11-05' };
  const table = buildSchedule(loan, { today: TODAY });
  const row71 = table.rows.find(r => r.date === '2026-10-05');
  const row72 = table.rows.find(r => r.date === '2026-11-05');
  // CRD saisi = situation après la 72e échéance, mais daté du 05/10/2026 (71e).
  const s = buildSchedule({ ...loan, crd: row72.balanceAfter + 8.5, startDate: row71.date }, { today: TODAY });
  const check = s.summary.referenceCheck;
  assert.strictEqual(check.consistent, false);
  assert.ok(check.shift, 'décalage détecté');
  assert.strictEqual(check.shift.months, 1);
  assert.strictEqual(check.shift.date, '2026-11-05');
  assert.strictEqual(check.shift.number, 72);
  assert.ok(check.message.includes('1 mois plus tard'));
});

test('relevé cohérent avec le tableau : pas de message ni de décalage', () => {
  const s = buildSchedule({ ...classic, crd: 100000, startDate: '2026-07-01' }, { today: TODAY });
  const ok = buildSchedule({ ...classic, crd: s.rows[5].balanceAfter, startDate: s.rows[5].date }, { today: TODAY });
  assert.strictEqual(ok.summary.referenceCheck.consistent, true);
  assert.strictEqual(ok.summary.referenceCheck.message, null);
  assert.strictEqual(ok.summary.referenceCheck.shift, null);
});

test('mode restant : échéancier depuis le CRD du relevé, mois du relevé inclus', () => {
  const net = Math.round(annuity(100000, 0.025, 121) * 100) / 100;
  const s = buildSchedule({ crd: 100000, startDate: '2026-09-01', rate: 0.025, monthly: net + 20, insurance: 20, endDate: '2036-09-01' }, { today: TODAY });
  assert.ok(s.ok, s.reason);
  assert.strictEqual(s.mode, 'restant');
  assert.strictEqual(s.rows.length, 121);
  assert.strictEqual(s.rows[0].date, '2026-09-01');
  assert.strictEqual(s.rows[0].number, null);
  assert.strictEqual(s.rows[0].balanceBefore, 100000);
  assert.strictEqual(s.rows[s.rows.length - 1].balanceAfter, 0);
});

test('mode restant : cohérent avec calculations.js#projectLoanCrdToDate (à quelques euros près)', () => {
  // Reproduit la boucle de calculations.js (intérêts non arrondis) pour vérifier l'équivalence.
  const loan = { crd: 100000, startDate: '2026-09-01', rate: 0.025, monthly: Math.round(annuity(100000, 0.025, 241) * 100) / 100 + 20, insurance: 20, endDate: '2046-09-01' };
  const s = buildSchedule(loan, { today: TODAY });
  let crd = loan.crd;
  const rate = loan.rate;
  const net = loan.monthly - loan.insurance;
  const balances = [];
  for (let i = 0; i < 60; i++) {
    const interest = crd * (rate / 12);
    crd = Math.max(0, crd - Math.min(crd, net - interest));
    balances.push(crd);
  }
  near(s.rows[59].balanceAfter, balances[59], 10);
});

test('prêt déjà soldé ou informations insuffisantes : pas de tableau', () => {
  assert.strictEqual(buildSchedule({ crd: 5000, startDate: '2022-01-01', rate: 0.02, monthly: 100, endDate: '2021-01-01' }, { today: TODAY }).ok, false);
  const none = buildSchedule({ label: 'vide' }, { today: TODAY });
  assert.strictEqual(none.ok, false);
  assert.ok(none.reason.length > 10);
});

// ---------------------------------------------------------------------------
// Mensualité lissée
// ---------------------------------------------------------------------------

const smoothedB = {
  initialAmount: 180000,
  totalInstallments: 240,
  rate: 0.0225,
  monthly: 800 + 25,
  insurance: 25,
  endDate: '2046-01-05',
  stepDate: '2036-01-05' // fin du prêt A : 120 échéances plus tôt
};

test('palier : la mensualité augmente après la date de fin et le prêt est soldé à la dernière échéance', () => {
  const s = buildSchedule(smoothedB, { today: TODAY });
  assert.ok(s.ok, s.reason);
  assert.strictEqual(s.rows.length, 240);
  const beforeStep = s.rows.filter(r => r.phase === 1);
  const afterStep = s.rows.filter(r => r.phase === 2);
  assert.strictEqual(beforeStep[beforeStep.length - 1].date, '2036-01-05');
  assert.strictEqual(afterStep[0].date, '2036-02-05');
  assert.strictEqual(beforeStep.length + afterStep.length, 240);
  assert.ok(s.summary.payment2 > s.summary.payment1);
  near(afterStep[3].payment, s.summary.payment2, 0.01);
  assert.strictEqual(s.rows[239].balanceAfter, 0);
  assert.strictEqual(s.summary.stepDate, '2036-01-05');
});

test('palier : le solde à la fin du palier sert bien de base à la nouvelle mensualité', () => {
  const s = buildSchedule(smoothedB, { today: TODAY });
  const lastPhase1 = s.rows.filter(r => r.phase === 1).pop();
  near(s.summary.payment2, annuity(lastPhase1.balanceAfter, smoothedB.rate, 120), 0.01);
});

test('palier hors durée : ignoré avec avertissement', () => {
  const s = buildSchedule({ ...smoothedB, stepDate: '2050-01-01' }, { today: TODAY });
  assert.strictEqual(s.summary.payment2, null);
  assert.ok(s.warnings.some(w => w.includes('hors de la durée')));
});

test('previewStep : renvoie l\'ancienne et la nouvelle mensualité', () => {
  const p = previewStep(smoothedB);
  assert.ok(p);
  assert.strictEqual(p.payment1, 800);
  assert.ok(p.payment2 > 800);
  assert.strictEqual(previewStep({ ...smoothedB, stepDate: '' }), null);
});

test('estimateStep : retrouve la fin de palier quand la somme des mensualités est constante', () => {
  const base = { ...smoothedB, stepDate: null };
  const expected = previewStep(smoothedB);
  const otherNet = expected.payment2 - expected.payment1; // mensualité (hors assurance) du prêt A
  const guess = estimateStep(base, otherNet);
  assert.ok(guess);
  assert.strictEqual(guess.stepDate, '2036-01-05');
  near(guess.payment2, expected.payment2, 0.01);
  assert.ok(guess.gap < 0.5);
});

test('estimateStep : sans informations suffisantes, pas de proposition', () => {
  assert.strictEqual(estimateStep({ crd: 1000, startDate: '2026-01-01', rate: 0.02, monthly: 50 }, 100), null);
  assert.strictEqual(estimateStep(smoothedB, 0), null);
});

test('récapitulatif annuel : somme des lignes et CRD de fin d\'année', () => {
  const s = buildSchedule(classic, { today: TODAY });
  const total = s.yearly.reduce((acc, y) => acc + y.count, 0);
  assert.strictEqual(total, 240);
  assert.strictEqual(s.yearly[0].year, 2026);
  assert.strictEqual(s.yearly[0].count, 11);
  assert.strictEqual(s.yearly[s.yearly.length - 1].balanceEnd, 0);
  near(s.yearly.reduce((acc, y) => acc + y.interest, 0), s.summary.totalInterest, 0.05);
});

// ---------------------------------------------------------------------------
// Rapport imprimable
// ---------------------------------------------------------------------------

test('rapport : contenu clé, totaux annuels, ligne de changement de mensualité', () => {
  const loan = { ...smoothedB, label: 'Prêt <B> & Cie', crd: 176744.22, startDate: '2026-08-05' };
  const schedule = buildSchedule(loan, { today: TODAY });
  const html = buildAmortizationHTML(loan, schedule, { generatedAt: new Date('2026-09-21T10:00:00') });
  assert.ok(html.startsWith('<!DOCTYPE html>'));
  assert.ok(html.includes('Prêt &lt;B&gt; &amp; Cie'), 'libellé échappé');
  assert.ok(!html.includes('<B>'));
  assert.strictEqual((html.match(/<tr class="year">/g) || []).length, schedule.yearly.length);
  assert.strictEqual((html.match(/<tr class="step">/g) || []).length, 1);
  assert.ok(html.includes('class="next"'));
  assert.ok(html.includes('Contrôle du relevé'), 'relevé cohérent : note de contrôle');
  // 240 lignes d'échéances + totaux annuels + 1 ligne de palier + 1 ligne d'en-tête
  assert.strictEqual((html.match(/<tr/g) || []).length, 240 + schedule.yearly.length + 1 + 1);
});

test('rapport : mode restant, sans numéro d\'échéance ni contrôle de relevé', () => {
  const loan = { label: 'Reste', crd: 50000, startDate: '2026-09-01', rate: 0.02, monthly: 600, insurance: 10, endDate: '2034-01-01' };
  const schedule = buildSchedule(loan, { today: TODAY });
  const html = buildAmortizationHTML(loan, schedule, { generatedAt: new Date('2026-09-21T10:00:00') });
  assert.ok(html.includes('CRD au dernier relevé'));
  assert.ok(!html.includes('Contrôle du relevé'));
});

test('rapport : relevé incohérent, le message figure une seule fois dans « À vérifier »', () => {
  const loan = { ...smoothedB, stepDate: null, crd: 90000, startDate: '2026-08-05' };
  const schedule = buildSchedule(loan, { today: TODAY });
  const html = buildAmortizationHTML(loan, schedule, { generatedAt: new Date('2026-09-21T10:00:00') });
  assert.strictEqual((html.match(/CRD du relevé/g) || []).length, 1);
  assert.ok(!html.includes('Contrôle du relevé'));
});

// ---------------------------------------------------------------------------
// Projection du CRD (calculations.js) et mensualité lissée
// ---------------------------------------------------------------------------

/** Boucle historique de projectLoanCrdToDate, sans notion de palier (référence de non-régression). */
function legacyProjection(loan, target) {
  let crd = Number(loan.crd) || 0;
  if (crd <= 0) return 0;
  const start = new Date(loan.startDate);
  const end = loan.endDate ? new Date(loan.endDate) : null;
  const t = new Date(target);
  let y = start.getFullYear();
  let m = start.getMonth();
  const targetAbs = t.getFullYear() * 12 + t.getMonth();
  while (crd > 0 && y * 12 + m <= targetAbs) {
    const interest = crd * (loan.rate / 12);
    const net = Math.max(0, loan.monthly - loan.insurance);
    crd = Math.max(0, crd - Math.min(crd, net - interest));
    if (end && (y > end.getFullYear() || (y === end.getFullYear() && m >= end.getMonth()))) crd = 0;
    m += 1;
    if (m > 11) { m = 0; y += 1; }
  }
  return Math.round(crd * 100) / 100;
}

test('projectLoanCrdToDate : sans palier, résultat identique à l\'ancienne boucle', () => {
  let seed = 12345;
  const rnd = () => { seed = (seed * 1103515245 + 12345) % 2147483648; return seed / 2147483648; };
  for (let i = 0; i < 200; i++) {
    const loan = {
      crd: 5000 + Math.floor(rnd() * 300000),
      rate: Math.round(rnd() * 500) / 10000,
      monthly: 300 + Math.floor(rnd() * 1500),
      insurance: Math.floor(rnd() * 60),
      startDate: `20${20 + Math.floor(rnd() * 8)}-${String(1 + Math.floor(rnd() * 12)).padStart(2, '0')}-05`,
      endDate: `20${30 + Math.floor(rnd() * 15)}-${String(1 + Math.floor(rnd() * 12)).padStart(2, '0')}-05`
    };
    const target = `20${25 + Math.floor(rnd() * 30)}-${String(1 + Math.floor(rnd() * 12)).padStart(2, '0')}-15`;
    assert.strictEqual(projectLoanCrdToDate(loan, target), legacyProjection(loan, target), JSON.stringify([loan, target]));
  }
});

test('projectLoanCrdToDate : avec palier, suit le tableau d\'amortissement (à 2 € près)', () => {
  const loan = { crd: 170000, startDate: '2026-10-05', rate: 0.0225, monthly: 825, insurance: 25, endDate: '2046-01-05', stepDate: '2036-01-05' };
  const table = buildSchedule(loan, { today: TODAY });
  assert.ok(table.summary.payment2 > table.summary.payment1);
  for (const date of ['2030-05-15', '2036-01-15', '2036-02-15', '2040-06-15', '2045-12-15']) {
    const row = table.rows.find(r => r.date.slice(0, 7) === date.slice(0, 7));
    near(projectLoanCrdToDate(loan, date), row.balanceAfter, 2, date);
  }
  // Sans la prise en compte du palier, le CRD de 2040 serait très supérieur.
  const without = projectLoanCrdToDate({ ...loan, stepDate: null }, '2040-06-15');
  assert.ok(without > projectLoanCrdToDate(loan, '2040-06-15') + 1000);
  assert.strictEqual(projectLoanCrdToDate(loan, '2046-02-15'), 0);
});

test('rapport : la note de recalage remplace la note de contrôle du relevé', () => {
  const base = buildSchedule(classic, { today: TODAY });
  const loan = { ...classic, crd: base.rows[5].balanceAfter + 3.2, startDate: base.rows[5].date };
  const html = buildAmortizationHTML(loan, buildSchedule(loan, { today: TODAY }), { generatedAt: new Date('2026-09-21T10:00:00') });
  assert.ok(html.includes('Tableau recalé sur le relevé'));
  assert.ok(!html.includes('Contrôle du relevé'));
});

// ---------------------------------------------------------------------------
// Remboursement anticipé partiel
// ---------------------------------------------------------------------------

test('remboursement anticipé, durée réduite : nouvelle durée conforme à la formule', () => {
  const base = buildSchedule(classic, { today: TODAY });
  const at = base.rows[59]; // 60e échéance
  const sim = simulateEarlyRepayment(classic, { date: at.date, amount: 20000, mode: 'duree' }, { today: TODAY });
  assert.ok(sim.ok, sim.reason);
  const c = sim.comparison;
  assert.strictEqual(c.installmentIndex, 60);
  assert.strictEqual(c.amount, 20000);
  // Durée théorique après remboursement : n = -ln(1 - B r / P) / ln(1 + r)
  const B = at.balanceAfter - 20000;
  const r = classic.rate / 12;
  const P = 1109.2;
  const n = -Math.log(1 - B * r / P) / Math.log(1 + r);
  assert.ok(Math.abs(sim.after.rows.length - (60 + Math.ceil(n))) <= 1, `durée ${sim.after.rows.length} vs ${60 + Math.ceil(n)}`);
  assert.ok(Math.abs(c.monthsSaved - (180 - Math.ceil(n))) <= 1, 'mois économisés ' + c.monthsSaved);
  assert.ok(c.monthsSaved > 10);
  assert.strictEqual(c.paymentBefore, c.paymentAfter);
  assert.strictEqual(sim.after.rows[sim.after.rows.length - 1].balanceAfter, 0);
  near(c.interestSaved, base.summary.totalInterest - sim.after.summary.totalInterest, 0.005);
});

test('remboursement anticipé, mensualité réduite : durée inchangée, mensualité recalculée', () => {
  const base = buildSchedule(classic, { today: TODAY });
  const at = base.rows[59];
  const sim = simulateEarlyRepayment(classic, { date: at.date, amount: 20000, mode: 'mensualite' }, { today: TODAY });
  assert.ok(sim.ok, sim.reason);
  const c = sim.comparison;
  assert.strictEqual(c.monthsSaved, 0);
  assert.strictEqual(sim.after.rows.length, 240);
  near(c.paymentAfter, annuity(at.balanceAfter - 20000, classic.rate, 180), 0.02);
  assert.ok(c.paymentAfter < c.paymentBefore);
  assert.strictEqual(sim.after.rows[239].balanceAfter, 0);
  assert.ok(c.interestSaved > 0);
  assert.strictEqual(c.insuranceSaved, 0);
});

test('remboursement anticipé : le capital amorti total (avec le versement) vaut le capital emprunté', () => {
  const sim = simulateEarlyRepayment(classic, { date: '2030-03-10', amount: 15000, mode: 'duree' }, { today: TODAY });
  near(sim.after.summary.totalPrincipal, 200000, 0.01);
  assert.strictEqual(sim.after.summary.totalEarly, 15000);
  assert.strictEqual(sim.after.summary.earlyRepayment.date, '2030-03-15');
});

test('remboursement anticipé : indemnité estimée (moindre de 6 mois d\'intérêts et 3 %) ou saisie', () => {
  const est = simulateEarlyRepayment(classic, { date: '2030-03-10', amount: 20000 }, { today: TODAY });
  // 3 % / 2 = 1,5 % du montant remboursé (inférieur au plafond de 3 %)
  near(est.comparison.indemnity, 300, 0.01);
  assert.strictEqual(est.comparison.indemnityEstimated, true);
  const custom = simulateEarlyRepayment(classic, { date: '2030-03-10', amount: 20000, indemnity: 0 }, { today: TODAY });
  assert.strictEqual(custom.comparison.indemnity, 0);
  assert.strictEqual(custom.comparison.indemnityEstimated, false);
  near(custom.comparison.netGain - est.comparison.netGain, 300, 0.01);
  const high = simulateEarlyRepayment({ ...classic, rate: 0.08 }, { date: '2030-03-10', amount: 10000 }, { today: TODAY });
  near(high.comparison.indemnity, 300, 0.01); // plafond de 3 %
});

test('remboursement anticipé : montant supérieur au CRD plafonné, prêt soldé', () => {
  const sim = simulateEarlyRepayment(classic, { date: '2040-01-10', amount: 999999 }, { today: TODAY });
  assert.ok(sim.ok);
  assert.strictEqual(sim.comparison.cappedAmount, true);
  const last = sim.after.rows[sim.after.rows.length - 1];
  assert.strictEqual(last.balanceAfter, 0);
  assert.strictEqual(last.index, sim.comparison.installmentIndex);
});

test('remboursement anticipé : demandes inexploitables', () => {
  assert.strictEqual(simulateEarlyRepayment(classic, { date: '2030-03-10', amount: 0 }, { today: TODAY }).ok, false);
  assert.strictEqual(simulateEarlyRepayment(classic, { date: '', amount: 5000 }, { today: TODAY }).ok, false);
  const late = simulateEarlyRepayment(classic, { date: '2050-01-01', amount: 5000 }, { today: TODAY });
  assert.strictEqual(late.ok, false);
  assert.ok(late.reason.includes('sans effet'));
});

test('remboursement anticipé sur un prêt recalé : la simulation part du tableau recalé', () => {
  const full = buildSchedule(classic, { today: TODAY });
  const loan = { ...classic, crd: full.rows[5].balanceAfter + 3.2, startDate: full.rows[5].date };
  const base = buildSchedule(loan, { today: TODAY });
  assert.ok(base.summary.calibration);
  const sim = simulateEarlyRepayment(loan, { date: '2030-03-10', amount: 10000 }, { today: TODAY });
  assert.strictEqual(sim.after.rows[0].interest, base.rows[0].interest);
  assert.strictEqual(sim.after.summary.referenceCheck, null);
});

test('rapport : simulation de remboursement anticipé (bandeau, ligne dédiée, totaux annuels)', () => {
  const sim = simulateEarlyRepayment(classic, { date: '2030-03-10', amount: 20000, mode: 'duree' }, { today: TODAY });
  const html = buildAmortizationHTML(classic, sim.after, { generatedAt: new Date('2026-09-21T10:00:00') });
  assert.ok(html.includes('<strong>Simulation</strong>'));
  assert.strictEqual((html.match(/<tr class="early">/g) || []).length, 1);
  assert.ok(html.includes('mensualité conservée, durée réduite'));
});

console.log(`\n${passed} tests passés.`);
