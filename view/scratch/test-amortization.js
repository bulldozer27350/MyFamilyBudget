/**
 * Tests du moteur de tableau d'amortissement (view/js/amortization.js).
 * Lancement : node view/scratch/test-amortization.js
 */
const assert = require('assert');
const path = require('path');

const { Amortization } = require(path.join(__dirname, '..', 'js', 'amortization.js'));
const { buildAmortizationHTML } = require(path.join(__dirname, '..', 'js', 'components', 'amortization-report.js'));
const { buildSchedule, estimateStep, previewStep, annuity } = Amortization;

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
  near(s.summary.referenceCheck.gap, 3.2, 0.01);
  assert.ok(!s.warnings.some(w => w.includes('CRD du relevé')));
});

test('relevé incohérent : avertissement', () => {
  const s = buildSchedule({ ...classic, crd: 150000, startDate: '2026-07-01' }, { today: TODAY });
  assert.strictEqual(s.summary.referenceCheck.consistent, false);
  assert.ok(s.warnings.some(w => w.includes('CRD du relevé')));
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
  assert.ok(html.includes('Contrôle du relevé'));
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

console.log(`\n${passed} tests passés.`);
