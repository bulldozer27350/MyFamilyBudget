/**
 * Objectifs d'épargne (Analyse) : remplace le tableau éditable + le bloc "Suivi & bascule" par
 * des cartes (une par objectif) et un tiroir d'édition des comptes support.
 *
 * Chaque objectif peut être alimenté par plusieurs comptes de la vue Patrimoine ; la barre de
 * progression de la carte affiche la part du montant visé couverte par du liquide et celle
 * couverte par de l'illiquide (deux couleurs), le reste (non atteint) en gris. Le statut de
 * bascule (lointain / à sécuriser / à rapatrier / échu), calculé par computeGoalReallocation
 * (view/js/calculations.js), est intégré à la carte sous forme de badge — il ne fait donc plus
 * l'objet d'un bloc séparé.
 *
 * Le tiroir liste tous les comptes de la vue Patrimoine avec une case à cocher (compte
 * alimentant l'objectif ou non) et, si cochée, le montant qui lui est réservé. La saisie est
 * plafonnée au montant encore disponible sur ce compte compte tenu des AUTRES objectifs qui s'en
 * servent déjà (computeDisponiblePourAllocation) — même règle que la validation faite par
 * BudgetMutationService côté serveur, qui reste le garde-fou final.
 */
(function (exports) {
  'use strict';

  const { useState } = React;
  const h = React.createElement;
  const BA = exports.C ? exports : (window.BudgetApp || {});
  const C = BA.C || {};
  const { Field } = BA;
  const eur = BA.eur || (v => `${Number(v) || 0} €`);
  const uid = BA.uid || (() => Math.random().toString(36).slice(2, 10));

  // Résolue à l'appel : calculations.js peut être chargé après ce module.
  const disponiblePourAllocation = () => BA.computeDisponiblePourAllocation
    || (window.BudgetApp && window.BudgetApp.computeDisponiblePourAllocation)
    || (() => Infinity);

  // ---------------------------------------------------------------------------
  // Styles & petits composants partagés (mêmes tokens que loans-panel.js)
  // ---------------------------------------------------------------------------

  const line = C.line || '#DED6C4';
  const ink = C.ink || '#232A2E';
  const inkSoft = C.inkSoft || '#6B7278';
  const pine = C.pine || '#2F5D50';
  const pineSoft = C.pineSoft || '#E3ECE8';
  const gold = C.gold || '#93802E';
  const goldSoft = C.goldSoft || '#F0EAD3';
  const brick = C.brick || '#A8503C';
  const paper = C.paper || '#F6F3EC';
  const panel = C.panel || '#FFFFFF';
  const panelAlt = C.panelAlt || '#EFEAE0';

  const inputBox = { border: `1px solid ${line}`, borderRadius: 7, padding: '2px 6px', background: '#fff' };
  const buttonBase = { fontSize: 12.5, borderRadius: 7, padding: '7px 12px', cursor: 'pointer', fontWeight: 600, border: 'none' };
  const primaryButton = { ...buttonBase, background: pine, color: '#fff' };
  const softButton = { ...buttonBase, background: pineSoft, color: pine };
  const dangerButton = { ...buttonBase, background: 'transparent', color: brick, border: `1px solid ${brick}` };

  function Labeled({ label, hint, children }) {
    return h('div', null,
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

  const STATUS_META = {
    lointain: { label: 'Lointain — laissez faire', color: pine },
    a_securiser: { label: 'À sécuriser', color: gold },
    a_rapatrier: { label: 'À rapatrier vers un support liquide', color: brick },
    echu: { label: 'Échéance imminente ou dépassée', color: brick },
    inconnu: { label: 'Échéance non renseignée', color: inkSoft }
  };

  function StatusBadge({ status }) {
    const meta = STATUS_META[status] || STATUS_META.inconnu;
    return h('span', {
      style: {
        fontSize: 10.5, fontWeight: 700, color: meta.color, background: '#fff',
        border: `1px solid ${meta.color}`, borderRadius: 999, padding: '2px 8px', whiteSpace: 'nowrap'
      }
    }, meta.label);
  }

  // ---------------------------------------------------------------------------
  // Carte d'un objectif
  // ---------------------------------------------------------------------------

  /**
   * @param {object} objectif  ligne brute (rawData.objectifs)
   * @param {object} goal      entrée correspondante calculée par computeGoalReallocation
   *                           (allocations enrichies, liquidPct/illiquidPct, status, gap...)
   */
  function ObjectifCard({ objectif, goal, onOpen }) {
    const targetAmount = goal ? goal.targetAmount : Number(objectif.targetAmount) || 0;
    const currentBalance = goal ? goal.currentBalance : 0;
    const liquidPct = goal ? goal.liquidPct : 0;
    const illiquidPct = goal ? goal.illiquidPct : 0;
    const totalPct = targetAmount > 0 ? Math.round((currentBalance / targetAmount) * 100) : 0;
    // La barre elle-même reste plafonnée à 100 % (un objectif dépassé ne déborde pas visuellement) ;
    // les pourcentages affichés en dessous, eux, restent les valeurs réelles.
    const liquidBarPct = Math.max(0, Math.min(100, liquidPct));
    const illiquidBarPct = Math.max(0, Math.min(100 - liquidBarPct, illiquidPct));
    const accountsLabel = !goal || goal.allocations.length === 0
      ? 'Aucun compte'
      : goal.allocations.length === 1
        ? (goal.allocations[0].placementLabel || 'Compte inconnu')
        : `${goal.allocations.length} comptes`;

    return h('div', {
      onClick: onOpen,
      style: { background: panel, border: `1.5px solid ${line}`, borderRadius: 10, padding: '14px 16px', cursor: 'pointer', boxShadow: '0 2px 4px rgba(0,0,0,0.02)', display: 'flex', flexDirection: 'column', gap: 10 },
      onMouseEnter: e => { e.currentTarget.style.borderColor = pine; },
      onMouseLeave: e => { e.currentTarget.style.borderColor = line; }
    },
    h('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 8 } },
      h('div', { style: { fontFamily: "'Newsreader', serif", fontSize: 17, fontWeight: 700, color: ink } }, objectif.label || '(sans nom)'),
      h('span', { style: { fontSize: 10.5, fontWeight: 700, color: pine, background: pineSoft, borderRadius: 999, padding: '2px 8px', whiteSpace: 'nowrap' } }, accountsLabel)),

    // Barre bicolore : segment liquide (pine) + segment illiquide (gold) + reste (gris)
    h('div', { style: { height: 10, borderRadius: 999, background: panelAlt, overflow: 'hidden', display: 'flex' } },
      h('div', { style: { width: `${liquidBarPct}%`, background: pine } }),
      h('div', { style: { width: `${illiquidBarPct}%`, background: gold } })),

    h('div', { style: { display: 'flex', justifyContent: 'space-between', fontSize: 12.5, color: inkSoft } },
      h('span', null, `${eur(currentBalance)} / ${eur(targetAmount)}`),
      h('span', { style: { fontWeight: 700, color: ink } }, `${totalPct} %`)),

    h('div', { style: { display: 'flex', gap: 14, fontSize: 11, color: inkSoft } },
      h('span', null, h('span', { style: { color: pine, fontWeight: 700 } }, '● '), `${Math.round(liquidPct)} % liquide`),
      h('span', null, h('span', { style: { color: gold, fontWeight: 700 } }, '● '), `${Math.round(illiquidPct)} % illiquide`)),

    h('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8, borderTop: `1px dashed ${line}`, paddingTop: 8 } },
      h('span', { style: { fontSize: 11, color: inkSoft } },
        goal && goal.monthsRemaining !== null
          ? (goal.monthsRemaining > 0 ? `Échéance dans ${goal.monthsRemaining} mois` : goal.monthsRemaining === 0 ? 'Échéance ce mois-ci' : `Échéance dépassée depuis ${Math.abs(goal.monthsRemaining)} mois`)
          : 'Échéance non renseignée'),
      h(StatusBadge, { status: goal ? goal.status : 'inconnu' })));
  }

  // ---------------------------------------------------------------------------
  // Tiroir d'édition : identification + choix des comptes support (multi-comptes)
  // ---------------------------------------------------------------------------

  const toNumber = v => { const n = Number(v); return Number.isFinite(n) ? n : 0; };
  const toTextOrEmpty = v => (v === null || v === undefined) ? '' : v;

  /**
   * Une ligne "compte support" du tiroir : case à cocher + montant réservé si cochée. Le montant
   * saisi est plafonné au disponible réel du compte (solde moins ce que LES AUTRES objectifs y
   * réservent déjà) — c'est le blocage de saisie demandé ; BudgetMutationService revalide la même
   * règle côté serveur en filet de sécurité final.
   */
  function AllocationRow({ placement, allocation, disponible, onToggle, onAmountChange }) {
    const checked = !!allocation;
    const amount = allocation ? toNumber(allocation.amount) : 0;
    return h('div', { style: { display: 'flex', alignItems: 'center', gap: 10, padding: '8px 0', borderBottom: `1px solid ${line}` } },
      h('input', { type: 'checkbox', checked, onChange: () => onToggle(!checked), style: { width: 16, height: 16, cursor: 'pointer' } }),
      h('div', { style: { flex: 1, minWidth: 0 } },
        h('div', { style: { fontSize: 13, fontWeight: 600, color: ink } }, placement.label),
        h('div', { style: { fontSize: 10.5, color: inkSoft } }, `Solde ${eur(placement.balance)} · disponible ${eur(disponible)}`)),
      checked
        ? h('div', { style: { width: 130 } },
          h('div', { style: inputBox },
            h(Field, {
              type: 'number', mono: true, align: 'right', value: amount,
              onChange: v => onAmountChange(Math.max(0, Math.min(toNumber(v), disponible)))
            })))
        : null);
  }

  function ObjectifDrawer({ objectif, isNew, placements, rawData, onChange, onClose, onSaveNew, onRemove }) {
    if (!objectif) return null;
    const allocations = Array.isArray(objectif.allocations) ? objectif.allocations : [];
    const allocationByPlacement = {};
    allocations.forEach(a => { allocationByPlacement[a.placementId] = a; });
    const totalReserved = allocations.reduce((s, a) => s + toNumber(a.amount), 0);
    const targetAmount = toNumber(objectif.targetAmount);

    const setAllocations = next => onChange('allocations', next);

    const toggle = (placement, checked) => {
      if (checked) {
        setAllocations([...allocations, { id: uid(), placementId: placement.id, amount: 0 }]);
      } else {
        setAllocations(allocations.filter(a => a.placementId !== placement.id));
      }
    };
    const changeAmount = (placement, amount) => {
      setAllocations(allocations.map(a => a.placementId === placement.id ? { ...a, amount } : a));
    };

    return h('div', { style: { position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, zIndex: 1000, display: 'flex', justifyContent: 'flex-end', background: 'rgba(0, 0, 0, 0.4)', backdropFilter: 'blur(2px)' } },
      h('div', { style: { flex: 1 }, onClick: onClose }),
      h('div', { style: { width: 560, maxWidth: '92vw', height: '100%', background: panel, boxShadow: '-4px 0 24px rgba(0, 0, 0, 0.15)', display: 'flex', flexDirection: 'column' } },
        h('div', { style: { padding: '18px 24px', borderBottom: `1px solid ${line}`, display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: paper } },
          h('div', null,
            h('h3', { style: { margin: 0, fontFamily: "'Newsreader', serif", fontSize: 20, color: ink } }, isNew ? 'Nouvel objectif' : (objectif.label || 'Objectif')),
            h('div', { style: { fontSize: 12, color: inkSoft, marginTop: 2 } }, 'Montant visé, échéance et comptes support')),
          h('button', { type: 'button', onClick: onClose, 'aria-label': 'Fermer', style: { border: 'none', background: 'transparent', fontSize: 20, cursor: 'pointer', color: inkSoft } }, '✕')),

        h('div', { style: { flex: 1, overflowY: 'auto', padding: 24 } },
          h(Block, { title: '1. Identification' },
            h('div', { style: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 } },
              h(Labeled, { label: 'Libellé' },
                h(Field, { type: 'text', value: toTextOrEmpty(objectif.label), onChange: v => onChange('label', v) })),
              h(Labeled, { label: 'Échéance' },
                h(Field, { type: 'date', value: toTextOrEmpty(objectif.targetDate), onChange: v => onChange('targetDate', v) })),
              h(Labeled, { label: 'Montant visé (€)' },
                h(Field, { type: 'number', mono: true, align: 'right', value: objectif.targetAmount, onChange: v => onChange('targetAmount', toNumber(v)) })),
              h(Labeled, { label: 'Notes' },
                h(Field, { type: 'text', value: toTextOrEmpty(objectif.notes), onChange: v => onChange('notes', v) })))),

          h(Block, { title: '2. Comptes support' },
            h('div', { style: { fontSize: 12, color: inkSoft, marginBottom: 6 } },
              'Cochez un ou plusieurs comptes de la vue Patrimoine et indiquez le montant qui alimente cet objectif sur chacun. Un même compte peut alimenter plusieurs objectifs différents.'),
            (placements || []).length === 0
              ? h(Note, null, 'Aucun compte dans la vue Patrimoine.')
              : (placements || []).map(p => h(AllocationRow, {
                key: p.id, placement: p, allocation: allocationByPlacement[p.id],
                disponible: disponiblePourAllocation()(rawData, p.id, objectif.id),
                onToggle: checked => toggle(p, checked),
                onAmountChange: amount => changeAmount(p, amount)
              })),
            h('div', { style: { display: 'flex', justifyContent: 'space-between', fontSize: 13, fontWeight: 700, color: ink, marginTop: 12, paddingTop: 10, borderTop: `1px solid ${line}` } },
              h('span', null, 'Total réservé'),
              h('span', null, `${eur(totalReserved)} / ${eur(targetAmount)}`)),
            targetAmount > 0 && totalReserved > targetAmount
              ? h(Note, { tone: 'warn' }, `Le total réservé dépasse le montant visé de ${eur(totalReserved - targetAmount)}.`)
              : null)),

        h('div', { style: { padding: '14px 24px', borderTop: `1px solid ${line}`, display: 'flex', justifyContent: 'space-between', gap: 8, background: paper } },
          !isNew ? h('button', { type: 'button', onClick: onRemove, style: dangerButton }, 'Supprimer') : h('div'),
          isNew
            ? h('div', { style: { display: 'flex', gap: 8 } },
              h('button', { type: 'button', onClick: onClose, style: softButton }, 'Annuler'),
              h('button', { type: 'button', onClick: onSaveNew, style: primaryButton }, 'Enregistrer'))
            : h('button', { type: 'button', onClick: onClose, style: primaryButton }, 'Fermer'))));
  }

  // ---------------------------------------------------------------------------
  // Panneau : grille de cartes + tiroir (même orchestration que LoansPanel)
  // ---------------------------------------------------------------------------

  /**
   * @param {object[]} objectifs        rawData.objectifs
   * @param {object[]} goalReallocation sortie de computeGoalReallocation(rawData) — même ordre
   * @param {object} rawData            données complètes (comptes, autres objectifs...) pour la
   *                                    validation du disponible par compte
   * @param {(id:string, field:string, value:any) => Promise|void} onCell
   * @param {() => Promise<object>} onCreateDraft
   * @param {(row:object) => void} onSaveNew
   * @param {(id:string) => void} onRemove
   */
  function ObjectifsPanel({ objectifs, goalReallocation, rawData, onCell, onCreateDraft, onSaveNew, onRemove }) {
    const [selectedId, setSelectedId] = useState(null);
    const [draft, setDraft] = useState(null);
    const [isNew, setIsNew] = useState(false);
    const [open, setOpen] = useState(false);

    const goalById = {};
    (goalReallocation || []).forEach(g => { goalById[g.id] = g; });

    const active = isNew ? draft : (objectifs || []).find(o => o.id === selectedId) || null;

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

    return h(React.Fragment, null,
      (objectifs || []).length === 0
        ? h('div', { style: { color: inkSoft, fontSize: 13, padding: '8px 0 12px' } }, 'Aucun objectif enregistré pour l\'instant.')
        : h('div', { style: { display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 14 } },
          objectifs.map(o => h(ObjectifCard, {
            key: o.id, objectif: o, goal: goalById[o.id],
            onOpen: () => { setSelectedId(o.id); setIsNew(false); setDraft(null); setOpen(true); }
          }))),
      h('button', { type: 'button', onClick: add, style: { ...softButton, marginTop: 14, display: 'flex', alignItems: 'center', gap: 6 } }, '+ Ajouter un objectif'),
      open && active
        ? h(ObjectifDrawer, {
          objectif: active, isNew, placements: rawData?.placements || [], rawData,
          onChange: change,
          onClose: close,
          onSaveNew: () => { onSaveNew(draft); close(); },
          onRemove: () => {
            if (window.confirm(`Êtes-vous sûr de vouloir supprimer l'objectif « ${active.label} » ?`)) {
              onRemove(active.id);
              close();
            }
          }
        })
        : null);
  }

  exports.ObjectifsPanel = ObjectifsPanel;
})(typeof window !== 'undefined' ? (window.BudgetApp = window.BudgetApp || {}) : module.exports);
