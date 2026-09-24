/**
 * En-têtes "Disponible liquide / illiquide / total" de la page Objectifs : consomme
 * computeTresorerieDisponible (view/js/calculations.js) pour afficher, une fois les réservations
 * de tous les objectifs déduites, ce qu'il reste réellement sur chaque groupe de comptes.
 *
 * Les deux cartes claires (liquide/illiquide) sont interactives : au survol sur un appareil qui
 * supporte le hover (desktop), ou au clic sur la vignette sinon (mobile/tactile), une info-bulle
 * liste chaque compte du groupe avec son disponible et son solde réel. La carte "Disponible
 * total" reste statique, comme sur la maquette d'origine.
 */
(function (exports) {
  'use strict';

  const { useState } = React;
  const h = React.createElement;
  const BA = exports.C ? exports : (window.BudgetApp || {});
  const C = BA.C || {};
  const eur = BA.eur || (v => `${Number(v) || 0} €`);

  const tresorerieDisponible = () => BA.computeTresorerieDisponible
    || (window.BudgetApp && window.BudgetApp.computeTresorerieDisponible)
    || (() => ({ liquide: { disponible: 0 }, illiquide: { disponible: 0 }, total: { disponible: 0 }, parPlacement: [] }));

  const line = C.line || '#DED6C4';
  const ink = C.ink || '#232A2E';
  const inkSoft = C.inkSoft || '#6B7278';
  const pine = C.pine || '#2F5D50';
  const gold = C.gold || '#93802E';
  const navy = C.navy || '#28394A';
  const panel = C.panel || '#FFFFFF';

  // Un appareil sans "hover" réel (tactile) n'a pas d'onMouseEnter fiable : sur ceux-là, seul le
  // clic doit piloter l'ouverture de l'info-bulle, pour ne pas la laisser coincée ouverte après
  // un tap qui déclenche un mouseenter synthétique.
  const supportsHover = typeof window !== 'undefined' && window.matchMedia
    && window.matchMedia('(hover: hover)').matches;

  function AccountBreakdownTable({ rows }) {
    if (!rows || rows.length === 0) {
      return h('div', { style: { fontSize: 12, color: inkSoft, padding: 6 } }, 'Aucun compte dans ce groupe.');
    }
    return h('table', { style: { width: '100%', fontSize: 12, borderCollapse: 'collapse' } },
      h('thead', null, h('tr', null,
        h('th', { style: { textAlign: 'left', padding: '3px 8px', color: inkSoft, fontWeight: 600, whiteSpace: 'nowrap' } }, 'Compte'),
        h('th', { style: { textAlign: 'right', padding: '3px 8px', color: inkSoft, fontWeight: 600, whiteSpace: 'nowrap' } }, 'Disponible'),
        h('th', { style: { textAlign: 'right', padding: '3px 8px', color: inkSoft, fontWeight: 600, whiteSpace: 'nowrap' } }, 'Solde réel'))),
      h('tbody', null, rows.map(r => h('tr', { key: r.id, style: { borderTop: `1px solid ${line}` } },
        h('td', { style: { padding: '5px 8px', color: ink } }, r.label),
        h('td', { style: { padding: '5px 8px', textAlign: 'right', fontWeight: 700, color: ink, whiteSpace: 'nowrap' } }, eur(r.disponible)),
        h('td', { style: { padding: '5px 8px', textAlign: 'right', color: inkSoft, whiteSpace: 'nowrap' } }, eur(r.balance))))));
  }

  function KpiCard({ label, value, accent, rows }) {
    const [open, setOpen] = useState(false);

    return h('div', { style: { position: 'relative', flex: '1 1 220px', minWidth: 220 } },
      h('div', {
        onMouseEnter: supportsHover ? () => setOpen(true) : undefined,
        onMouseLeave: supportsHover ? () => setOpen(false) : undefined,
        onClick: () => setOpen(o => !o),
        style: { background: panel, border: `1px solid ${line}`, borderRadius: 10, padding: '14px 16px', cursor: 'pointer' }
      },
      h('div', { style: { fontSize: 12, color: inkSoft } }, label),
      h('div', { style: { fontSize: 22, fontWeight: 700, color: accent, fontFamily: "'Newsreader', serif", marginTop: 2 } }, eur(value))),
      open ? h('div', {
        style: {
          position: 'absolute', top: '100%', left: 0, marginTop: 6, zIndex: 20, minWidth: 300,
          background: panel, border: `1px solid ${line}`, borderRadius: 8, boxShadow: '0 8px 20px rgba(0,0,0,0.14)', padding: 6
        }
      }, h(AccountBreakdownTable, { rows })) : null);
  }

  function TotalCard({ value }) {
    return h('div', { style: { flex: '1 1 220px', minWidth: 220, background: navy, borderRadius: 10, padding: '14px 16px' } },
      h('div', { style: { fontSize: 12, color: 'rgba(255,255,255,0.72)' } }, 'Disponible total'),
      h('div', { style: { fontSize: 22, fontWeight: 700, color: '#fff', fontFamily: "'Newsreader', serif", marginTop: 2 } }, eur(value)));
  }

  /**
   * @param {object} rawData données complètes (comptes, objectifs...) — mêmes props que le
   *                         reste de la page Objectifs.
   */
  function TresorerieHeaderCards({ rawData }) {
    const disponible = tresorerieDisponible()(rawData);
    const parPlacement = disponible.parPlacement || [];

    return h('div', { style: { display: 'flex', gap: 14, marginBottom: 18, flexWrap: 'wrap' } },
      h(KpiCard, {
        label: 'Disponible liquide', value: disponible.liquide.disponible, accent: pine,
        rows: parPlacement.filter(p => p.groupe === 'liquide')
      }),
      h(KpiCard, {
        label: 'Disponible illiquide', value: disponible.illiquide.disponible, accent: gold,
        rows: parPlacement.filter(p => p.groupe === 'illiquide')
      }),
      h(TotalCard, { value: disponible.total.disponible }));
  }

  exports.TresorerieHeaderCards = TresorerieHeaderCards;
})(typeof window !== 'undefined' ? (window.BudgetApp = window.BudgetApp || {}) : module.exports);
