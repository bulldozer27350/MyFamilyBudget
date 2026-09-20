/**
 * Panneau « Analyse des prêts » de l'onglet Analyse › Fiscal & Prêts.
 *
 * Trois blocs, alimentés par le back-end (aucun calcul ici) :
 *  - Marché : taux publics (Livret A / LDDS / LEP, taux moyen des crédits immobiliers, courbe BCE),
 *    avec leur fraîcheur et un bouton de rafraîchissement ;
 *  - Hypothèses : seuils de l'analyse, modifiables et enregistrés côté serveur ;
 *  - Prêts en cours : verdict « rembourser ? » et « renégocier ? » pour chaque prêt.
 *
 * Rien n'est appliqué automatiquement : ces données sont des repères, l'utilisateur décide.
 * Si le back-end est injoignable, le panneau affiche le contenu de repli fourni par la vue
 * (l'ancien calcul local).
 */
(function (exports) {
  'use strict';

  const {
    useState,
    useEffect,
    useCallback
  } = React;
  const h = React.createElement;
  const {
    C,
    eur
  } = exports.C ? exports : window.BudgetApp || {};
  const {
    SectionCard
  } = exports.SectionCard ? exports : window.BudgetApp || {};

  // ---------------------------------------------------------------------------
  // Utilitaires purs (exposés pour les tests)
  // ---------------------------------------------------------------------------

  const percentFormat = new Intl.NumberFormat('fr-FR', {
    style: 'percent',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });

  /** 0.0321 → « 3,21 % » ; absent → « — ». */
  function pct(fraction) {
    return fraction === null || fraction === undefined ? '—' : percentFormat.format(fraction);
  }

  /** Taux en composition continue (BCE) → taux annuel effectif, comparable à un livret. */
  function effectiveAnnual(continuousRate) {
    return continuousRate === null || continuousRate === undefined ? null : Math.exp(continuousRate) - 1;
  }

  /** « 3,5 » ou « 3.5 » → 3.5 ; vide → null ; illisible → undefined. */
  function parseDecimal(text) {
    const cleaned = String(text === null || text === undefined ? '' : text).trim().replace(/\s/g, '').replace(',', '.');
    if (cleaned === '') return null;
    if (!/^-?\d+(\.\d+)?$/.test(cleaned)) return undefined;
    return Number(cleaned);
  }

  /** Nombre → texte de saisie français (« 1500 », « 0,7 »). */
  function numberToText(n) {
    return n === null || n === undefined ? '' : String(n).replace('.', ',');
  }

  /** Fraction → pourcentage de saisie : 0.007 → « 0,7 ». */
  function fractionToPercentText(fraction) {
    return fraction === null || fraction === undefined ? '' : numberToText(Number((fraction * 100).toFixed(4)));
  }

  const FIELDS = [{
    key: 'marketRate',
    label: 'Taux de marché des crédits (saisie manuelle)',
    kind: 'percent',
    unit: '%',
    optional: true,
    hint: 'Laisser vide pour utiliser le taux de la Banque de France.'
  }, {
    key: 'repayMarginRate',
    label: 'Marge pour trancher sur un remboursement',
    kind: 'percent',
    unit: 'pt',
    hint: 'Écart entre le coût du prêt et le rendement de l\'épargne sous lequel on juge « équivalent ».'
  }, {
    key: 'renegotiationMinGapRate',
    label: 'Écart minimal pour renégocier',
    kind: 'percent',
    unit: 'pt',
    hint: 'Écart entre le taux du prêt et le taux du marché.'
  }, {
    key: 'renegotiationMinCrd',
    label: 'Capital restant dû minimal',
    kind: 'amount',
    unit: '€',
    hint: 'En dessous, les frais absorbent l\'économie.'
  }, {
    key: 'renegotiationMinRemainingMonths',
    label: 'Durée restante minimale',
    kind: 'int',
    unit: 'mois'
  }, {
    key: 'renegotiationFixedCosts',
    label: 'Frais fixes de renégociation',
    kind: 'amount',
    unit: '€',
    hint: 'Frais de dossier, garantie, mainlevée (hors indemnité de remboursement anticipé).'
  }, {
    key: 'flatTaxRate',
    label: 'Fiscalité des placements hors livrets',
    kind: 'percent',
    unit: '%',
    hint: 'Appliquée au rendement d\'un fonds en euros (PFU 30 % par défaut).'
  }];

  /** Valeurs serveur (fractions) → texte des champs de saisie. */
  function valuesToForm(values) {
    const form = {};
    FIELDS.forEach(f => {
      const v = values ? values[f.key] : null;
      form[f.key] = f.kind === 'percent' ? fractionToPercentText(v) : numberToText(v);
    });
    return form;
  }

  /**
   * Champs de saisie → valeurs serveur. Renvoie { values } ou { error } (message français).
   * Les bornes détaillées sont vérifiées par le serveur (réponse 400 avec message explicite).
   */
  function formToValues(form) {
    const values = {};
    for (const f of FIELDS) {
      const n = parseDecimal(form[f.key]);
      if (n === undefined) return {
        error: `« ${f.label} » : valeur illisible.`
      };
      if (n === null) {
        if (f.optional) continue;
        return {
          error: `« ${f.label} » : valeur obligatoire.`
        };
      }
      if (f.kind === 'int' && !Number.isInteger(n)) return {
        error: `« ${f.label} » : nombre entier attendu.`
      };
      values[f.key] = f.kind === 'percent' ? Math.round(n * 1e4) / 1e6 : n;
    }
    return {
      values
    };
  }

  const REPAY_META = {
    REMBOURSER: {
      label: 'À envisager : rembourser',
      tone: 'brick'
    },
    CONSERVER: {
      label: 'Conserver le prêt',
      tone: 'pine'
    },
    NEUTRE: {
      label: 'Écart trop faible pour trancher',
      tone: 'inkSoft'
    },
    INCONNU: {
      label: 'Non évaluable',
      tone: 'inkSoft'
    }
  };
  const RENEGOTIATION_META = {
    RENEGOCIER: {
      label: 'Renégociation rentable',
      tone: 'brick'
    },
    NON_RENTABLE: {
      label: 'Renégociation non rentable',
      tone: 'inkSoft'
    },
    TAUX_PROCHE_MARCHE: {
      label: 'Taux proche du marché',
      tone: 'pine'
    },
    NON_ELIGIBLE: {
      label: 'Non éligible',
      tone: 'inkSoft'
    },
    INCONNU: {
      label: 'Taux de marché inconnu',
      tone: 'inkSoft'
    }
  };
  const STATUS_TONE = {
    FRESH: 'pine',
    STALE: 'gold',
    UNAVAILABLE: 'inkSoft',
    NOT_CONFIGURED: 'inkSoft'
  };
  const FALLBACK_COLORS = {
    brick: '#A8503C',
    pine: '#2F5D50',
    gold: '#93802E',
    inkSoft: '#6B7278',
    ink: '#232A2E',
    line: '#DED6C4'
  };
  const color = tone => C && C[tone] || FALLBACK_COLORS[tone] || FALLBACK_COLORS.inkSoft;

  // ---------------------------------------------------------------------------
  // Petits composants d'affichage
  // ---------------------------------------------------------------------------

  const smallText = {
    fontSize: 11.5,
    color: color('inkSoft'),
    marginTop: 2
  };

  function Row({
    label,
    value,
    message,
    tone
  }) {
    return h('div', {
      style: {
        display: 'flex',
        justifyContent: 'space-between',
        gap: 14,
        padding: '8px 0',
        borderBottom: `1px solid ${color('line')}`,
        flexWrap: 'wrap'
      }
    }, h('div', null, h('div', {
      style: {
        fontWeight: 600,
        fontSize: 13.5,
        color: color('ink')
      }
    }, label), message ? h('div', {
      style: {
        ...smallText,
        color: color(tone || 'inkSoft')
      }
    }, message) : null), h('div', {
      style: {
        fontWeight: 600,
        fontSize: 14,
        color: color('ink'),
        whiteSpace: 'nowrap'
      }
    }, value));
  }

  function Button({
    onClick,
    disabled,
    children,
    primary
  }) {
    return h('button', {
      type: 'button',
      onClick,
      disabled,
      style: {
        padding: '7px 14px',
        borderRadius: 7,
        border: `1px solid ${primary ? color('pine') : color('line')}`,
        background: primary ? color('pine') : '#FFFFFF',
        color: primary ? '#FFFFFF' : color('ink'),
        fontSize: 13,
        cursor: disabled ? 'default' : 'pointer',
        opacity: disabled ? 0.6 : 1
      }
    }, children);
  }

  // ---------------------------------------------------------------------------
  // Bloc « Marché »
  // ---------------------------------------------------------------------------

  function MarketBlock({
    market,
    onRefresh,
    refreshing
  }) {
    const reg = market && market.reglementes || {};
    const credit = market && market.creditImmobilier || {};
    const curve = market && market.courbeTaux || {};
    const hasCurve = curve.status && curve.status !== 'UNAVAILABLE' || curve.spot2y !== undefined && curve.spot2y !== null;
    return h(SectionCard, {
      title: 'Marché',
      subtitle: 'Données publiques, indicatives : rien n\'est appliqué automatiquement à vos taux.',
      right: h(Button, {
        onClick: onRefresh,
        disabled: refreshing
      }, refreshing ? 'Actualisation…' : 'Actualiser')
    }, market ? h(React.Fragment, null, h(Row, {
      label: 'Livret A / LDDS',
      value: pct(reg.livretA),
      message: reg.message,
      tone: STATUS_TONE[reg.status]
    }), h(Row, {
      label: 'LEP',
      value: pct(reg.lep)
    }), h(Row, {
      label: 'Crédit immobilier (taux moyen, hors renégociations)',
      value: pct(credit.rate),
      message: credit.message,
      tone: STATUS_TONE[credit.status]
    }), h(Row, {
      label: 'Courbe des taux BCE — spot 2 ans / 10 ans',
      value: hasCurve ? `${pct(effectiveAnnual(curve.spot2y))} / ${pct(effectiveAnnual(curve.spot10y))}` : '—',
      message: curve.message,
      tone: STATUS_TONE[curve.status]
    }), hasCurve ? h(Row, {
      label: 'Taux courts anticipés par le marché — dans 1 / 2 / 5 / 10 ans',
      value: [curve.forward1y, curve.forward2y, curve.forward5y, curve.forward10y].map(v => pct(effectiveAnnual(v))).join(' / '),
      message: 'Taux forwards convertis en taux annuel effectif : le consensus implicite du marché, pas une certitude.'
    }) : null, market.lastRefreshError ? h('div', {
      style: {
        ...smallText,
        color: color('brick'),
        marginTop: 8
      }
    }, 'Dernière actualisation incomplète : ', market.lastRefreshError) : null) : h('div', {
      style: smallText
    }, 'Données de marché indisponibles.'));
  }

  // ---------------------------------------------------------------------------
  // Bloc « Hypothèses »
  // ---------------------------------------------------------------------------

  function HypothesesBlock({
    form,
    onChange,
    onSave,
    onReset,
    saving,
    message,
    error
  }) {
    return h(SectionCard, {
      title: 'Hypothèses de l\'analyse',
      subtitle: 'Seuils modifiables, enregistrés sur le serveur. Les valeurs par défaut sont des repères courants.',
      collapsible: true,
      defaultCollapsed: true
    }, h('div', {
      style: {
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))',
        gap: 14
      }
    }, FIELDS.map(f => h('label', {
      key: f.key,
      style: {
        display: 'block',
        fontSize: 12.5,
        color: color('ink')
      }
    }, f.label, h('div', {
      style: {
        display: 'flex',
        alignItems: 'center',
        gap: 6,
        marginTop: 4
      }
    }, h('input', {
      type: 'text',
      inputMode: 'decimal',
      value: form[f.key] === undefined ? '' : form[f.key],
      onChange: e => onChange(f.key, e.target.value),
      'aria-label': f.label,
      style: {
        border: `1px solid ${color('line')}`,
        borderRadius: 7,
        padding: '7px 10px',
        fontSize: 14,
        width: 130
      }
    }), h('span', {
      style: smallText
    }, f.unit)), f.hint ? h('div', {
      style: smallText
    }, f.hint) : null))), h('div', {
      style: {
        display: 'flex',
        gap: 10,
        alignItems: 'center',
        marginTop: 16,
        flexWrap: 'wrap'
      }
    }, h(Button, {
      onClick: onSave,
      disabled: saving,
      primary: true
    }, saving ? 'Enregistrement…' : 'Enregistrer'), h(Button, {
      onClick: onReset,
      disabled: saving
    }, 'Valeurs par défaut'), error ? h('span', {
      role: 'alert',
      style: {
        fontSize: 12.5,
        color: color('brick')
      }
    }, error) : null, !error && message ? h('span', {
      style: {
        fontSize: 12.5,
        color: color('pine')
      }
    }, message) : null));
  }

  // ---------------------------------------------------------------------------
  // Bloc « Prêts en cours »
  // ---------------------------------------------------------------------------

  function VerdictLine({
    title,
    meta,
    reason,
    children
  }) {
    return h('div', {
      style: {
        marginTop: 8
      }
    }, h('div', {
      style: {
        display: 'flex',
        gap: 8,
        alignItems: 'baseline',
        flexWrap: 'wrap'
      }
    }, h('span', {
      style: {
        fontSize: 12,
        color: color('inkSoft')
      }
    }, title), h('span', {
      style: {
        fontSize: 12.5,
        fontWeight: 600,
        color: color(meta.tone)
      }
    }, meta.label)), reason ? h('div', {
      style: smallText
    }, reason) : null, children || null);
  }

  function LoanCard({
    loan
  }) {
    const rep = loan.repayment || {};
    const reneg = loan.renegotiation || {};
    const repayMeta = REPAY_META[rep.verdict] || REPAY_META.INCONNU;
    const renegMeta = RENEGOTIATION_META[reneg.verdict] || RENEGOTIATION_META.INCONNU;
    const duration = loan.remainingMonths === null || loan.remainingMonths === undefined ? 'durée indéterminée' : `${loan.remainingMonths} mois restants`;
    return h('div', {
      style: {
        padding: '12px 14px',
        border: `1px solid ${color('line')}`,
        borderRadius: 8,
        marginBottom: 10
      }
    }, h('div', {
      style: {
        fontWeight: 600,
        fontSize: 14,
        color: color('ink')
      }
    }, loan.label, ' ', h('span', {
      style: {
        fontWeight: 400,
        color: color('inkSoft'),
        fontSize: 12
      }
    }, '(', pct(loan.rate), ')')), h('div', {
      style: smallText
    }, `Capital restant dû ${eur(loan.crd)} · mensualité ${eur(loan.monthly)} (dont assurance ${eur(loan.insurance)}) · ${duration}`, loan.remainingInterest !== null && loan.remainingInterest !== undefined ? ` · intérêts restants ${eur(loan.remainingInterest)}` : '', ` · IRA estimée ${eur(loan.earlyRepaymentIndemnity)}`), h(VerdictLine, {
      title: 'Rembourser plus vite ?',
      meta: repayMeta,
      reason: rep.reason
    }), h(VerdictLine, {
      title: 'Renégocier ?',
      meta: renegMeta,
      reason: reneg.reason
    }, reneg.verdict === 'RENEGOCIER' ? h('div', {
      style: {
        ...smallText,
        color: color('ink')
      }
    }, `Économie nette estimée ${eur(reneg.netSaving)} · nouvelle mensualité hors assurance ${eur(reneg.newMonthly)} (${eur(reneg.monthlyGain)} de moins par mois)`) : null));
  }

  function LoansBlock({
    advice
  }) {
    const loans = advice.loans || [];
    return h(SectionCard, {
      title: 'Prêts en cours — analyse',
      subtitle: advice.marketRateUsed !== null && advice.marketRateUsed !== undefined ? `Taux de marché retenu : ${pct(advice.marketRateUsed)}${advice.marketRateSource ? ' (' + advice.marketRateSource + ')' : ''}` : 'Taux de marché non renseigné : la renégociation n\'est pas évaluée. Saisissez-le dans les hypothèses ou configurez la clé Banque de France.'
    }, loans.length === 0 ? h('div', {
      style: smallText
    }, 'Aucun prêt en cours enregistré.') : loans.map(l => h(LoanCard, {
      key: l.id || l.label,
      loan: l
    })), (advice.notes || []).length > 0 ? h('ul', {
      style: {
        margin: '10px 0 0',
        paddingLeft: 18
      }
    }, advice.notes.map((n, i) => h('li', {
      key: i,
      style: smallText
    }, n))) : null);
  }

  // ---------------------------------------------------------------------------
  // Panneau complet
  // ---------------------------------------------------------------------------

  function LoanAdvicePanel({
    fallback
  }) {
    const [status, setStatus] = useState('loading'); // loading | ready | unavailable
    const [advice, setAdvice] = useState(null);
    const [market, setMarket] = useState(null);
    const [params, setParams] = useState(null);
    const [form, setForm] = useState({});
    const [saving, setSaving] = useState(false);
    const [refreshing, setRefreshing] = useState(false);
    const [message, setMessage] = useState('');
    const [error, setError] = useState('');
    const getApi = () => (window.BudgetApp || exports).BudgetApi;
    useEffect(() => {
      let cancelled = false;
      (async () => {
        const api = getApi();
        try {
          const [a, m, p] = await Promise.all([api.getAnalysePrets(), api.getTauxMarche(), api.getAnalysePretsParametres()]);
          if (cancelled) return;
          if (!a) {
            setStatus('unavailable');
            return;
          }
          setAdvice(a);
          setMarket(m);
          setParams(p);
          setForm(valuesToForm(p && p.values));
          setStatus('ready');
        } catch (e) {
          if (!cancelled) setStatus('unavailable');
        }
      })();
      return () => {
        cancelled = true;
      };
    }, []);
    const reloadAdvice = useCallback(async () => {
      const a = await getApi().getAnalysePrets();
      if (a) setAdvice(a);
    }, []);
    const onChange = (key, value) => {
      setForm(prev => ({
        ...prev,
        [key]: value
      }));
      setMessage('');
      setError('');
    };
    const onSave = async () => {
      const parsed = formToValues(form);
      if (parsed.error) {
        setError(parsed.error);
        setMessage('');
        return;
      }
      setSaving(true);
      setError('');
      setMessage('');
      try {
        const saved = await getApi().saveAnalysePretsParametres(parsed.values);
        setParams(saved);
        setForm(valuesToForm(saved && saved.values));
        await reloadAdvice();
        setMessage('Hypothèses enregistrées.');
      } catch (e) {
        setError(e && e.message ? e.message : 'Enregistrement impossible.');
      } finally {
        setSaving(false);
      }
    };
    const onReset = () => {
      setForm(valuesToForm(params && params.defaults));
      setError('');
      setMessage('Valeurs par défaut chargées : cliquez sur « Enregistrer » pour les appliquer.');
    };
    const onRefresh = async () => {
      setRefreshing(true);
      try {
        const m = await getApi().refreshTauxMarche();
        if (m) setMarket(m);
        await reloadAdvice();
      } finally {
        setRefreshing(false);
      }
    };
    if (status === 'unavailable') return fallback || null;
    if (status === 'loading') {
      return h('div', {
        style: smallText
      }, 'Chargement de l\'analyse des prêts…');
    }
    return h(React.Fragment, null, h(MarketBlock, {
      market,
      onRefresh,
      refreshing
    }), h(HypothesesBlock, {
      form,
      onChange,
      onSave,
      onReset,
      saving,
      message,
      error
    }), h(LoansBlock, {
      advice
    }));
  }
  exports.LoanAdvicePanel = LoanAdvicePanel;
  exports.LoanAdviceUi = {
    pct,
    effectiveAnnual,
    parseDecimal,
    numberToText,
    fractionToPercentText,
    valuesToForm,
    formToValues,
    FIELDS,
    REPAY_META,
    RENEGOTIATION_META
  };
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);
