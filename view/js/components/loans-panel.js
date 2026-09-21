/**
 * Crédits : cartes de synthèse + tiroir d'édition d'un prêt (Patrimoine).
 *
 * Remplace le tableau éditable des crédits. Chaque prêt est une carte (informations générales) ;
 * un clic ouvre un tiroir où l'on saisit les informations du contrat bancaire : capital emprunté,
 * nombre d'échéances, date de dernière échéance, taux, mensualité, assurance, éventuelle
 * mensualité lissée (palier) et dernier relevé de CRD. À partir de là, le tableau d'amortissement
 * de chaque prêt peut être exporté en PDF (amortization.js + amortization-report.js).
 *
 * Le CRD et la « Date CRD » du dernier relevé restent enregistrés sur le prêt : les autres écrans
 * (vue d'ensemble, analyse des prêts) continuent de s'en servir.
 */
(function (exports) {
  'use strict';

  const { useState } = React;
  const h = React.createElement;
  const BA = exports.C ? exports : (window.BudgetApp || {});
  const C = BA.C || {};
  const { Field, PercentField } = BA;

  // Résolus à l'appel : ces modules peuvent être chargés après celui-ci.
  const engine = () => exports.Amortization || (window.BudgetApp && window.BudgetApp.Amortization) || null;
  const reportExporter = () => exports.exportAmortizationPDF || (window.BudgetApp && window.BudgetApp.exportAmortizationPDF) || null;

  // ---------------------------------------------------------------------------
  // Formatage et conversions de saisie
  // ---------------------------------------------------------------------------

  const moneyFormat = new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR', minimumFractionDigits: 2, maximumFractionDigits: 2 });
  const money = v => moneyFormat.format(Number(v) || 0);
  const percentFormat = new Intl.NumberFormat('fr-FR', { style: 'percent', minimumFractionDigits: 2, maximumFractionDigits: 3 });

  function dateFR(iso) {
    if (!iso) return '—';
    const parts = String(iso).split('-');
    return parts.length === 3 ? `${parts[2]}/${parts[1]}/${parts[0]}` : String(iso);
  }
  function monthFR(iso) {
    if (!iso) return '—';
    const parts = String(iso).split('-');
    return parts.length >= 2 ? `${parts[1]}/${parts[0]}` : String(iso);
  }

  const toNumberOrNull = v => (v === '' || v === null || v === undefined || !Number.isFinite(Number(v))) ? null : Number(v);
  const toNumber = v => { const n = toNumberOrNull(v); return n === null ? 0 : n; };
  const toIntegerOrNull = v => { const n = toNumberOrNull(v); return n === null ? null : Math.max(0, Math.trunc(n)); };
  const toTextOrNull = v => (v === '' || v === undefined || v === null) ? null : v;

  /** Tableau d'amortissement du prêt tel que saisi (null si le moteur est indisponible). */
  function scheduleOf(loan) {
    const e = engine();
    return e ? e.buildSchedule(loan) : null;
  }

  /** Mensualité assurance comprise : saisie, ou à défaut calculée par le moteur. */
  function effectiveMonthly(loan) {
    if (Number(loan.monthly) > 0) return Number(loan.monthly);
    const schedule = scheduleOf(loan);
    return schedule && schedule.ok ? schedule.summary.payment1 + (Number(loan.insurance) || 0) : 0;
  }

  // ---------------------------------------------------------------------------
  // Styles partagés
  // ---------------------------------------------------------------------------

  const line = C.line || '#DED6C4';
  const ink = C.ink || '#232A2E';
  const inkSoft = C.inkSoft || '#6B7278';
  const pine = C.pine || '#2F5D50';
  const pineSoft = C.pineSoft || '#E3ECE8';
  const brick = C.brick || '#A8503C';
  const paper = C.paper || '#F6F3EC';
  const panel = C.panel || '#FFFFFF';

  const inputBox = { border: `1px solid ${line}`, borderRadius: 7, padding: '2px 6px', background: '#fff' };
  const buttonBase = { fontSize: 12.5, borderRadius: 7, padding: '7px 12px', cursor: 'pointer', fontWeight: 600, border: 'none' };
  const primaryButton = { ...buttonBase, background: pine, color: '#fff' };
  const softButton = { ...buttonBase, background: pineSoft, color: pine };
  const dangerButton = { ...buttonBase, background: 'transparent', color: brick, border: `1px solid ${brick}` };

  function Labeled({ label, hint, span, children }) {
    return h('div', { style: span ? { gridColumn: `span ${span}` } : undefined },
      h('label', { style: { display: 'block', fontSize: 11, color: inkSoft, marginBottom: 4 } }, label),
      h('div', { style: inputBox }, children),
      hint ? h('div', { style: { fontSize: 10.5, color: inkSoft, marginTop: 3 } }, hint) : null);
  }

  function Block({ title, children }) {
    return h('div', { style: { background: paper, borderRadius: 10, padding: 16, border: `1px solid ${line}`, marginBottom: 16 } },
      h('div', { style: { fontSize: 12, fontWeight: 700, textTransform: 'uppercase', letterSpacing: 0.5, color: pine, marginBottom: 12 } }, title),
      children);
  }

  function Note({ tone, children }) {
    const colors = tone === 'warn' ? { bg: '#FFF9E6', border: '#C9A227', text: '#6B5300' }
      : tone === 'ok' ? { bg: '#EAF4EE', border: pine, text: pine }
        : { bg: '#fff', border: line, text: inkSoft };
    return h('div', { style: { fontSize: 11.5, lineHeight: 1.45, borderRadius: 7, padding: '7px 10px', marginTop: 10, background: colors.bg, border: `1px solid ${colors.border}`, color: colors.text } }, children);
  }

  // ---------------------------------------------------------------------------
  // Carte d'un prêt
  // ---------------------------------------------------------------------------

  function Stat({ label, value, strong }) {
    return h('div', null,
      h('div', { style: { fontSize: 10, textTransform: 'uppercase', letterSpacing: 0.4, color: inkSoft } }, label),
      h('div', { style: { fontSize: strong ? 15 : 13, fontWeight: strong ? 700 : 600, color: ink, fontFamily: "'IBM Plex Mono', monospace" } }, value));
  }

  function LoanCard({ loan, onOpen, onExport }) {
    const schedule = scheduleOf(loan);
    const ok = schedule && schedule.ok;
    const s = ok ? schedule.summary : null;
    const insurance = Number(loan.insurance) || 0;
    const monthlyTotal = Number(loan.monthly) || (s ? s.payment1 + insurance : 0);
    const rank = s && s.nextNumber !== null && s.totalInstallments
      ? `n° ${s.nextNumber} / ${s.totalInstallments}`
      : (s && s.nextIndex === null ? 'soldé' : '—');
    return h('div', {
      onClick: onOpen,
      style: { background: panel, border: `1.5px solid ${line}`, borderRadius: 10, padding: '12px 14px', cursor: 'pointer', boxShadow: '0 2px 4px rgba(0,0,0,0.02)', display: 'flex', flexDirection: 'column', gap: 10 },
      onMouseEnter: e => { e.currentTarget.style.borderColor = pine; },
      onMouseLeave: e => { e.currentTarget.style.borderColor = line; }
    },
    h('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 } },
      h('div', { style: { fontFamily: "'Newsreader', serif", fontSize: 16, fontWeight: 700, color: ink } }, loan.label || '(sans nom)'),
      loan.stepDate ? h('span', { title: 'Mensualité lissée : elle change à une date donnée', style: { fontSize: 10, fontWeight: 700, color: '#7A5B00', background: '#FFF2D6', borderRadius: 999, padding: '2px 8px' } }, 'lissé') : null),
    h('div', { style: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 } },
      h(Stat, { label: 'CRD dernier relevé', value: Number(loan.crd) > 0 ? money(loan.crd) : '—', strong: true }),
      h(Stat, { label: 'CRD théorique ce jour', value: s ? money(s.balanceToday) : '—', strong: true }),
      h(Stat, { label: 'Taux hors assurance', value: percentFormat.format(Number(loan.rate) || 0) }),
      h(Stat, { label: 'Mensualité (ass. comprise)', value: money(monthlyTotal) }),
      h(Stat, { label: 'Prochaine échéance', value: rank }),
      h(Stat, { label: 'Dernière échéance', value: monthFR(loan.endDate) })),
    h('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center' } },
      h('span', { style: { fontSize: 11, color: inkSoft } }, ok ? '' : 'Contrat à compléter pour le tableau'),
      h('button', {
        type: 'button',
        disabled: !ok,
        title: ok ? 'Ouvrir le tableau d\'amortissement (PDF)' : (schedule ? schedule.reason : 'Indisponible'),
        onClick: e => { e.stopPropagation(); onExport(); },
        style: { ...softButton, opacity: ok ? 1 : 0.45, cursor: ok ? 'pointer' : 'not-allowed' }
      }, '📄 Tableau d\'amortissement')));
  }

  // ---------------------------------------------------------------------------
  // Tiroir d'édition
  // ---------------------------------------------------------------------------

  function StepEstimator({ loan, loans, onApply }) {
    const others = loans.filter(l => l.id !== loan.id && effectiveMonthly(l) > 0);
    const [otherId, setOtherId] = useState('');
    const [proposals, setProposals] = useState(null);
    const e = engine();
    if (!e || others.length === 0) return null;

    const estimate = () => {
      const other = others.find(l => l.id === otherId);
      if (!other) { setProposals([]); return; }
      const insurance = Number(other.insurance) || 0;
      const monthly = effectiveMonthly(other);
      const base = { ...loan, stepDate: null };
      const candidates = [
        { label: `sans l'assurance de « ${other.label} »`, result: e.estimateStep(base, Math.max(0, monthly - insurance)) },
        { label: `avec l'assurance de « ${other.label} » reprise`, result: e.estimateStep(base, monthly) }
      ].filter(c => c.result);
      const seen = new Set();
      setProposals(candidates.filter(c => (seen.has(c.result.stepDate) ? false : seen.add(c.result.stepDate))));
    };

    return h('div', { style: { marginTop: 12, paddingTop: 12, borderTop: `1px dashed ${line}` } },
      h('div', { style: { fontSize: 11.5, color: inkSoft, marginBottom: 6 } },
        'Date de fin inconnue ? Un lissage garde la somme des mensualités constante : choisissez le prêt avec lequel celui-ci est lissé pour estimer la date.'),
      h('div', { style: { display: 'flex', gap: 8, alignItems: 'center' } },
        h('select', { value: otherId, onChange: ev => { setOtherId(ev.target.value); setProposals(null); }, style: { ...inputBox, fontSize: 12.5, flex: 1, padding: '6px 8px' } },
          h('option', { value: '' }, '— Prêt lissé avec… —'),
          others.map(l => h('option', { key: l.id, value: l.id }, `${l.label} (${money(effectiveMonthly(l))}/mois)`))),
        h('button', { type: 'button', disabled: !otherId, onClick: estimate, style: { ...softButton, opacity: otherId ? 1 : 0.5 } }, 'Estimer')),
      proposals && proposals.length === 0
        ? h(Note, { tone: 'warn' }, 'Estimation impossible : renseignez d\'abord le capital emprunté, le nombre d\'échéances, la date de dernière échéance et la mensualité de ce prêt.')
        : null,
      proposals && proposals.map(p => h('div', { key: p.result.stepDate, style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8, marginTop: 8, fontSize: 12, color: ink } },
        h('span', null,
          `Fin de palier le ${dateFR(p.result.stepDate)} (${p.label}) : nouvelle mensualité ${money(p.result.payment2)} hors assurance`,
          p.result.gap > 1 ? h('span', { style: { color: brick } }, ` — écart avec la cible ${money(p.result.gap)}`) : null),
        h('button', { type: 'button', onClick: () => onApply(p.result.stepDate), style: primaryButton }, 'Appliquer'))));
  }

  function LoanDrawer({ loan, loans, isNew, onChange, onClose, onSaveNew, onRemove, onExport }) {
    if (!loan) return null;
    const e = engine();
    const schedule = scheduleOf(loan);
    const summary = schedule && schedule.ok ? schedule.summary : null;
    const preview = e && loan.stepDate ? e.previewStep(loan) : null;
    const check = summary && summary.referenceCheck;

    const numberField = (key, opts) => h(Field, {
      type: 'number', mono: true, align: 'right', placeholder: opts && opts.placeholder,
      value: loan[key] === null || loan[key] === undefined ? '' : loan[key],
      onChange: v => onChange(key, opts && opts.integer ? toIntegerOrNull(v) : (opts && opts.optional ? toNumberOrNull(v) : toNumber(v)))
    });
    const dateField = key => h(Field, {
      type: 'date', value: loan[key] || '',
      onChange: v => onChange(key, toTextOrNull(v))
    });

    return h('div', { style: { position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, zIndex: 1000, display: 'flex', justifyContent: 'flex-end', background: 'rgba(0, 0, 0, 0.4)', backdropFilter: 'blur(2px)' } },
      h('div', { style: { flex: 1 }, onClick: onClose }),
      h('div', { style: { width: 560, maxWidth: '92vw', height: '100%', background: panel, boxShadow: '-4px 0 24px rgba(0, 0, 0, 0.15)', display: 'flex', flexDirection: 'column' } },
        h('div', { style: { padding: '18px 24px', borderBottom: `1px solid ${line}`, display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: paper } },
          h('div', null,
            h('h3', { style: { margin: 0, fontFamily: "'Newsreader', serif", fontSize: 20, color: ink } }, isNew ? 'Nouveau prêt' : (loan.label || 'Prêt')),
            h('div', { style: { fontSize: 12, color: inkSoft, marginTop: 2 } }, 'Informations du contrat bancaire')),
          h('button', { type: 'button', onClick: onClose, 'aria-label': 'Fermer', style: { border: 'none', background: 'transparent', fontSize: 20, cursor: 'pointer', color: inkSoft } }, '✕')),

        h('div', { style: { flex: 1, overflowY: 'auto', padding: 24 } },
          h(Block, { title: '1. Identification' },
            h(Labeled, { label: 'Libellé' },
              h(Field, { type: 'text', value: loan.label || '', onChange: v => onChange('label', v) }))),

          h(Block, { title: '2. Contrat bancaire' },
            h('div', { style: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 } },
              h(Labeled, { label: 'Capital emprunté (€)' }, numberField('initialAmount', { optional: true })),
              h(Labeled, { label: 'Nombre total d\'échéances' }, numberField('totalInstallments', { integer: true })),
              h(Labeled, { label: 'Date de dernière échéance' }, dateField('endDate')),
              h(Labeled, { label: 'Taux hors assurance (%)' }, h(PercentField, { mono: true, align: 'right', value: loan.rate, onChange: v => onChange('rate', v === null ? 0 : v) })),
              h(Labeled, { label: 'Mensualité (€, assurance comprise)', hint: 'Laissez 0 pour la calculer depuis le taux et la durée.' }, numberField('monthly')),
              h(Labeled, { label: 'Assurance (€ / mois)', hint: 'Supposée constante.' }, numberField('insurance'))),
            summary && summary.mode === 'complet'
              ? h(Note, null,
                summary.nextIndex === null
                  ? 'Prêt intégralement remboursé.'
                  : `Prochaine échéance : n° ${summary.nextNumber} sur ${summary.totalInstallments}, le ${dateFR(summary.nextDate)}. Mensualité théorique hors assurance : ${money(summary.theoreticalPayment)}.`)
              : null),

          h(Block, { title: '3. Mensualité lissée (facultatif)' },
            h('div', { style: { fontSize: 12, color: inkSoft, marginBottom: 10 } },
              'Si ce prêt est lissé avec un autre, sa mensualité change quand l\'autre se termine. Indiquez la dernière échéance payée à la mensualité actuelle : la mensualité suivante est recalculée pour solder le prêt à sa date de fin.'),
            h(Labeled, { label: 'Dernière échéance à la mensualité actuelle' },
              h('div', { style: { display: 'flex', alignItems: 'center', gap: 6 } },
                h('div', { style: { flex: 1 } }, dateField('stepDate')),
                loan.stepDate ? h('button', { type: 'button', onClick: () => onChange('stepDate', null), style: { border: 'none', background: 'transparent', color: inkSoft, cursor: 'pointer', fontSize: 12 } }, 'Effacer') : null)),
            preview
              ? h(Note, { tone: 'ok' }, `À partir de ${monthFR(nextMonth(loan.stepDate))} : ${money(preview.payment2)} hors assurance, soit ${money(preview.payment2 - preview.payment1)} de plus qu'aujourd'hui — à comparer à la mensualité du prêt qui se termine.`)
              : null,
            h(StepEstimator, { loan, loans, onApply: date => onChange('stepDate', date) })),

          h(Block, { title: '4. Dernier relevé bancaire' },
            h('div', { style: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 } },
              h(Labeled, { label: 'CRD au relevé (€)' }, numberField('crd')),
              h(Labeled, { label: 'Date du relevé (« Date CRD »)' }, dateField('startDate'))),
            check
              ? h(Note, { tone: check.consistent ? 'ok' : 'warn' },
                check.consistent && check.calibrated
                  ? `Tableau recalé sur ce relevé : l'écart d'origine (${money(summary.calibration.gapBefore)}) est imputé aux intérêts de la 1re échéance (+${money(summary.calibration.adjustment)}), car la 1re période dure souvent plus d'un mois. Les échéances passent maintenant par ce CRD.`
                  : check.consistent
                  ? `Cohérent avec le tableau : CRD théorique ${money(check.theoretical)} (${check.closest === 'before' ? 'avant' : 'après'} l'échéance du mois), écart ${money(check.gap)}.`
                  : check.message)
              : h(Note, null, 'Le CRD et sa date servent aux autres écrans (vue d\'ensemble, analyse des prêts). Avec le capital emprunté renseigné, ils servent aussi de contrôle du tableau.'),
            h('div', { style: { fontSize: 10.5, color: inkSoft, marginTop: 8, lineHeight: 1.4 } },
              'Les autres écrans considèrent l\'échéance du mois de cette date comme restant à payer : saisissez le CRD d\'aujourd\'hui avec la date de la prochaine échéance (avant son prélèvement). Le contrôle du tableau accepte aussi un CRD juste après une échéance.')),

          schedule && !schedule.ok ? h(Note, { tone: 'warn' }, schedule.reason) : null,
          schedule && schedule.ok && schedule.warnings.length
            ? h(Note, { tone: 'warn' }, schedule.warnings.map((w, i) => h('div', { key: i }, '• ' + w)))
            : null),

        h('div', { style: { padding: '14px 24px', borderTop: `1px solid ${line}`, display: 'flex', justifyContent: 'space-between', gap: 8, background: paper } },
          h('div', { style: { display: 'flex', gap: 8 } },
            !isNew ? h('button', { type: 'button', onClick: onRemove, style: dangerButton }, 'Supprimer') : null,
            h('button', {
              type: 'button', disabled: !(schedule && schedule.ok), onClick: onExport,
              style: { ...softButton, opacity: schedule && schedule.ok ? 1 : 0.45, cursor: schedule && schedule.ok ? 'pointer' : 'not-allowed' }
            }, '📄 Tableau d\'amortissement')),
          isNew
            ? h('div', { style: { display: 'flex', gap: 8 } },
              h('button', { type: 'button', onClick: onClose, style: softButton }, 'Annuler'),
              h('button', { type: 'button', onClick: onSaveNew, style: primaryButton }, 'Enregistrer'))
            : h('button', { type: 'button', onClick: onClose, style: primaryButton }, 'Fermer'))));
  }

  /** « 2036-01-05 » → « 2036-02 » : premier mois à la nouvelle mensualité. */
  function nextMonth(iso) {
    const parts = String(iso).split('-');
    if (parts.length < 2) return iso;
    let y = Number(parts[0]);
    let m = Number(parts[1]) + 1;
    if (m > 12) { m = 1; y += 1; }
    return `${y}-${String(m).padStart(2, '0')}`;
  }

  // ---------------------------------------------------------------------------
  // Panneau
  // ---------------------------------------------------------------------------

  /**
   * @param {object[]} loans
   * @param {(id:string, field:string, value:any) => Promise|void} onCell  écriture d'une cellule
   * @param {() => Promise<object>} onCreateDraft   nouvelle ligne non enregistrée
   * @param {(row:object) => void} onSaveNew        enregistre un brouillon
   * @param {(id:string) => void} onRemove
   */
  function LoansPanel({ loans, onCell, onCreateDraft, onSaveNew, onRemove }) {
    const [selectedId, setSelectedId] = useState(null);
    const [draft, setDraft] = useState(null);
    const [isNew, setIsNew] = useState(false);
    const [open, setOpen] = useState(false);

    const active = isNew ? draft : (loans || []).find(l => l.id === selectedId) || null;

    const close = () => { setOpen(false); setIsNew(false); setDraft(null); };
    const add = () => {
      Promise.resolve(onCreateDraft()).then(row => {
        setDraft(row);
        setIsNew(true);
        setSelectedId(null);
        setOpen(true);
      });
    };
    const change = (field, value) => {
      if (isNew) {
        setDraft(prev => ({ ...prev, [field]: value }));
        return undefined;
      }
      return active ? onCell(active.id, field, value) : undefined;
    };
    const exportPdf = loan => {
      const exporter = reportExporter();
      if (exporter) exporter(loan);
    };

    return h(React.Fragment, null,
      (loans || []).length === 0
        ? h('div', { style: { color: inkSoft, fontSize: 13, padding: '8px 0 12px' } }, 'Aucun crédit saisi.')
        : h('div', { style: { display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(290px, 1fr))', gap: 14 } },
          loans.map(loan => h(LoanCard, {
            key: loan.id, loan,
            onOpen: () => { setSelectedId(loan.id); setIsNew(false); setDraft(null); setOpen(true); },
            onExport: () => exportPdf(loan)
          }))),
      h('button', { type: 'button', onClick: add, style: { ...softButton, marginTop: 14, display: 'flex', alignItems: 'center', gap: 6 } }, '+ Ajouter un prêt'),
      open && active
        ? h(LoanDrawer, {
          loan: active, loans: loans || [], isNew,
          onChange: change,
          onClose: close,
          onSaveNew: () => { onSaveNew(draft); close(); },
          onRemove: () => {
            if (window.confirm(`Êtes-vous sûr de vouloir supprimer le prêt « ${active.label} » ?`)) {
              onRemove(active.id);
              close();
            }
          },
          onExport: () => exportPdf(active)
        })
        : null);
  }

  exports.LoansPanel = LoansPanel;
})(typeof window !== 'undefined' ? (window.BudgetApp = window.BudgetApp || {}) : module.exports);
