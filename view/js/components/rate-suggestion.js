/**
 * Suggestion de taux de rendement pour la fiche d'un placement (Placements › fiche › « 3. Hypothèses
 * de rendement annuel »).
 *
 * Interroge le back-end (GET /patrimoine/suggestions-taux) et affiche, selon le placement :
 *  - une SUGGESTION applicable en un clic (livrets réglementés : taux en vigueur ± une amplitude) ;
 *  - un simple taux de REPÈRE (fonds en euros, obligations) ;
 *  - une ligne discrète expliquant pourquoi il n'y a aucune suggestion (actions, immobilier...).
 *
 * Rien n'est appliqué automatiquement : le bouton « Appliquer » remplit les trois taux, comme si
 * l'utilisateur les avait saisis. Si le back-end est injoignable, le composant n'affiche rien.
 */
(function (exports) {
  'use strict';

  const {
    useState,
    useEffect
  } = React;
  const h = React.createElement;
  const {
    C
  } = exports.C ? exports : window.BudgetApp || {};

  const AMPLITUDE_KEY = 'mfb.rateSuggestion.amplitudePt';
  const AMPLITUDE_CHOICES_PT = [0, 0.5, 1, 1.5, 2, 3];
  const DEFAULT_AMPLITUDE_PT = 1;

  const percentFormat = new Intl.NumberFormat('fr-FR', {
    style: 'percent',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });

  /** 0.017 → « 1,70 % » ; absent → « — ». */
  function pct(fraction) {
    return fraction === null || fraction === undefined || fraction === '' ? '—' : percentFormat.format(Number(fraction));
  }

  /** Deux taux sont identiques à 0,0001 point près (évite les artefacts de virgule flottante). */
  function sameRate(a, b) {
    return Math.abs((Number(a) || 0) - (Number(b) || 0)) < 1e-6;
  }

  /** Amplitude mémorisée dans le navigateur (préférence d'affichage), en points ; défaut 1 pt. */
  function readAmplitudePt() {
    try {
      const stored = Number(window.localStorage.getItem(AMPLITUDE_KEY));
      return AMPLITUDE_CHOICES_PT.includes(stored) && window.localStorage.getItem(AMPLITUDE_KEY) !== null ? stored : DEFAULT_AMPLITUDE_PT;
    } catch (e) {
      return DEFAULT_AMPLITUDE_PT;
    }
  }
  function saveAmplitudePt(value) {
    try {
      window.localStorage.setItem(AMPLITUDE_KEY, String(value));
    } catch (e) {
      // Stockage indisponible : la préférence ne sera simplement pas mémorisée.
    }
  }
  const color = (name, fallback) => C && C[name] || fallback;
  const smallText = {
    fontSize: 11.5,
    color: color('inkSoft', '#6B7278'),
    lineHeight: 1.5
  };
  function RateSuggestion({
    placement,
    onApply
  }) {
    const [amplitudePt, setAmplitudePt] = useState(readAmplitudePt);
    const [result, setResult] = useState(null);
    const [applying, setApplying] = useState(false);
    const [applied, setApplied] = useState(false);
    const id = placement && placement.id;
    const label = placement && placement.label;
    const category = placement && placement.category;
    useEffect(() => {
      let cancelled = false;
      const api = (window.BudgetApp || exports).BudgetApi;
      setResult(null);
      setApplied(false);
      if (!id || !api || !api.getSuggestionsTaux) return undefined;
      (async () => {
        try {
          const res = await api.getSuggestionsTaux(amplitudePt / 100);
          if (cancelled || !res) return;
          setResult((res.suggestions || []).find(s => s.placementId === id) || null);
        } catch (e) {
          // Back-end indisponible : aucune suggestion affichée.
        }
      })();
      return () => {
        cancelled = true;
      };
    }, [id, label, category, amplitudePt]);
    if (!result) return null;
    if (result.kind === 'NONE') {
      return h('div', {
        style: {
          ...smallText,
          marginBottom: 10
        }
      }, 'Pas de suggestion de taux : ', result.basis);
    }
    const box = {
      background: color('pineSoft', '#E3ECE8'),
      border: `1px solid ${color('line', '#DED6C4')}`,
      borderRadius: 8,
      padding: '10px 12px',
      marginBottom: 12
    };
    if (result.kind === 'REFERENCE') {
      return h('div', {
        style: box
      }, h('div', {
        style: {
          fontSize: 12.5,
          fontWeight: 600,
          color: color('ink', '#232A2E')
        }
      }, '💡 Repère de marché : ', pct(result.referenceRate), ' ', h('span', {
        style: {
          fontWeight: 400,
          color: color('inkSoft', '#6B7278')
        }
      }, '(', result.benchmark, ')')), h('div', {
        style: smallText
      }, result.basis), h('div', {
        style: smallText
      }, 'Aucun scénario proposé : ce placement n\'a pas de taux public directement applicable.'));
    }

    // SUGGESTION
    const suggested = {
      ratePess: result.suggestedPess,
      rateCorr: result.suggestedCorr,
      rateOpti: result.suggestedOpti
    };
    const alreadyApplied = sameRate(placement.ratePess, suggested.ratePess) && sameRate(placement.rateCorr, suggested.rateCorr) && sameRate(placement.rateOpti, suggested.rateOpti);
    const onClick = async () => {
      setApplying(true);
      try {
        await onApply(suggested);
        setApplied(true);
      } finally {
        setApplying(false);
      }
    };
    const scenario = (name, tone, suggestedValue, currentValue) => h('div', {
      key: name,
      style: {
        minWidth: 120
      }
    }, h('div', {
      style: {
        fontSize: 11,
        fontWeight: 600,
        color: tone
      }
    }, name), h('div', {
      style: {
        fontSize: 14,
        fontWeight: 600,
        color: color('ink', '#232A2E')
      }
    }, pct(suggestedValue)), h('div', {
      style: smallText
    }, 'actuel : ', pct(currentValue)));
    return h('div', {
      style: box
    }, h('div', {
      style: {
        fontSize: 12.5,
        fontWeight: 600,
        color: color('ink', '#232A2E')
      }
    }, '💡 Suggestion de marché ', h('span', {
      style: {
        fontWeight: 400,
        color: color('inkSoft', '#6B7278')
      }
    }, '(', result.benchmark, ')')), h('div', {
      style: {
        display: 'flex',
        gap: 18,
        flexWrap: 'wrap',
        margin: '8px 0'
      }
    }, scenario('Pessimiste', color('brick', '#A8503C'), suggested.ratePess, placement.ratePess), scenario('Correct', color('pine', '#2F5D50'), suggested.rateCorr, placement.rateCorr), scenario('Optimiste', color('gold', '#93802E'), suggested.rateOpti, placement.rateOpti)), h('div', {
      style: smallText
    }, result.basis), result.caveat ? h('div', {
      style: {
        ...smallText,
        color: color('brick', '#A8503C'),
        marginTop: 4
      }
    }, result.caveat) : null, h('div', {
      style: {
        ...smallText,
        marginTop: 4
      }
    }, 'L\'écart entre taux correct et taux pessimiste/optimiste est une convention, pas une prévision.'), h('div', {
      style: {
        display: 'flex',
        gap: 12,
        alignItems: 'center',
        flexWrap: 'wrap',
        marginTop: 8
      }
    }, h('button', {
      type: 'button',
      onClick,
      disabled: applying || alreadyApplied,
      style: {
        padding: '6px 12px',
        borderRadius: 7,
        border: `1px solid ${color('pine', '#2F5D50')}`,
        background: alreadyApplied ? '#FFFFFF' : color('pine', '#2F5D50'),
        color: alreadyApplied ? color('pine', '#2F5D50') : '#FFFFFF',
        fontSize: 12.5,
        cursor: applying || alreadyApplied ? 'default' : 'pointer',
        opacity: applying ? 0.6 : 1
      }
    }, alreadyApplied ? applied ? 'Appliqué ✓' : 'Déjà à jour' : applying ? 'Application…' : 'Appliquer les 3 taux'), h('label', {
      style: smallText
    }, 'Écart pessimiste / optimiste ± ', h('select', {
      value: amplitudePt,
      onChange: e => {
        const value = Number(e.target.value);
        saveAmplitudePt(value);
        setAmplitudePt(value);
      },
      'aria-label': 'Écart pessimiste et optimiste',
      style: {
        border: `1px solid ${color('line', '#DED6C4')}`,
        borderRadius: 6,
        padding: '3px 6px',
        fontSize: 12
      }
    }, AMPLITUDE_CHOICES_PT.map(v => h('option', {
      key: v,
      value: v
    }, String(v).replace('.', ',') + ' pt'))))));
  }
  exports.RateSuggestion = RateSuggestion;
  exports.RateSuggestionUi = {
    pct,
    sameRate,
    AMPLITUDE_CHOICES_PT
  };
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);
