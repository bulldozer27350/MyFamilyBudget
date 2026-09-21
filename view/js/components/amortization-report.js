/**
 * Tableau d'amortissement imprimable d'un prêt (export PDF via impression navigateur).
 *
 * Même principe que le bilan patrimonial (patrimoine-report.js) : le document est ouvert dans un
 * nouvel onglet et l'utilisateur choisit « Enregistrer en PDF » dans la boîte d'impression.
 * Le calcul est fait par amortization.js ; ce module ne fait que la mise en page.
 *
 * Le tableau est RECONSTITUÉ à partir des données saisies : il est indicatif et peut différer de
 * quelques euros du tableau contractuel de la banque (arrondis, conventions de calcul).
 */
(function (exports) {
  'use strict';

  // Résolu à l'appel (et non au chargement) : amortization.js peut être chargé après ce fichier.
  function engine() {
    if (exports.Amortization) return exports.Amortization;
    return typeof require === 'function' ? require('../amortization.js').Amortization : null;
  }

  const money = new Intl.NumberFormat('fr-FR', {
    style: 'currency',
    currency: 'EUR',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });
  const percent = new Intl.NumberFormat('fr-FR', {
    style: 'percent',
    minimumFractionDigits: 2,
    maximumFractionDigits: 3
  });

  function escapeHtml(str) {
    return String(str === null || str === undefined ? '' : str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function dateFR(iso) {
    if (!iso) return '—';
    const parts = String(iso).split('-');
    return parts.length === 3 ? `${parts[2]}/${parts[1]}/${parts[0]}` : iso;
  }
  function monthFR(iso) {
    if (!iso) return '—';
    const parts = String(iso).split('-');
    return parts.length >= 2 ? `${parts[1]}/${parts[0]}` : iso;
  }

  function kpi(label, value, note) {
    return `<div class="kpi"><div class="kpi-label">${escapeHtml(label)}</div>`
      + `<div class="kpi-value">${escapeHtml(value)}</div>`
      + (note ? `<div class="kpi-note">${escapeHtml(note)}</div>` : '')
      + '</div>';
  }

  function buildRowsHtml(schedule) {
    const { rows, summary } = schedule;
    const yearTotals = new Map(schedule.yearly.map(y => [y.year, y]));
    const html = [];
    rows.forEach((r, i) => {
      const classes = [];
      if (i < summary.paidCount) classes.push('paid');
      if (i === summary.paidCount) classes.push('next');
      const previous = rows[i - 1];
      if (previous && previous.phase === 1 && r.phase === 2) {
        html.push(`<tr class="step"><td colspan="8">À partir d'ici : nouvelle mensualité de ${escapeHtml(money.format(r.payment))} hors assurance (fin de la mensualité lissée)</td></tr>`);
      }
      html.push(`<tr class="${classes.join(' ')}">`
        + `<td class="c">${r.number !== null ? r.number : r.index}</td>`
        + `<td class="c">${escapeHtml(dateFR(r.date))}</td>`
        + `<td class="n">${escapeHtml(money.format(r.payment))}</td>`
        + `<td class="n">${escapeHtml(money.format(r.interest))}</td>`
        + `<td class="n">${escapeHtml(money.format(r.principal))}</td>`
        + `<td class="n">${escapeHtml(money.format(r.insurance))}</td>`
        + `<td class="n strong">${escapeHtml(money.format(r.total))}</td>`
        + `<td class="n">${escapeHtml(money.format(r.balanceAfter))}</td>`
        + '</tr>');
      const year = Number(r.date.slice(0, 4));
      const next = rows[i + 1];
      if (!next || Number(next.date.slice(0, 4)) !== year) {
        const y = yearTotals.get(year);
        html.push(`<tr class="year"><td colspan="2">Total ${year} (${y.count} éch.)</td>`
          + `<td class="n">${escapeHtml(money.format(y.interest + y.principal))}</td>`
          + `<td class="n">${escapeHtml(money.format(y.interest))}</td>`
          + `<td class="n">${escapeHtml(money.format(y.principal))}</td>`
          + `<td class="n">${escapeHtml(money.format(y.insurance))}</td>`
          + `<td class="n">${escapeHtml(money.format(y.total))}</td>`
          + `<td class="n">${escapeHtml(money.format(y.balanceEnd))}</td></tr>`);
      }
    });
    return html.join('');
  }

  /**
   * Construit le document HTML complet (chaîne autonome, feuille de style A4 incluse).
   *
   * @param {object} loan      la ligne de prêt
   * @param {object} schedule  résultat de Amortization.buildSchedule (ok === true)
   * @param {object} [opts]    { generatedAt: Date }
   */
  function buildAmortizationHTML(loan, schedule, opts) {
    const s = schedule.summary;
    const generatedAt = (opts && opts.generatedAt) || new Date();
    const dateGeneration = generatedAt.toLocaleDateString('fr-FR');
    const heureGeneration = generatedAt.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
    const label = loan.label || 'Prêt';
    const rate = Number(loan.rate) || 0;

    const kpis = [
      kpi(s.mode === 'complet' ? 'Capital emprunté' : 'CRD au dernier relevé', money.format(s.initialBalance), s.mode === 'complet' ? '' : `au ${dateFR(loan.startDate)}`),
      kpi('Taux (hors assurance)', percent.format(rate)),
      kpi('Mensualité hors assurance', money.format(s.payment1), s.payment2 !== null ? `puis ${money.format(s.payment2)} à partir de ${monthFR(schedule.rows.find(r => r.phase === 2).date)}` : (s.paymentComputed ? 'calculée' : '')),
      kpi('Assurance mensuelle', money.format(Number(loan.insurance) || 0)),
      kpi('Échéances', s.totalInstallments ? `${s.count} / ${s.totalInstallments}` : String(s.count), `du ${monthFR(s.firstDate)} au ${monthFR(s.lastDate)}`),
      kpi('Intérêts totaux', money.format(s.totalInterest), s.mode === 'complet' ? '' : 'sur les échéances à venir'),
      kpi('Assurance totale', money.format(s.totalInsurance)),
      kpi('Coût total du crédit', money.format(s.totalInterest + s.totalInsurance), 'intérêts + assurance')
    ].join('');

    const today = [];
    today.push(`CRD estimé à ce jour : <strong>${escapeHtml(money.format(s.balanceToday))}</strong>`);
    if (s.nextIndex !== null) {
      const rank = s.nextNumber !== null && s.totalInstallments ? `n° ${s.nextNumber} sur ${s.totalInstallments}` : `n° ${s.nextIndex} du tableau`;
      today.push(`prochaine échéance : ${escapeHtml(rank)}, le ${escapeHtml(dateFR(s.nextDate))}`);
      today.push(`intérêts restants : ${escapeHtml(money.format(s.remainingInterest))}`);
    } else {
      today.push('prêt intégralement remboursé');
    }

    const notes = [
      'Taux fixe supposé ; intérêts du mois = capital restant dû × taux ÷ 12, arrondis au centime.',
      'Assurance supposée constante sur toute la durée.',
      'Dernière échéance ajustée pour solder exactement le capital.'
    ];
    if (s.mode === 'restant') {
      notes.push('Le CRD du relevé est considéré avant l\'échéance du mois du relevé ; les échéances déjà payées n\'apparaissent pas.');
    }
    if (s.payment2 !== null) {
      notes.push(`Mensualité lissée : ${money.format(s.payment1)} jusqu'au ${dateFR(s.stepDate)} inclus, puis ${money.format(s.payment2)} (recalculée pour solder le capital à la dernière échéance).`);
    }
    if (s.referenceCheck && s.referenceCheck.consistent) {
      const rc = s.referenceCheck;
      notes.push(`Contrôle du relevé du ${dateFR(rc.date)} : CRD saisi ${money.format(rc.declared)}, CRD théorique ${money.format(rc.theoretical)} (${rc.closest === 'before' ? 'avant' : 'après'} l'échéance du mois), écart ${money.format(rc.gap)}.`);
    }
    const toCheck = schedule.warnings.slice();
    if (s.referenceCheck && s.referenceCheck.message) toCheck.push(s.referenceCheck.message);
    const warningsHtml = toCheck.length
      ? `<div class="warnings"><strong>À vérifier</strong><ul>${toCheck.map(w => `<li>${escapeHtml(w)}</li>`).join('')}</ul></div>`
      : '';

    return `<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="utf-8">
<title>Tableau d'amortissement — ${escapeHtml(label)}</title>
<style>
  @page { size: A4 portrait; margin: 12mm; }
  * { box-sizing: border-box; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
  body { font-family: Arial, Helvetica, sans-serif; color: #232A2E; font-size: 11px; margin: 0; padding: 16px; }
  h1 { font-family: Georgia, serif; font-size: 22px; margin: 0 0 2px; }
  .subtitle { color: #6B7278; font-size: 10px; margin-bottom: 12px; }
  .kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin-bottom: 10px; }
  .kpi { border: 1px solid #DED6C4; border-radius: 6px; padding: 6px 8px; background: #F6F3EC; }
  .kpi-label { font-size: 9px; text-transform: uppercase; letter-spacing: 0.4px; color: #6B7278; }
  .kpi-value { font-size: 14px; font-weight: 700; margin-top: 2px; }
  .kpi-note { font-size: 9px; color: #6B7278; margin-top: 1px; }
  .today { border-left: 3px solid #2F5D50; padding: 4px 10px; margin: 8px 0 10px; background: #F1F6F3; }
  .warnings { border: 1px solid #C9A227; background: #FFF9E6; border-radius: 6px; padding: 6px 10px; margin-bottom: 10px; font-size: 10px; }
  .warnings ul { margin: 4px 0 0; padding-left: 16px; }
  table { width: 100%; border-collapse: collapse; font-size: 9.5px; }
  thead { display: table-header-group; }
  th { background: #2F5D50; color: #fff; padding: 4px 5px; text-align: right; font-weight: 600; }
  th:first-child, th:nth-child(2) { text-align: center; }
  td { padding: 2.5px 5px; border-bottom: 1px solid #ECE6D8; }
  td.c { text-align: center; }
  td.n { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }
  td.strong { font-weight: 700; }
  tr { page-break-inside: avoid; }
  tr.paid td { color: #8A9096; }
  tr.next td { background: #E4F0EA; font-weight: 700; }
  tr.year td { background: #EFEAE0; font-weight: 700; border-top: 1px solid #C9BFA6; }
  tr.step td { background: #FFF2D6; color: #7A5B00; font-style: italic; text-align: center; padding: 4px; }
  .legend { font-size: 9.5px; color: #6B7278; margin: 6px 0 10px; }
  .notes { font-size: 9.5px; color: #4A5257; margin-top: 12px; }
  .notes ul { margin: 4px 0 0; padding-left: 16px; }
  .footer { margin-top: 14px; border-top: 1px solid #DED6C4; padding-top: 6px; font-size: 9px; color: #6B7278; }
  @media print { body { padding: 0; } }
</style>
</head>
<body>
  <h1>Tableau d'amortissement — ${escapeHtml(label)}</h1>
  <div class="subtitle">Généré le ${escapeHtml(dateGeneration)} à ${escapeHtml(heureGeneration)} — MyFamilyBudget (tableau reconstitué, indicatif : il peut différer du tableau contractuel de la banque)</div>
  <div class="kpis">${kpis}</div>
  <div class="today">${today.join(' — ')}</div>
  ${warningsHtml}
  <table>
    <thead><tr><th>N°</th><th>Date</th><th>Échéance hors ass.</th><th>Intérêts</th><th>Capital amorti</th><th>Assurance</th><th>Total à payer</th><th>CRD après échéance</th></tr></thead>
    <tbody>${buildRowsHtml(schedule)}</tbody>
  </table>
  <div class="legend">Lignes grisées : échéances déjà passées. Ligne surlignée : prochaine échéance.</div>
  <div class="notes"><strong>Hypothèses</strong><ul>${notes.map(n => `<li>${escapeHtml(n)}</li>`).join('')}</ul></div>
  <div class="footer">Document généré localement dans votre navigateur à partir des données saisies dans MyFamilyBudget.</div>
</body>
</html>`;
  }

  /**
   * Ouvre le tableau d'amortissement dans un nouvel onglet et déclenche la boîte d'impression.
   * @returns {boolean} false si le tableau ne peut pas être construit (message affiché).
   */
  function exportAmortizationPDF(loan, opts) {
    const schedule = engine().buildSchedule(loan, opts);
    if (!schedule.ok) {
      window.alert(schedule.reason);
      return false;
    }
    const html = buildAmortizationHTML(loan, schedule, opts);
    const printWindow = window.open('', '_blank', 'width=900,height=1000');
    if (!printWindow) {
      window.alert('Le navigateur a bloqué l\'ouverture de la fenêtre d\'impression. Autorisez les pop-ups pour cette page puis réessayez.');
      return false;
    }
    printWindow.document.open();
    printWindow.document.write(html);
    printWindow.document.close();
    printWindow.focus();
    // Laisse le temps au navigateur de mettre en page le document avant d'ouvrir la boîte d'impression.
    printWindow.setTimeout(() => {
      printWindow.print();
    }, 300);
    return true;
  }

  exports.buildAmortizationHTML = buildAmortizationHTML;
  exports.exportAmortizationPDF = exportAmortizationPDF;
})(typeof window !== 'undefined' ? (window.BudgetApp = window.BudgetApp || {}) : module.exports);
