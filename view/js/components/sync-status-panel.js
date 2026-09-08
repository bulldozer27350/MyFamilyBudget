/**
 * Indicateur visuel de connectivité + panneau de revue de la file "à valider"
 * (voir sync-status.js pour le détail des deux files : auto / validate).
 */
(function (exports) {
  'use strict';

  const {
    useState,
    useEffect,
    useCallback
  } = React;
  const {
    C
  } = exports.C ? exports : window.BudgetApp || {};

  function getSyncStatus() {
    return (typeof window !== 'undefined' && window.BudgetApp && window.BudgetApp.SyncStatus) || null;
  }

  /**
   * Bandeau compact affiché en haut de la zone de contenu. Ne s'affiche que
   * s'il y a quelque chose à signaler (hors-ligne et/ou modifications à
   * valider) — invisible en fonctionnement normal.
   */
  function SyncStatusBanner({ onOpenReview }) {
    const [state, setState] = useState(() => {
      const s = getSyncStatus();
      return s ? s.getState() : { online: true, autoCount: 0, validateCount: 0 };
    });

    useEffect(() => {
      const s = getSyncStatus();
      if (!s) return undefined;
      setState(s.getState());
      return s.subscribe(setState);
    }, []);

    if (state.online && state.autoCount === 0 && state.validateCount === 0) {
      return null;
    }

    const isOffline = !state.online;
    const bg = isOffline ? (C?.brickSoft || '#F4E4DF') : (C?.goldSoft || '#F0EAD3');
    const fg = isOffline ? (C?.brick || '#A8503C') : (C?.gold || '#93802E');

    let message;
    if (isOffline) {
      const total = state.autoCount + state.validateCount;
      message = total > 0
        ? `Hors-ligne — ${total} modification${total > 1 ? 's' : ''} en attente d'envoi au serveur`
        : "Hors-ligne — connexion au serveur indisponible";
    } else {
      message = `${state.validateCount} modification${state.validateCount > 1 ? 's' : ''} à valider avant envoi au serveur`;
    }

    return React.createElement('div', {
      style: {
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        flexWrap: 'wrap',
        gap: 10,
        padding: '9px 16px',
        marginBottom: 16,
        background: bg,
        color: fg,
        borderRadius: 8,
        border: `1px solid ${fg}`,
        fontSize: 13,
        fontWeight: 600
      }
    },
      React.createElement('div', {
        style: { display: 'flex', alignItems: 'center', gap: 9 }
      },
        React.createElement('span', {
          "aria-hidden": "true",
          style: {
            width: 8,
            height: 8,
            borderRadius: '50%',
            background: fg,
            display: 'inline-block',
            flexShrink: 0
          }
        }),
        React.createElement('span', null, message)
      ),
      state.validateCount > 0 && React.createElement('button', {
        type: 'button',
        onClick: onOpenReview,
        style: {
          background: 'transparent',
          border: `1px solid ${fg}`,
          color: fg,
          borderRadius: 6,
          padding: '4px 12px',
          fontSize: 12,
          fontWeight: 700,
          cursor: 'pointer'
        }
      }, 'Vérifier')
    );
  }

  /**
   * Compare deux objets champ par champ (niveau racine) et retourne les
   * champs dont la valeur diffère. Suffisant pour les enregistrements
   * relativement plats manipulés ici (ligne de patrimoine, données de
   * retraite) sans avoir à implémenter un différentiel générique récursif.
   */
  function diffFields(before, after) {
    const beforeObj = before && typeof before === 'object' ? before : {};
    const afterObj = after && typeof after === 'object' ? after : {};
    const keys = new Set([...Object.keys(beforeObj), ...Object.keys(afterObj)]);
    const rows = [];
    keys.forEach(function (k) {
      const a = beforeObj[k];
      const b = afterObj[k];
      if (JSON.stringify(a) !== JSON.stringify(b)) {
        rows.push({ field: k, local: a, server: b });
      }
    });
    return rows;
  }

  function formatDiffValue(v) {
    if (v === undefined || v === null) return '—';
    if (typeof v === 'object') return JSON.stringify(v);
    return String(v);
  }

  /**
   * Panneau de revue de la file "à valider" : remplacements complets
   * d'objet/ligne, jamais rejoués sans confirmation explicite. Pour chaque
   * entrée, l'utilisateur peut vérifier s'il y a eu dérive côté serveur
   * pendant la coupure, puis choisir d'envoyer, de forcer l'envoi malgré la
   * dérive détectée, ou d'abandonner sa modification locale.
   */
  function SyncReviewModal({ isOpen, onClose }) {
    const [items, setItems] = useState([]);
    const [driftById, setDriftById] = useState({});
    const [busyId, setBusyId] = useState(null);

    const refresh = useCallback(function () {
      const s = getSyncStatus();
      setItems(s ? s.getValidateQueue() : []);
    }, []);

    useEffect(() => {
      if (!isOpen) return undefined;
      refresh();
      const s = getSyncStatus();
      if (!s) return undefined;
      return s.subscribe(refresh);
    }, [isOpen, refresh]);

    if (!isOpen) return null;

    const syncStatus = getSyncStatus();

    function clearDrift(id) {
      setDriftById(function (prev) {
        const next = Object.assign({}, prev);
        delete next[id];
        return next;
      });
    }

    async function handleCheck(id) {
      if (!syncStatus) return;
      setBusyId(id);
      try {
        const res = await syncStatus.resolveValidateItem(id, {
          onDrift: function (serverValue) {
            setDriftById(function (prev) {
              return Object.assign({}, prev, { [id]: serverValue });
            });
          }
        });
        if (res && res.sent) clearDrift(id);
      } finally {
        setBusyId(null);
        refresh();
      }
    }

    async function handleForceSend(id) {
      if (!syncStatus) return;
      setBusyId(id);
      try {
        await syncStatus.resolveValidateItem(id, { force: true });
        clearDrift(id);
      } finally {
        setBusyId(null);
        refresh();
      }
    }

    function handleDiscard(id) {
      if (!syncStatus) return;
      syncStatus.discardValidateItem(id);
      clearDrift(id);
      refresh();
    }

    return React.createElement('div', {
      style: {
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        background: 'rgba(35, 42, 46, 0.65)',
        backdropFilter: 'blur(3px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 9999,
        padding: 20
      },
      onClick: onClose
    },
      React.createElement('div', {
        style: {
          background: C?.paper || '#F6F3EC',
          borderRadius: 12,
          width: '100%',
          maxWidth: 720,
          maxHeight: '85vh',
          display: 'flex',
          flexDirection: 'column',
          boxShadow: '0 20px 40px rgba(0,0,0,0.25)',
          border: `1px solid ${C?.line || '#DED6C4'}`,
          overflow: 'hidden'
        },
        onClick: function (e) { e.stopPropagation(); }
      },
        React.createElement('div', {
          style: {
            background: C?.navy || '#28394A',
            color: '#fff',
            padding: '16px 24px',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center'
          }
        },
          React.createElement('div', null,
            React.createElement('div', {
              style: { fontFamily: "'Newsreader', serif", fontSize: 20, fontWeight: 600 }
            }, 'Modifications à valider'),
            React.createElement('div', {
              style: { fontSize: 12, color: '#9FB0BE' }
            }, "Ces modifications n'ont pas encore été envoyées au serveur")
          ),
          React.createElement('button', {
            onClick: onClose,
            style: {
              background: 'none',
              border: 'none',
              color: '#fff',
              fontSize: 22,
              cursor: 'pointer',
              padding: '0 8px',
              opacity: 0.8
            }
          }, '✕')
        ),
        React.createElement('div', {
          style: { padding: 20, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 16 }
        },
          items.length === 0
            ? React.createElement('div', {
                style: { color: C?.inkSoft || '#6B7278', textAlign: 'center', padding: '20px 0' }
              }, 'Aucune modification en attente de validation.')
            : items.map(function (item) {
                const drift = driftById[item.id];
                const rows = drift ? diffFields(item.beforeSnapshot, drift) : [];
                const isBusy = busyId === item.id;
                return React.createElement('div', {
                  key: item.id,
                  style: {
                    border: `1px solid ${C?.line || '#DED6C4'}`,
                    borderRadius: 8,
                    padding: 14,
                    background: C?.panel || '#FFFFFF'
                  }
                },
                  React.createElement('div', { style: { fontWeight: 600, marginBottom: 4 } }, item.description),
                  React.createElement('div', {
                    style: { fontSize: 12, color: C?.inkSoft || '#6B7278', marginBottom: 10 }
                  }, 'Modifié le ' + new Date(item.createdAt).toLocaleString('fr-FR')),
                  drift && React.createElement('div', {
                    style: {
                      background: C?.brickSoft || '#F4E4DF',
                      border: `1px solid ${C?.brick || '#A8503C'}`,
                      borderRadius: 6,
                      padding: 10,
                      marginBottom: 10,
                      fontSize: 12
                    }
                  },
                    React.createElement('div', {
                      style: { fontWeight: 700, color: C?.brick || '#A8503C', marginBottom: 6 }
                    }, '⚠️ Cette donnée a été modifiée sur le serveur depuis votre coupure'),
                    rows.length > 0
                      ? React.createElement('table', { style: { width: '100%', borderCollapse: 'collapse' } },
                          React.createElement('thead', null,
                            React.createElement('tr', null,
                              React.createElement('th', { style: { textAlign: 'left', padding: '2px 6px' } }, 'Champ'),
                              React.createElement('th', { style: { textAlign: 'left', padding: '2px 6px' } }, 'Votre version'),
                              React.createElement('th', { style: { textAlign: 'left', padding: '2px 6px' } }, 'Version serveur')
                            )
                          ),
                          React.createElement('tbody', null,
                            rows.map(function (r) {
                              return React.createElement('tr', { key: r.field },
                                React.createElement('td', { style: { padding: '2px 6px', fontWeight: 600 } }, r.field),
                                React.createElement('td', { style: { padding: '2px 6px' } }, formatDiffValue(r.local)),
                                React.createElement('td', { style: { padding: '2px 6px' } }, formatDiffValue(r.server))
                              );
                            })
                          )
                        )
                      : React.createElement('div', null, 'Différence détectée dans des champs calculés ou imbriqués.')
                  ),
                  React.createElement('div', { style: { display: 'flex', gap: 8, flexWrap: 'wrap' } },
                    !drift && React.createElement('button', {
                      type: 'button',
                      disabled: isBusy,
                      onClick: function () { handleCheck(item.id); },
                      style: {
                        background: C?.pine || '#2F5D50',
                        color: '#fff',
                        border: 'none',
                        borderRadius: 6,
                        padding: '6px 12px',
                        fontSize: 12,
                        fontWeight: 700,
                        cursor: 'pointer',
                        opacity: isBusy ? 0.6 : 1
                      }
                    }, isBusy ? 'Vérification…' : 'Vérifier & envoyer'),
                    drift && React.createElement('button', {
                      type: 'button',
                      disabled: isBusy,
                      onClick: function () { handleForceSend(item.id); },
                      style: {
                        background: C?.brick || '#A8503C',
                        color: '#fff',
                        border: 'none',
                        borderRadius: 6,
                        padding: '6px 12px',
                        fontSize: 12,
                        fontWeight: 700,
                        cursor: 'pointer',
                        opacity: isBusy ? 0.6 : 1
                      }
                    }, 'Envoyer quand même (écrase le serveur)'),
                    React.createElement('button', {
                      type: 'button',
                      disabled: isBusy,
                      onClick: function () { handleDiscard(item.id); },
                      style: {
                        background: 'transparent',
                        color: C?.inkSoft || '#6B7278',
                        border: `1px solid ${C?.line || '#DED6C4'}`,
                        borderRadius: 6,
                        padding: '6px 12px',
                        fontSize: 12,
                        fontWeight: 600,
                        cursor: 'pointer'
                      }
                    }, drift ? 'Garder la version du serveur (abandonner ma modif)' : 'Abandonner ma modification')
                  )
                );
              })
        )
      )
    );
  }

  exports.SyncStatusBanner = SyncStatusBanner;
  exports.SyncReviewModal = SyncReviewModal;
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);
