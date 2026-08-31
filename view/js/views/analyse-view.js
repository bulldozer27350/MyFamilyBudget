/**
 * Vue Analyse (AnalyseView : Analyse Réel vs Prévisionnel, Atterrissage du mois, Dérives par ligne, Historique)
 */
(function (exports) {
  'use strict';

  const {
    useState,
    useMemo,
    useEffect,
    useRef
  } = React;
  const {
    C,
    eur,
    eurExact
  } = exports.C ? exports : window.BudgetApp || {};
  const {
    chargeMonthlyForYear,
    incomeMonthlyForYear,
    computeRealAverages
  } = exports.chargeMonthlyForYear ? exports : window.BudgetApp || {};
  const {
    SectionCard,
    KPI
  } = exports.SectionCard ? exports : window.BudgetApp || {};
  const {
    AllocationChartJS
  } = exports.AllocationChartJS ? exports : window.BudgetApp || {};
  const inputStyle = {
    border: `1px solid ${C?.line || "#DED6C4"}`,
    borderRadius: 7,
    padding: "8px 10px",
    fontSize: 14,
    width: 140
  };
  function multiSelectKindMeta(kind) {
    if (kind === "Revenu" || kind === "revenu") {
      return {
        dot: C?.pine || "#2F5D50"
      };
    }
    if (kind === "placement") {
      return {
        dot: C?.navy || "#28394A"
      };
    }
    return {
      dot: C?.brick || "#A8503C"
    };
  }
  function MultiSelectDropdown({
    items,
    selectedIds,
    onChange,
    width
  }) {
    const [open, setOpen] = useState(false);
    const [search, setSearch] = useState("");
    const containerRef = useRef(null);
    useEffect(() => {
      const onDocClick = e => {
        if (containerRef.current && !containerRef.current.contains(e.target)) setOpen(false);
      };
      document.addEventListener("mousedown", onDocClick);
      return () => document.removeEventListener("mousedown", onDocClick);
    }, []);
    const filteredItems = useMemo(() => {
      if (!search.trim()) return items;
      const q = search.trim().toLowerCase();
      return items.filter(it => (it.label || "").toLowerCase().includes(q));
    }, [items, search]);
    const toggle = id => {
      if (selectedIds.includes(id)) onChange(selectedIds.filter(x => x !== id));else onChange([...selectedIds, id]);
    };
    const buttonLabel = selectedIds.length === 0 ? `Toutes (${items.length})` : `${selectedIds.length} sélectionnée${selectedIds.length > 1 ? "s" : ""}`;
    return /*#__PURE__*/React.createElement("div", {
      ref: containerRef,
      style: {
        position: "relative",
        width: width || 220
      }
    }, /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => setOpen(o => !o),
      style: {
        ...inputStyle,
        width: "100%",
        textAlign: "left",
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        background: C?.panel || "#FFFFFF",
        cursor: "pointer"
      }
    }, /*#__PURE__*/React.createElement("span", {
      style: {
        overflow: "hidden",
        textOverflow: "ellipsis",
        whiteSpace: "nowrap"
      }
    }, buttonLabel), /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 10,
        color: C?.inkSoft || "#6B7278",
        marginLeft: 6
      }
    }, open ? "▲" : "▼")), open && /*#__PURE__*/React.createElement("div", {
      style: {
        position: "absolute",
        top: "calc(100% + 4px)",
        left: 0,
        zIndex: 20,
        width: Math.max(width || 220, 260),
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 8,
        boxShadow: "0 6px 18px rgba(0,0,0,0.12)",
        padding: 10
      }
    }, items.length > 8 && /*#__PURE__*/React.createElement("input", {
      value: search,
      onChange: e => setSearch(e.target.value),
      placeholder: "🔍 Rechercher…",
      style: {
        ...inputStyle,
        width: "100%",
        marginBottom: 8,
        fontSize: 12.5
      }
    }), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        justifyContent: "space-between",
        marginBottom: 6
      }
    }, /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => onChange(items.map(it => it.id)),
      style: {
        border: "none",
        background: "none",
        color: C?.pine || "#2F5D50",
        fontSize: 11.5,
        fontWeight: 600,
        cursor: "pointer",
        padding: 0
      }
    }, "Tout cocher"), /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => onChange([]),
      style: {
        border: "none",
        background: "none",
        color: C?.inkSoft || "#6B7278",
        fontSize: 11.5,
        fontWeight: 600,
        cursor: "pointer",
        padding: 0
      }
    }, "Tout décocher")), /*#__PURE__*/React.createElement("div", {
      style: {
        maxHeight: 240,
        overflowY: "auto"
      }
    }, filteredItems.length === 0 && /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        padding: "6px 2px"
      }
    }, "Aucun résultat."), filteredItems.map(it => /*#__PURE__*/React.createElement("label", {
      key: it.id,
      style: {
        display: "flex",
        alignItems: "center",
        gap: 7,
        padding: "5px 2px",
        fontSize: 12.5,
        cursor: "pointer",
        color: C?.ink || "#232A2E"
      }
    }, /*#__PURE__*/React.createElement("input", {
      type: "checkbox",
      checked: selectedIds.includes(it.id),
      onChange: () => toggle(it.id)
    }), it.kind && /*#__PURE__*/React.createElement("span", {
      style: {
        width: 7,
        height: 7,
        borderRadius: "50%",
        background: multiSelectKindMeta(it.kind).dot,
        flexShrink: 0
      }
    }), /*#__PURE__*/React.createElement("span", {
      style: {
        overflow: "hidden",
        textOverflow: "ellipsis",
        whiteSpace: "nowrap"
      }
    }, it.label))))));
  }
  function AnalyseView({
    data,
    openHelp
  }) {
    const api = exports.BudgetApi || window.BudgetApp?.BudgetApi;
    const [apiData, setApiData] = useState(null);
    const [loading, setLoading] = useState(!data);
    const [monthsBack, setMonthsBack] = useState(12);

    const loadDataFromApi = (currentMonthsBack) => {
      if (api && api.getAnalyse) {
        api.getAnalyse(currentMonthsBack).then(res => {
          setApiData(res);
          setLoading(false);
        }).catch(err => {
          console.error("Erreur chargement analyse via BudgetApi:", err);
          setLoading(false);
        });
      } else if (data) {
        setApiData({
          data,
          bankImport: data?.bankImport || {},
          pendingOperations: data?.bankImport?.pendingOperations || [],
          charges: data?.charges || [],
          incomes: data?.incomes || [],
          placements: data?.placements || [],
          settings: data?.settings || {}
        });
        setLoading(false);
      }
    };

    useEffect(() => {
      loadDataFromApi(monthsBack);
      if (api && api.onAnalyseChanged) {
        const unsub = api.onAnalyseChanged(() => {
          loadDataFromApi(monthsBack);
        });
        return () => unsub && unsub();
      }
      // Se recharge quand la période sélectionnée change, pour que le serveur
      // recalcule kpis / landingData / driftRows / monthlyCompareData / categorySummaries
      // sur la bonne fenêtre temporelle (le sélecteur "monthsBack" pilote /api/v1/analyse).
    }, [monthsBack]);

    /**
     * Vérifie que la réponse serveur contient bien les agrégats calculés par
     * AnalyseCalculator.java (et pas seulement les données brutes retournées
     * par le fallback JS local AnalyseService.buildAnalyse(), qui ne les fournit pas).
     * Un tableau vide reste valide (absence de données ≠ réponse incomplète).
     */
    const isServerAnalyseComplete = ad => !!ad && ad.kpis && typeof ad.kpis === "object" && Array.isArray(ad.landingData) && Array.isArray(ad.driftRows) && Array.isArray(ad.monthlyCompareData) && Array.isArray(ad.categorySummaries);
    const serverDataValid = isServerAnalyseComplete(apiData);
    useEffect(() => {
      if (apiData && !serverDataValid) {
        console.warn("[AnalyseView] Réponse /api/v1/analyse incomplète ou issue du fallback JS local (kpis/landingData/driftRows/monthlyCompareData/categorySummaries manquants) : recalcul local JS utilisé pour les 4 onglets historiques.", apiData);
      }
    }, [apiData, serverDataValid]);

    const rawData = apiData?.data || data;
    const transactions = rawData?.bankImport?.transactions || [];
    const categories = rawData?.bankImport?.categories || [];
    const matchings = rawData?.bankImport?.matchings || [];
    const pendingOperations = apiData?.pendingOperations || rawData?.bankImport?.pendingOperations || [];
    const [driftSortKey, setDriftSortKey] = useState("ecart");
    const [driftSortDir, setDriftSortDir] = useState(-1);
    const [driftSearch, setDriftSearch] = useState("");
    const [activeTab, setActiveTab] = useState("overview");
    const [customDateFrom, setCustomDateFrom] = useState("");
    const [customDateTo, setCustomDateTo] = useState("");
    const [customCategoryIds, setCustomCategoryIds] = useState([]);
    const [customBudgetLineIds, setCustomBudgetLineIds] = useState([]);
    const [customSortKey, setCustomSortKey] = useState("date");
    const [customSortDir, setCustomSortDir] = useState(-1);
    const cutoffISO = useMemo(() => {
      if (!monthsBack) return null;
      const d = new Date();
      d.setMonth(d.getMonth() - monthsBack);
      return d.toISOString().slice(0, 10);
    }, [monthsBack]);
    const periodTx = useMemo(() => cutoffISO ? transactions.filter(t => t.date >= cutoffISO) : transactions, [transactions, cutoffISO]);
    const txMap = useMemo(() => {
      const m = {};
      transactions.forEach(t => { m[t.id] = t; });
      return m;
    }, [transactions]);

    const resolveAmount = (refId) => {
      if (!refId) return 0;
      const hashIdx = refId.indexOf('#');
      if (hashIdx >= 0) {
        const txId = refId.substring(0, hashIdx);
        const splitId = refId.substring(hashIdx + 1);
        const tx = txMap[txId];
        if (tx && Array.isArray(tx.splits)) {
          const split = tx.splits.find(s => s.id === splitId);
          if (split) return Number(split.amount) || 0;
        }
        return 0;
      }
      const tx = txMap[refId];
      return tx ? Number(tx.amount) || 0 : 0;
    };

    const localByCategory = useMemo(() => {
      const map = {};
      periodTx.forEach(t => {
        if (t.splits && t.splits.length > 0) {
          t.splits.forEach(s => {
            const cat = categories.find(c => c.id === s.categoryId);
            if (cat && cat.kind === "Revenu") return;
            const label = cat ? cat.label : "Non catégorisé";
            map[label] = (map[label] || 0) + (-Number(s.amount) || 0);
          });
        } else {
          const cat = categories.find(c => c.id === t.categoryId);
          if (cat && cat.kind === "Revenu") return;
          const label = cat ? cat.label : "Non catégorisé";
          map[label] = (map[label] || 0) + (-Number(t.amount) || 0);
        }
      });
      return Object.entries(map).map(([label, amount]) => ({
        label,
        amount,
        color: amount < 0 ? C?.pine || "#2F5D50" : label === "Non catégorisé" ? C?.inkSoft || "#6B7278" : C?.brick || "#A8503C"
      })).filter(item => Math.abs(item.amount) > 0.01).sort((a, b) => b.amount - a.amount);
    }, [periodTx, categories]);
    const localTotalExpenses = useMemo(() => localByCategory.reduce((s, c) => s + c.amount, 0), [localByCategory]);
    const localTotalIncome = useMemo(() => {
      return periodTx.reduce((s, t) => {
        if (t.splits && t.splits.length > 0) {
          return s + t.splits.reduce((subS, split) => {
            const cat = categories.find(c => c.id === split.categoryId);
            const amt = Number(split.amount) || 0;
            if ((cat && cat.kind === "Revenu") || amt > 0) {
              return subS + (amt > 0 ? amt : 0);
            }
            return subS;
          }, 0);
        }
        const cat = categories.find(c => c.id === t.categoryId);
        const amt = Number(t.amount) || 0;
        if ((cat && cat.kind === "Revenu") || amt > 0) {
          return s + (amt > 0 ? amt : 0);
        }
        return s;
      }, 0);
    }, [periodTx, categories]);
    const localNbMonths = Math.max(1, monthsBack || 1);
    const localUncategorized = periodTx.filter(t => !t.categoryId && (!t.splits || t.splits.length === 0)).length;
    const localCompressibleTotal = useMemo(() => {
      const compressibleIds = new Set(categories.filter(c => c.compressible === "Oui").map(c => c.id));
      return periodTx.reduce((s, t) => {
        if (t.splits && t.splits.length > 0) {
          return s + t.splits.reduce((subS, split) => {
            if (compressibleIds.has(split.categoryId)) return subS + (-Number(split.amount) || 0);
            return subS;
          }, 0);
        }
        if (compressibleIds.has(t.categoryId)) return s + (-Number(t.amount) || 0);
        return s;
      }, 0);
    }, [periodTx, categories]);
    // Priorité aux agrégats calculés par AnalyseCalculator.java (apiData.kpis / categorySummaries)
    // quand la réponse serveur est complète ; recalcul JS local en secours sinon (cf. isServerAnalyseComplete).
    const byCategory = serverDataValid ? apiData.categorySummaries : localByCategory;
    const totalExpenses = serverDataValid ? Number(apiData.kpis.totalExpenses) || 0 : localTotalExpenses;
    const totalIncome = serverDataValid ? Number(apiData.kpis.totalIncome) || 0 : localTotalIncome;
    const nbMonths = serverDataValid ? Math.max(1, Number(apiData.kpis.nbMonths) || 1) : localNbMonths;
    const uncategorized = serverDataValid ? Number(apiData.kpis.uncategorizedCount) || 0 : localUncategorized;
    const compressibleTotal = serverDataValid ? Number(apiData.kpis.compressibleTotal) || 0 : localCompressibleTotal;
    const currentMonthISO = useMemo(() => {
      const n = new Date();
      return `${n.getFullYear()}-${String(n.getMonth() + 1).padStart(2, "0")}`;
    }, []);
    const currentMonthLabel = useMemo(() => {
      const [y, m] = currentMonthISO.split("-");
      return new Date(Number(y), Number(m) - 1, 1).toLocaleDateString("fr-FR", {
        month: "long",
        year: "numeric"
      });
    }, [currentMonthISO]);
    const localLandingData = useMemo(() => {
      const inflationRate = Number(rawData?.settings?.inflationRate) || 0.02;
      const currentYear = new Date().getFullYear();
      const currentMatching = matchings.find(m => m.month === currentMonthISO) || {
        links: []
      };
      const calcCharge = exports.chargeMonthlyForYear || window.BudgetApp && window.BudgetApp.chargeMonthlyForYear || chargeMonthlyForYear;
      const calcIncome = exports.incomeMonthlyForYear || window.BudgetApp && window.BudgetApp.incomeMonthlyForYear || incomeMonthlyForYear;
      const rows = [];
      const processLine = (row, kind) => {
        const budgeted = kind === "charge" ? calcCharge(row, currentYear, inflationRate) : kind === "revenu" ? calcIncome(row, currentYear) : Number(row.monthly) || 0;
        if (budgeted <= 0) return;
        const startStr = kind === "placement" ? row.monthlyFrom : row.start;
        const endStr = kind === "placement" ? row.monthlyUntil : row.end;
        const startOK = !startStr || currentMonthISO >= startStr.slice(0, 7);
        const endOK = !endStr || currentMonthISO <= endStr.slice(0, 7);
        if (!startOK || !endOK) return;
        const link = (currentMatching.links || []).find(l => l.budgetLineId === row.id);
        const reelFromPointing = (link?.txIds || []).reduce((s, refId) => {
          const amt = resolveAmount(refId);
          return s + (kind === "revenu" ? amt : -amt);
        }, 0);
        const pendingContrib = pendingOperations
          .filter(op => op.status === "pending" && op.budgetLineId === row.id && (!op.date || op.date.slice(0, 7) === currentMonthISO))
          .reduce((s, op) => {
            const amt = Number(op.amount) || 0;
            return s + (kind === "revenu" ? Math.max(0, amt) : Math.abs(amt));
          }, 0);
        const reel = reelFromPointing + pendingContrib;
        const pct = Math.min(100, budgeted > 0 ? reel / budgeted * 100 : 0);
        const diff = Math.abs(reel - budgeted);
        const tolerance = Math.max(1, budgeted * 0.02);
        const hasData = (link && (link.txIds || []).length > 0) || pendingContrib > 0;
        const status = hasData ? diff <= tolerance ? "match" : kind === "revenu" || kind === "placement" ? reel > budgeted ? "economy" : "over" : reel < budgeted ? "economy" : "over" : "pending";
        const displayLabel = kind === "placement" ? `Épargne : ${row.label}` : row.label;
        rows.push({
          id: row.id,
          label: displayLabel,
          kind,
          budgeted,
          reel,
          reelFromPointing,
          pendingContrib,
          hasPendingContrib: pendingContrib > 0,
          pct,
          status
        });
      };
      (rawData?.charges || []).forEach(c => processLine(c, "charge"));
      (rawData?.incomes || []).forEach(i => processLine(i, "revenu"));
      (rawData?.placements || []).forEach(p => processLine(p, "placement"));
      return rows.sort((a, b) => b.budgeted - a.budgeted);
    }, [rawData, matchings, transactions, pendingOperations, currentMonthISO]);
    // apiData.landingData (AnalyseLandingRowDto) porte déjà id/label/kind/budgeted/reel/pct/status/
    // pendingContrib/hasPendingContrib : mêmes clés que les lignes calculées en JS, pas de remapping.
    const landingData = serverDataValid ? apiData.landingData : localLandingData;
    const landingTotalBudget = landingData.reduce((s, r) => s + r.budgeted, 0);
    const landingTotalReel = landingData.reduce((s, r) => s + r.reel, 0);
    const localMonthlyCompareData = useMemo(() => {
      const inflationRate = Number(rawData?.settings?.inflationRate) || 0.02;
      const txById = {};
      transactions.forEach(t => {
        txById[t.id] = t;
      });
      const lineKindMap = {};
      (rawData?.charges || []).forEach(c => {
        lineKindMap[c.id] = "charge";
      });
      (rawData?.incomes || []).forEach(i => {
        lineKindMap[i.id] = "revenu";
      });
      (rawData?.placements || []).forEach(p => {
        lineKindMap[p.id] = "placement";
      });
      const calcCharge = exports.chargeMonthlyForYear || window.BudgetApp && window.BudgetApp.chargeMonthlyForYear || chargeMonthlyForYear;
      const calcIncome = exports.incomeMonthlyForYear || window.BudgetApp && window.BudgetApp.incomeMonthlyForYear || incomeMonthlyForYear;
      const nMonths = Math.min(monthsBack || 12, 24);
      const months = [];
      for (let i = nMonths - 1; i >= 0; i--) {
        const d = new Date();
        d.setDate(1);
        d.setMonth(d.getMonth() - i);
        months.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`);
      }
      return months.map(monthISO => {
        const year = Number(monthISO.slice(0, 4));
        const budgeted = [...(rawData?.charges || []).map(c => {
          const ok = (!c.start || monthISO >= c.start.slice(0, 7)) && (!c.end || monthISO <= c.end.slice(0, 7));
          return ok ? calcCharge(c, year, inflationRate) : 0;
        }), ...(rawData?.incomes || []).map(inc => {
          const ok = (!inc.start || monthISO >= inc.start.slice(0, 7)) && (!inc.end || monthISO <= inc.end.slice(0, 7));
          return ok ? calcIncome(inc, year) : 0;
        }), ...(rawData?.placements || []).map(p => {
          const m = Number(p.monthly) || 0;
          const ok = (!p.monthlyFrom || monthISO >= p.monthlyFrom.slice(0, 7)) && (!p.monthlyUntil || monthISO <= p.monthlyUntil.slice(0, 7));
          return m > 0 && ok ? m : 0;
        })].reduce((s, v) => s + v, 0);
        const monthMatching = matchings.find(m => m.month === monthISO) || {
          links: []
        };
        const pointedReel = (monthMatching.links || []).reduce((s, l) => {
          const kind = lineKindMap[l.budgetLineId] || "charge";
          return s + (l.txIds || []).reduce((ss, refId) => {
            const amt = resolveAmount(refId);
            return ss + (kind === "revenu" ? amt : -amt);
          }, 0);
        }, 0);
        const pendingContrib = pendingOperations
          .filter(op => op.status === "pending" && op.budgetLineId && op.date && op.date.slice(0, 7) === monthISO)
          .reduce((s, op) => {
            const kind = lineKindMap[op.budgetLineId] || "charge";
            const amt = Number(op.amount) || 0;
            return s + (kind === "revenu" ? Math.max(0, amt) : Math.abs(amt));
          }, 0);
        const reel = pointedReel + pendingContrib;
        const label = new Date(Number(monthISO.slice(0, 4)), Number(monthISO.slice(5, 7)) - 1, 1).toLocaleDateString("fr-FR", {
          month: "short",
          year: "2-digit"
        });
        return {
          monthISO,
          label,
          budgeted,
          reel,
          pointedReel,
          pendingContrib,
          hasPointing: (monthMatching.links || []).some(l => (l.txIds || []).length > 0) || pendingContrib > 0
        };
      });
    }, [rawData, matchings, transactions, pendingOperations, monthsBack]);
    // apiData.monthlyCompareData (AnalyseMonthlyCompareDto) : monthISO/label/budgeted/reel/hasPointing,
    // seuls champs effectivement lus par le rendu (ecart/status sont recalculés localement dans le tableau
    // et le graphique à partir de budgeted/reel/hasPointing, identiquement des deux côtés).
    const monthlyCompareData = serverDataValid ? apiData.monthlyCompareData : localMonthlyCompareData;
    const barChartRef = useRef(null);
    const barCanvasRef = useRef(null);
    useEffect(() => {
      if (!barCanvasRef.current || monthlyCompareData.length === 0) return;
      if (barChartRef.current) barChartRef.current.destroy();
      const labels = monthlyCompareData.map(d => d.label);
      barChartRef.current = new Chart(barCanvasRef.current.getContext("2d"), {
        type: "bar",
        data: {
          labels,
          datasets: [{
            label: "Budget Prévu",
            data: monthlyCompareData.map(d => Math.round(d.budgeted)),
            backgroundColor: "#2563EB33",
            borderColor: "#2563EB",
            borderWidth: 1.5,
            borderRadius: 4
          }, {
            label: "Réel Constaté (pointé)",
            data: monthlyCompareData.map(d => d.hasPointing ? Math.round(d.reel) : null),
            backgroundColor: "#16A34A55",
            borderColor: "#16A34A",
            borderWidth: 1.5,
            borderRadius: 4
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: {
            legend: {
              position: "top",
              labels: {
                font: {
                  size: 12
                },
                padding: 14
              }
            },
            tooltip: {
              callbacks: {
                label: ctx => {
                  const v = ctx.raw;
                  if (v == null) return `${ctx.dataset.label}: non pointé`;
                  return `${ctx.dataset.label}: ${eur(v)}`;
                }
              }
            }
          },
          scales: {
            x: {
              grid: {
                display: false
              },
              ticks: {
                font: {
                  size: 11
                }
              }
            },
            y: {
              grid: {
                color: C?.line || "#DED6C4"
              },
              ticks: {
                font: {
                  size: 11
                },
                callback: v => eur(v)
              }
            }
          }
        }
      });
      return () => {
        if (barChartRef.current) barChartRef.current.destroy();
      };
    }, [monthlyCompareData, activeTab]);
    const localDriftRows = useMemo(() => {
      const calcAvgs = exports.computeRealAverages || window.BudgetApp && window.BudgetApp.computeRealAverages || computeRealAverages;
      const realAvgs = calcAvgs(rawData);
      const inflationRate = Number(rawData?.settings?.inflationRate) || 0.02;
      const currentYear = new Date().getFullYear();
      const rows = [];
      const calcCharge = exports.chargeMonthlyForYear || window.BudgetApp && window.BudgetApp.chargeMonthlyForYear || chargeMonthlyForYear;
      const calcIncome = exports.incomeMonthlyForYear || window.BudgetApp && window.BudgetApp.incomeMonthlyForYear || incomeMonthlyForYear;
      const addLine = (row, kind) => {
        const avg = realAvgs[row.id];
        const budgeted = kind === "charge" ? calcCharge(row, currentYear, inflationRate) : kind === "revenu" ? calcIncome(row, currentYear) : Number(row.monthly) || 0;
        if (budgeted <= 0) return;
        const avg3m = avg?.avg3m ?? null;
        const avg12m = avg?.avg12m ?? null;
        const ecart = avg3m !== null ? avg3m - budgeted : null;
        const ecartPct = avg3m !== null && budgeted > 0 ? ecart / budgeted * 100 : null;
        const tolerance = Math.max(1, budgeted * 0.02);
        let status = "pending";
        if (ecart !== null) {
          if (Math.abs(ecart) <= tolerance) status = "match";else if (kind === "revenu" || kind === "placement") status = ecart > 0 ? "economy" : "over";else status = ecart < 0 ? "economy" : "over";
        }
        const displayLabel = kind === "placement" ? `Épargne : ${row.label}` : row.label;
        rows.push({
          id: row.id,
          label: displayLabel,
          kind,
          budgeted,
          avg3m,
          avg12m,
          ecart,
          ecartPct,
          status,
          months: avg?.months || 0
        });
      };
      (rawData?.charges || []).forEach(c => addLine(c, "charge"));
      (rawData?.incomes || []).forEach(i => addLine(i, "revenu"));
      (rawData?.placements || []).forEach(p => addLine(p, "placement"));
      return rows;
    }, [rawData]);
    // apiData.driftRows (AnalyseDriftRowDto) : id/label/kind/budgeted/avg3m/avg12m/ecart/ecartPct/status/months,
    // structure identique aux lignes calculées en JS.
    const driftRows = serverDataValid ? apiData.driftRows : localDriftRows;
    const displayDriftRows = useMemo(() => {
      let r = driftRows;
      if (driftSearch.trim()) {
        const q = driftSearch.trim().toLowerCase();
        r = r.filter(row => row.label.toLowerCase().includes(q));
      }
      return [...r].sort((a, b) => {
        const av = a[driftSortKey] ?? -Infinity;
        const bv = b[driftSortKey] ?? -Infinity;
        return av < bv ? driftSortDir : av > bv ? -driftSortDir : 0;
      });
    }, [driftRows, driftSearch, driftSortKey, driftSortDir]);
    const colDrift = key => {
      if (driftSortKey === key) setDriftSortDir(-driftSortDir);else {
        setDriftSortKey(key);
        setDriftSortDir(-1);
      }
    };
    const driftIcon = key => driftSortKey === key ? driftSortDir === 1 ? " ↑" : " ↓" : "";
    const getStatusMeta = (status, kind = "charge") => {
      if (status === "match") return {
        label: "Conforme",
        dot: "#2563EB",
        bg: "#EFF6FF",
        border: "#BFDBFE"
      };
      if (status === "pending") return {
        label: "Non pointé",
        dot: "#D97706",
        bg: "#FFFBEB",
        border: "#FDE68A"
      };
      if (status === "economy") {
        return {
          label: kind === "revenu" ? "Surplus (+)" : "Économie (-)",
          dot: "#16A34A",
          bg: "#F0FDF4",
          border: "#BBF7D0"
        };
      }
      if (status === "over") {
        return {
          label: kind === "revenu" ? "Déficit (-)" : "Dépassement (+)",
          dot: "#DC2626",
          bg: "#FEF2F2",
          border: "#FECACA"
        };
      }
      return {
        label: status,
        dot: "#666",
        bg: "#fff",
        border: "#ccc"
      };
    };
    const hdrCell = (key, label, align = "left") => ({
      onClick: () => colDrift(key),
      style: {
        padding: "9px 10px",
        textAlign: align,
        fontSize: 11,
        fontWeight: 700,
        color: driftSortKey === key ? C?.pine || "#2F5D50" : C?.inkSoft || "#6B7278",
        background: C?.panelAlt || "#EFEAE0",
        cursor: "pointer",
        whiteSpace: "nowrap",
        borderBottom: `2px solid ${C?.line || "#DED6C4"}`,
        userSelect: "none"
      }
    });
    const txBudgetLineMap = useMemo(() => {
      const map = {};
      matchings.forEach(m => {
        (m.links || []).forEach(link => {
          (link.txIds || []).forEach(refId => {
            map[refId] = link.budgetLineId;
          });
        });
      });
      return map;
    }, [matchings]);
    const categoryOptions = useMemo(() => {
      return [...categories].map(c => ({
        id: c.id,
        label: c.label,
        kind: c.kind
      })).sort((a, b) => (a.label || "").localeCompare(b.label || ""));
    }, [categories]);
    const budgetLineOptions = useMemo(() => {
      const opts = [];
      (rawData?.charges || []).forEach(c => opts.push({
        id: c.id,
        label: c.label,
        kind: "charge"
      }));
      (rawData?.incomes || []).forEach(i => opts.push({
        id: i.id,
        label: i.label,
        kind: "revenu"
      }));
      (rawData?.placements || []).forEach(p => opts.push({
        id: p.id,
        label: p.label,
        kind: "placement"
      }));
      return opts.sort((a, b) => (a.label || "").localeCompare(b.label || ""));
    }, [rawData]);
    const categoryLabelMap = useMemo(() => {
      const m = {};
      categories.forEach(c => {
        m[c.id] = c.label;
      });
      return m;
    }, [categories]);
    const budgetLineLabelMap = useMemo(() => {
      const m = {};
      budgetLineOptions.forEach(o => {
        m[o.id] = o;
      });
      return m;
    }, [budgetLineOptions]);
    const customAllOps = useMemo(() => {
      const ops = [];
      const pushOp = (refId, date, label, amount, categoryId, budgetLineId, origin) => {
        ops.push({
          key: refId,
          date: date || "",
          label: label || "—",
          amount,
          categoryId: categoryId || null,
          budgetLineId: budgetLineId || null,
          categoryLabel: categoryId ? categoryLabelMap[categoryId] || "Catégorie supprimée" : "Non catégorisé",
          budgetLineLabel: budgetLineId ? budgetLineLabelMap[budgetLineId]?.label || "Ligne supprimée" : "Non affectée",
          budgetLineKind: budgetLineId ? budgetLineLabelMap[budgetLineId]?.kind || null : null,
          origin
        });
      };
      transactions.forEach(t => {
        if (t.splits && t.splits.length > 0) {
          t.splits.forEach(s => {
            const refId = `${t.id}#${s.id}`;
            pushOp(refId, t.date, t.label, Number(s.amount) || 0, s.categoryId, txBudgetLineMap[refId] || txBudgetLineMap[t.id], "cleared");
          });
        } else {
          pushOp(t.id, t.date, t.label, Number(t.amount) || 0, t.categoryId, txBudgetLineMap[t.id], "cleared");
        }
      });
      pendingOperations.filter(op => op.status === "pending").forEach(op => {
        if (op.splits && op.splits.length > 0) {
          op.splits.forEach(s => {
            pushOp(`${op.id}#${s.id}`, op.date, op.label, Number(s.amount) || 0, s.categoryId, op.budgetLineId, "pending");
          });
        } else {
          pushOp(op.id, op.date, op.label, Number(op.amount) || 0, op.categoryId, op.budgetLineId, "pending");
        }
      });
      return ops;
    }, [transactions, pendingOperations, txBudgetLineMap, categoryLabelMap, budgetLineLabelMap]);
    const customFilteredOps = useMemo(() => {
      const filtered = customAllOps.filter(op => {
        if (customDateFrom && (!op.date || op.date < customDateFrom)) return false;
        if (customDateTo && (!op.date || op.date > customDateTo)) return false;
        if (customCategoryIds.length > 0 && (!op.categoryId || !customCategoryIds.includes(op.categoryId))) return false;
        if (customBudgetLineIds.length > 0 && (!op.budgetLineId || !customBudgetLineIds.includes(op.budgetLineId))) return false;
        return true;
      });
      return [...filtered].sort((a, b) => {
        const av = a[customSortKey] ?? "";
        const bv = b[customSortKey] ?? "";
        return av < bv ? customSortDir : av > bv ? -customSortDir : 0;
      });
    }, [customAllOps, customDateFrom, customDateTo, customCategoryIds, customBudgetLineIds, customSortKey, customSortDir]);
    const customKpis = useMemo(() => {
      const expenseOps = customFilteredOps.filter(o => o.amount < 0);
      const incomeOps = customFilteredOps.filter(o => o.amount > 0);
      const sumExpenses = -expenseOps.reduce((s, o) => s + o.amount, 0);
      const sumIncome = incomeOps.reduce((s, o) => s + o.amount, 0);
      return {
        count: customFilteredOps.length,
        sumExpenses,
        sumIncome,
        avgExpense: expenseOps.length ? sumExpenses / expenseOps.length : 0,
        avgIncome: incomeOps.length ? sumIncome / incomeOps.length : 0
      };
    }, [customFilteredOps]);
    const colCustom = key => {
      if (customSortKey === key) setCustomSortDir(-customSortDir);else {
        setCustomSortKey(key);
        setCustomSortDir(-1);
      }
    };
    const customIcon = key => customSortKey === key ? customSortDir === 1 ? " ↑" : " ↓" : "";
    const customHasFilters = !!(customDateFrom || customDateTo || customCategoryIds.length > 0 || customBudgetLineIds.length > 0);
    const resetCustomFilters = () => {
      setCustomDateFrom("");
      setCustomDateTo("");
      setCustomCategoryIds([]);
      setCustomBudgetLineIds([]);
    };
    const hdrCellCustom = (key, align = "left") => ({
      onClick: () => colCustom(key),
      style: {
        padding: "9px 10px",
        textAlign: align,
        fontSize: 11,
        fontWeight: 700,
        color: customSortKey === key ? C?.pine || "#2F5D50" : C?.inkSoft || "#6B7278",
        background: C?.panelAlt || "#EFEAE0",
        cursor: "pointer",
        whiteSpace: "nowrap",
        borderBottom: `2px solid ${C?.line || "#DED6C4"}`,
        userSelect: "none"
      }
    });
    const originMeta = origin => origin === "pending" ? {
      label: "En attente",
      dot: "#D97706",
      bg: "#FFFBEB",
      border: "#FDE68A"
    } : {
      label: "Comptabilisé",
      dot: "#2563EB",
      bg: "#EFF6FF",
      border: "#BFDBFE"
    };
    const TAB_STYLE = active => ({
      padding: "7px 18px",
      fontSize: 13,
      fontWeight: 600,
      cursor: "pointer",
      borderBottom: active ? `2px solid ${C?.pine || "#2F5D50"}` : `2px solid transparent`,
      color: active ? C?.pine || "#2F5D50" : C?.inkSoft || "#6B7278",
      background: "none",
      border: "none",
      transition: "all 0.15s"
    });
    if (transactions.length === 0) {
      return /*#__PURE__*/React.createElement("div", {
        style: {
          color: C?.inkSoft || "#6B7278",
          fontSize: 13,
          padding: 20
        }
      }, "Aucune transaction importée — rendez-vous dans l'onglet ", /*#__PURE__*/React.createElement("strong", null, "Import"), " pour commencer.");
    }
    return /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-end",
        marginBottom: 20,
        flexWrap: "wrap",
        gap: 12
      }
    }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        alignItems: "center",
        gap: 10
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontFamily: "'Newsreader', serif",
        fontSize: 24,
        fontWeight: 600,
        color: C?.ink || "#232A2E"
      }
    }, "Analyse Réel vs Prévisionnel"), openHelp && /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => openHelp("analyse"),
      style: {
        background: "none",
        border: `1px solid ${C?.pine || "#2F5D50"}`,
        color: C?.pine || "#2F5D50",
        borderRadius: "50%",
        width: 22,
        height: 22,
        fontSize: 12,
        fontWeight: 700,
        cursor: "pointer",
        display: "flex",
        alignItems: "center",
        justifyContent: "center"
      },
      title: "Aide sur l'analyse"
    }, "?")), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12.5,
        color: C?.inkSoft || "#6B7278",
        marginTop: 3
      }
    }, "Comparaison entre les transactions bancaires et le prévisionnel.")), /*#__PURE__*/React.createElement("select", {
      value: monthsBack,
      onChange: e => setMonthsBack(Number(e.target.value)),
      style: {
        ...inputStyle,
        width: 200,
        cursor: "pointer"
      }
    }, /*#__PURE__*/React.createElement("option", {
      value: 3
    }, "3 derniers mois"), /*#__PURE__*/React.createElement("option", {
      value: 6
    }, "6 derniers mois"), /*#__PURE__*/React.createElement("option", {
      value: 12
    }, "12 derniers mois"), /*#__PURE__*/React.createElement("option", {
      value: 0
    }, "Toute la période"))), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        borderBottom: `1px solid ${C?.line || "#DED6C4"}`,
        marginBottom: 22,
        gap: 0
      }
    }, [{
      key: "overview",
      label: "📊 Vue Générale"
    }, {
      key: "landing",
      label: "🎯 Atterrissage du Mois"
    }, {
      key: "monthly",
      label: "📅 Historique Mensuel"
    }, {
      key: "drift",
      label: "📉 Dérives par Ligne"
    }, {
      key: "custom",
      label: "🔎 Filtre Personnalisé"
    }].map(t => /*#__PURE__*/React.createElement("button", {
      type: "button",
      key: t.key,
      onClick: () => setActiveTab(t.key),
      style: TAB_STYLE(activeTab === t.key)
    }, t.label))), activeTab === "overview" && /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 14,
        flexWrap: "wrap",
        marginBottom: 18
      }
    }, /*#__PURE__*/React.createElement(KPI, {
      label: "Dépenses (période)",
      value: eur(totalExpenses),
      accent: C?.brick || "#A8503C",
      sub: `≈ ${eur(totalExpenses / nbMonths)} / mois`
    }), /*#__PURE__*/React.createElement(KPI, {
      label: "Revenus (période)",
      value: eur(totalIncome),
      accent: C?.pine || "#2F5D50",
      sub: `≈ ${eur(totalIncome / nbMonths)} / mois`
    }), /*#__PURE__*/React.createElement(KPI, {
      label: "Postes réductibles",
      value: eur(compressibleTotal),
      accent: C?.gold || "#93802E",
      sub: "Catégories marquées « réductible »"
    }), uncategorized > 0 && /*#__PURE__*/React.createElement(KPI, {
      label: "Non catégorisées",
      value: uncategorized,
      accent: C?.inkSoft || "#6B7278",
      sub: "À classer dans l'onglet Import"
    })), /*#__PURE__*/React.createElement(SectionCard, {
      title: "Postes de dépense, du plus élevé au plus faible",
      subtitle: "Somme des dépenses réelles par catégorie sur la période sélectionnée."
    }, byCategory.length === 0 ? /*#__PURE__*/React.createElement("div", {
      style: {
        color: C?.inkSoft || "#6B7278",
        fontSize: 12.5
      }
    }, "Aucune dépense sur la période.") : /*#__PURE__*/React.createElement(AllocationChartJS, {
      allocation: byCategory,
      mode: "bars",
      height: Math.max(200, byCategory.length * 34)
    })), /*#__PURE__*/React.createElement(SectionCard, {
      title: "Détail par catégorie",
      subtitle: "Total sur la période, moyenne mensuelle, et poids dans le total des dépenses."
    }, /*#__PURE__*/React.createElement("table", {
      style: {
        width: "100%",
        borderCollapse: "collapse",
        fontSize: 12.5
      }
    }, /*#__PURE__*/React.createElement("thead", null, /*#__PURE__*/React.createElement("tr", {
      style: {
        borderBottom: `1px solid ${C?.line || "#DED6C4"}`
      }
    }, /*#__PURE__*/React.createElement("th", {
      style: {
        textAlign: "left",
        padding: "6px 8px",
        color: C?.inkSoft || "#6B7278",
        fontSize: 11
      }
    }, "Catégorie"), /*#__PURE__*/React.createElement("th", {
      style: {
        textAlign: "right",
        padding: "6px 8px",
        color: C?.inkSoft || "#6B7278",
        fontSize: 11
      }
    }, "Total période"), /*#__PURE__*/React.createElement("th", {
      style: {
        textAlign: "right",
        padding: "6px 8px",
        color: C?.inkSoft || "#6B7278",
        fontSize: 11
      }
    }, "Moyenne / mois"), /*#__PURE__*/React.createElement("th", {
      style: {
        textAlign: "right",
        padding: "6px 8px",
        color: C?.inkSoft || "#6B7278",
        fontSize: 11
      }
    }, "% du total"))), /*#__PURE__*/React.createElement("tbody", null, byCategory.map(c => {
      const cat = categories.find(cc => cc.label === c.label);
      const isCredit = c.amount < 0;
      return /*#__PURE__*/React.createElement("tr", {
        key: c.label,
        style: {
          borderBottom: `1px solid ${C?.line || "#DED6C4"}`
        }
      }, /*#__PURE__*/React.createElement("td", {
        style: {
          padding: "7px 8px"
        }
      }, c.label, cat?.compressible === "Oui" && /*#__PURE__*/React.createElement("span", {
        style: {
          marginLeft: 6,
          fontSize: 10,
          color: C?.gold || "#93802E",
          fontWeight: 700
        }
      }, "RÉDUCTIBLE"), isCredit && /*#__PURE__*/React.createElement("span", {
        style: {
          marginLeft: 6,
          fontSize: 10,
          color: C?.pine || "#2F5D50",
          fontWeight: 700,
          background: C?.pineSoft || "#E3ECE8",
          padding: "1px 5px",
          borderRadius: 4
        }
      }, "GAIN / REMBOURSEMENT")), /*#__PURE__*/React.createElement("td", {
        style: {
          padding: "7px 8px",
          textAlign: "right",
          fontFamily: "'IBM Plex Mono', monospace",
          color: isCredit ? C?.pine || "#2F5D50" : C?.ink || "#232A2E",
          fontWeight: isCredit ? 600 : 400
        }
      }, eur(c.amount)), /*#__PURE__*/React.createElement("td", {
        style: {
          padding: "7px 8px",
          textAlign: "right",
          fontFamily: "'IBM Plex Mono', monospace",
          color: isCredit ? C?.pine || "#2F5D50" : C?.ink || "#232A2E"
        }
      }, eur(c.amount / nbMonths)), /*#__PURE__*/React.createElement("td", {
        style: {
          padding: "7px 8px",
          textAlign: "right",
          color: C?.inkSoft || "#6B7278"
        }
      }, !isCredit && totalExpenses > 0 ? `${(c.amount / totalExpenses * 100).toFixed(1)} %` : "—"));
    }))))), activeTab === "landing" && /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        marginBottom: 16,
        flexWrap: "wrap",
        gap: 10
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontFamily: "'Newsreader', serif",
        fontSize: 18,
        fontWeight: 600,
        color: C?.ink || "#232A2E",
        textTransform: "capitalize"
      }
    }, "🎯 Atterrissage — ", currentMonthLabel), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 14
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 10,
        padding: "10px 16px",
        minWidth: 130
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11,
        color: C?.inkSoft || "#6B7278",
        marginBottom: 3
      }
    }, "Budget total du mois"), /*#__PURE__*/React.createElement("div", {
      style: {
        fontFamily: "'IBM Plex Mono', monospace",
        fontSize: 16,
        fontWeight: 700,
        color: C?.navy || "#28394A"
      }
    }, eur(landingTotalBudget))), /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 10,
        padding: "10px 16px",
        minWidth: 130
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11,
        color: C?.inkSoft || "#6B7278",
        marginBottom: 3
      }
    }, "Réel pointé"), /*#__PURE__*/React.createElement("div", {
      style: {
        fontFamily: "'IBM Plex Mono', monospace",
        fontSize: 16,
        fontWeight: 700,
        color: landingTotalReel > landingTotalBudget ? "#DC2626" : "#16A34A"
      }
    }, eur(landingTotalReel))))), landingData.length === 0 ? /*#__PURE__*/React.createElement("div", {
      style: {
        color: C?.inkSoft || "#6B7278",
        fontSize: 13
      }
    }, "Aucune ligne budgétaire active ce mois-ci.") : /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        flexDirection: "column",
        gap: 10
      }
    }, landingData.map(row => {
      const sm = getStatusMeta(row.status, row.kind);
      const pct = Math.min(100, row.budgeted > 0 ? row.reel / row.budgeted * 100 : 0);
      const barColor = row.status === "match" ? "#2563EB" : row.status === "economy" ? "#16A34A" : row.status === "over" ? "#DC2626" : C?.line || "#DED6C4";
      return /*#__PURE__*/React.createElement("div", {
        key: row.id,
        style: {
          background: C?.panel || "#FFFFFF",
          border: `1px solid ${C?.line || "#DED6C4"}`,
          borderRadius: 10,
          padding: "12px 16px"
        }
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          marginBottom: 8,
          gap: 8
        }
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          display: "flex",
          alignItems: "center",
          gap: 8,
          flex: 1,
          minWidth: 0
        }
      }, /*#__PURE__*/React.createElement("span", {
        style: {
          padding: "2px 7px",
          borderRadius: 8,
          fontSize: 10.5,
          fontWeight: 700,
          flexShrink: 0,
          background: row.kind === "charge" ? C?.brickSoft || "#F4E4DF" : row.kind === "revenu" ? C?.pineSoft || "#E3ECE8" : C?.panelAlt || "#EFEAE0",
          color: row.kind === "charge" ? C?.brick || "#A8503C" : row.kind === "revenu" ? C?.pine || "#2F5D50" : C?.navy || "#28394A",
          border: row.kind === "placement" ? `1px solid ${C?.line || "#DED6C4"}` : "none"
        }
      }, row.kind === "charge" ? "Charge" : row.kind === "revenu" ? "Revenu" : "Épargne"), /*#__PURE__*/React.createElement("span", {
        style: {
          fontWeight: 600,
          fontSize: 13,
          overflow: "hidden",
          textOverflow: "ellipsis",
          whiteSpace: "nowrap"
        },
        title: row.label
      }, row.label)), /*#__PURE__*/React.createElement("div", {
        style: {
          display: "flex",
          alignItems: "center",
          gap: 8,
          flexShrink: 0
        }
      }, row.hasPendingContrib && /*#__PURE__*/React.createElement("span", {
        style: {
          fontSize: 10.5,
          color: "#D97706",
          background: "#FFFBEB",
          border: "1px solid #FDE68A",
          borderRadius: 6,
          padding: "1px 6px",
          fontWeight: 600,
          whiteSpace: "nowrap"
        },
        title: `Inclut ${eur(row.pendingContrib)} issu d'opérations en cours (pending)`
      }, `⏳ +${eur(row.pendingContrib)} en cours`), /*#__PURE__*/React.createElement("span", {
        style: {
          fontFamily: "'IBM Plex Mono', monospace",
          fontSize: 13,
          color: C?.inkSoft || "#6B7278"
        }
      }, eur(row.reel), " / ", eur(row.budgeted)), /*#__PURE__*/React.createElement("div", {
        style: {
          display: "inline-flex",
          alignItems: "center",
          gap: 4,
          padding: "2px 8px",
          borderRadius: 10,
          fontSize: 11,
          fontWeight: 700,
          background: sm.bg,
          border: `1px solid ${sm.border}`,
          color: sm.dot
        }
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          width: 6,
          height: 6,
          borderRadius: "50%",
          background: sm.dot
        }
      }), sm.label))), /*#__PURE__*/React.createElement("div", {
        style: {
          height: 8,
          background: C?.panelAlt || "#EFEAE0",
          borderRadius: 4,
          overflow: "hidden"
        }
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          height: "100%",
          width: `${pct}%`,
          background: barColor,
          borderRadius: 4,
          transition: "width 0.4s ease",
          maxWidth: "100%"
        }
      })), /*#__PURE__*/React.createElement("div", {
        style: {
          display: "flex",
          justifyContent: "space-between",
          marginTop: 4,
          fontSize: 10.5,
          color: C?.inkSoft || "#6B7278"
        }
      }, /*#__PURE__*/React.createElement("span", null, pct.toFixed(1), "% consommé"), row.status !== "pending" && /*#__PURE__*/React.createElement("span", {
        style: {
          color: row.status === "over" ? "#DC2626" : row.status === "economy" ? "#16A34A" : "#2563EB",
          fontWeight: 600
        }
      }, row.reel >= row.budgeted ? "+" : "", eur(row.reel - row.budgeted))));
    }))), activeTab === "monthly" && /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontFamily: "'Newsreader', serif",
        fontSize: 18,
        fontWeight: 600,
        color: C?.ink || "#232A2E",
        marginBottom: 16
      }
    }, "📅 Budget Prévu vs Réel Constaté — mois par mois"), monthlyCompareData.every(d => !d.hasPointing) && /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.goldSoft || "#F0EAD3",
        border: `1px solid ${C?.gold || "#93802E"}`,
        borderRadius: 10,
        padding: "12px 16px",
        fontSize: 12.5,
        color: C?.gold || "#93802E",
        marginBottom: 16
      }
    }, "💡 Aucun pointage enregistré sur cette période. Utilisez l'onglet ", /*#__PURE__*/React.createElement("strong", null, "Pointage"), " pour associer vos transactions bancaires aux lignes budgétaires."), /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 12,
        padding: "20px 16px",
        height: 320
      }
    }, /*#__PURE__*/React.createElement("canvas", {
      ref: barCanvasRef
    })), /*#__PURE__*/React.createElement("div", {
      style: {
        marginTop: 18,
        overflowX: "auto",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 10
      }
    }, /*#__PURE__*/React.createElement("table", {
      style: {
        width: "100%",
        borderCollapse: "collapse",
        fontSize: 12.5,
        minWidth: 600
      }
    }, /*#__PURE__*/React.createElement("thead", null, /*#__PURE__*/React.createElement("tr", {
      style: {
        borderBottom: `2px solid ${C?.line || "#DED6C4"}`
      }
    }, /*#__PURE__*/React.createElement("th", {
      style: {
        padding: "8px 12px",
        textAlign: "left",
        fontSize: 11,
        fontWeight: 700,
        color: C?.inkSoft || "#6B7278",
        background: C?.panelAlt || "#EFEAE0"
      }
    }, "Mois"), /*#__PURE__*/React.createElement("th", {
      style: {
        padding: "8px 12px",
        textAlign: "right",
        fontSize: 11,
        fontWeight: 700,
        color: "#2563EB",
        background: C?.panelAlt || "#EFEAE0"
      }
    }, "Budget Prévu"), /*#__PURE__*/React.createElement("th", {
      style: {
        padding: "8px 12px",
        textAlign: "right",
        fontSize: 11,
        fontWeight: 700,
        color: "#16A34A",
        background: C?.panelAlt || "#EFEAE0"
      }
    }, "Réel Pointé"), /*#__PURE__*/React.createElement("th", {
      style: {
        padding: "8px 12px",
        textAlign: "right",
        fontSize: 11,
        fontWeight: 700,
        color: C?.inkSoft || "#6B7278",
        background: C?.panelAlt || "#EFEAE0"
      }
    }, "Écart"), /*#__PURE__*/React.createElement("th", {
      style: {
        padding: "8px 12px",
        textAlign: "center",
        fontSize: 11,
        fontWeight: 700,
        color: C?.inkSoft || "#6B7278",
        background: C?.panelAlt || "#EFEAE0"
      }
    }, "Statut"))), /*#__PURE__*/React.createElement("tbody", null, monthlyCompareData.map(row => {
      const ecart = row.reel - row.budgeted;
      const tolerance = Math.max(10, row.budgeted * 0.02);
      const status = !row.hasPointing ? "pending" : Math.abs(ecart) <= tolerance ? "match" : ecart < 0 ? "economy" : "over";
      const sm = getStatusMeta(status, "charge");
      const ecartColor = status === "match" ? "#2563EB" : status === "economy" ? "#16A34A" : status === "over" ? "#DC2626" : C?.inkSoft || "#6B7278";
      return /*#__PURE__*/React.createElement("tr", {
        key: row.monthISO,
        style: {
          borderBottom: `1px solid ${C?.line || "#DED6C4"}`
        }
      }, /*#__PURE__*/React.createElement("td", {
        style: {
          padding: "8px 12px",
          fontWeight: 600,
          textTransform: "capitalize"
        }
      }, row.label), /*#__PURE__*/React.createElement("td", {
        style: {
          padding: "8px 12px",
          textAlign: "right",
          fontFamily: "'IBM Plex Mono', monospace",
          color: "#2563EB"
        }
      }, eur(row.budgeted)), /*#__PURE__*/React.createElement("td", {
        style: {
          padding: "8px 12px",
          textAlign: "right",
          fontFamily: "'IBM Plex Mono', monospace",
          color: row.hasPointing ? "#16A34A" : C?.inkSoft || "#6B7278"
        }
      }, row.hasPointing ? eur(row.reel) : "—"), /*#__PURE__*/React.createElement("td", {
        style: {
          padding: "8px 12px",
          textAlign: "right",
          fontFamily: "'IBM Plex Mono', monospace",
          fontWeight: 600,
          color: ecartColor
        }
      }, row.hasPointing ? `${ecart >= 0 ? "+" : ""}${eur(ecart)}` : "—"), /*#__PURE__*/React.createElement("td", {
        style: {
          padding: "8px 12px",
          textAlign: "center"
        }
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          display: "inline-flex",
          alignItems: "center",
          gap: 4,
          padding: "2px 9px",
          borderRadius: 10,
          fontSize: 11,
          fontWeight: 700,
          background: sm.bg,
          border: `1px solid ${sm.border}`,
          color: sm.dot
        }
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          width: 6,
          height: 6,
          borderRadius: "50%",
          background: sm.dot
        }
      }), sm.label)));
    }))))), activeTab === "drift" && /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        marginBottom: 14,
        flexWrap: "wrap",
        gap: 10
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontFamily: "'Newsreader', serif",
        fontSize: 18,
        fontWeight: 600,
        color: C?.ink || "#232A2E"
      }
    }, "📉 Dérives par ligne budgétaire"), /*#__PURE__*/React.createElement("input", {
      value: driftSearch,
      onChange: e => setDriftSearch(e.target.value),
      placeholder: "🔍 Rechercher une ligne…",
      style: {
        ...inputStyle,
        width: 220,
        fontSize: 12.5
      }
    })), /*#__PURE__*/React.createElement("div", {
      style: {
        overflowX: "auto",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 10,
        background: C?.panel || "#FFFFFF"
      }
    }, /*#__PURE__*/React.createElement("table", {
      style: {
        width: "100%",
        borderCollapse: "collapse",
        minWidth: 820,
        fontSize: 12.5
      }
    }, /*#__PURE__*/React.createElement("thead", null, /*#__PURE__*/React.createElement("tr", null, /*#__PURE__*/React.createElement("th", hdrCell("status", "Statut"), "Statut", driftIcon("status")), /*#__PURE__*/React.createElement("th", hdrCell("kind", "Type"), "Type", driftIcon("kind")), /*#__PURE__*/React.createElement("th", hdrCell("label", "Libellé"), "Libellé", driftIcon("label")), /*#__PURE__*/React.createElement("th", hdrCell("budgeted", "Budget Actuel", "right"), "Budget Actuel", driftIcon("budgeted")), /*#__PURE__*/React.createElement("th", hdrCell("avg3m", "Moy. 3M", "right"), "Moy. Réelle 3M", driftIcon("avg3m")), /*#__PURE__*/React.createElement("th", hdrCell("avg12m", "Moy. 12M", "right"), "Moy. Réelle 12M", driftIcon("avg12m")), /*#__PURE__*/React.createElement("th", hdrCell("ecart", "Écart", "right"), "Écart (€ / %)", driftIcon("ecart")))), /*#__PURE__*/React.createElement("tbody", null, displayDriftRows.length === 0 && /*#__PURE__*/React.createElement("tr", null, /*#__PURE__*/React.createElement("td", {
      colSpan: 7,
      style: {
        padding: 24,
        textAlign: "center",
        color: C?.inkSoft || "#6B7278"
      }
    }, "Aucune ligne budgétaire trouvée.")), displayDriftRows.map(row => {
      const sm = getStatusMeta(row.status, row.kind);
      const ecartColor = row.status === "match" ? "#2563EB" : row.status === "economy" ? "#16A34A" : row.status === "over" ? "#DC2626" : C?.inkSoft || "#6B7278";
      const cellS = {
        padding: "9px 10px",
        borderBottom: `1px solid ${C?.line || "#DED6C4"}`,
        verticalAlign: "middle"
      };
      return /*#__PURE__*/React.createElement("tr", {
        key: row.id
      }, /*#__PURE__*/React.createElement("td", {
        style: cellS
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          display: "inline-flex",
          alignItems: "center",
          gap: 5,
          padding: "3px 9px",
          borderRadius: 12,
          fontSize: 11,
          fontWeight: 700,
          background: sm.bg,
          border: `1px solid ${sm.border}`,
          color: sm.dot,
          whiteSpace: "nowrap"
        }
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          width: 7,
          height: 7,
          borderRadius: "50%",
          background: sm.dot
        }
      }), sm.label)), /*#__PURE__*/React.createElement("td", {
        style: cellS
      }, /*#__PURE__*/React.createElement("span", {
        style: {
          padding: "2px 8px",
          borderRadius: 8,
          fontSize: 11,
          fontWeight: 600,
          background: row.kind === "charge" ? C?.brickSoft || "#F4E4DF" : row.kind === "revenu" ? C?.pineSoft || "#E3ECE8" : C?.panelAlt || "#EFEAE0",
          color: row.kind === "charge" ? C?.brick || "#A8503C" : row.kind === "revenu" ? C?.pine || "#2F5D50" : C?.navy || "#28394A",
          border: row.kind === "placement" ? `1px solid ${C?.line || "#DED6C4"}` : "none"
        }
      }, row.kind === "charge" ? "Charge" : row.kind === "revenu" ? "Revenu" : "Épargne")), /*#__PURE__*/React.createElement("td", {
        style: {
          ...cellS,
          fontWeight: 600,
          maxWidth: 220,
          overflow: "hidden",
          textOverflow: "ellipsis",
          whiteSpace: "nowrap"
        },
        title: row.label
      }, row.label), /*#__PURE__*/React.createElement("td", {
        style: {
          ...cellS,
          textAlign: "right",
          fontFamily: "'IBM Plex Mono', monospace"
        }
      }, eur(row.budgeted)), /*#__PURE__*/React.createElement("td", {
        style: {
          ...cellS,
          textAlign: "right",
          fontFamily: "'IBM Plex Mono', monospace",
          fontWeight: row.avg3m !== null ? 700 : 400,
          color: row.avg3m !== null ? "#2563EB" : C?.inkSoft || "#6B7278"
        }
      }, row.avg3m !== null ? eur(row.avg3m) : "—", row.months > 0 && /*#__PURE__*/React.createElement("div", {
        style: {
          fontSize: 10,
          color: C?.inkSoft || "#6B7278",
          fontWeight: 400
        }
      }, row.months, " mois")), /*#__PURE__*/React.createElement("td", {
        style: {
          ...cellS,
          textAlign: "right",
          fontFamily: "'IBM Plex Mono', monospace",
          color: C?.inkSoft || "#6B7278"
        }
      }, row.avg12m !== null ? eur(row.avg12m) : "—"), /*#__PURE__*/React.createElement("td", {
        style: {
          ...cellS,
          textAlign: "right",
          fontFamily: "'IBM Plex Mono', monospace",
          fontWeight: 700,
          color: ecartColor
        }
      }, row.ecart !== null ? /*#__PURE__*/React.createElement(React.Fragment, null, row.ecart >= 0 ? "+" : "", eur(row.ecart), /*#__PURE__*/React.createElement("div", {
        style: {
          fontSize: 10,
          fontWeight: 400
        }
      }, row.ecartPct >= 0 ? "+" : "", row.ecartPct?.toFixed(1), "%")) : "—"));
    })))), driftRows.filter(r => r.status === "pending").length > 0 && /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11.5,
        color: C?.inkSoft || "#6B7278",
        marginTop: 10,
        fontStyle: "italic"
      }
    }, "💡 Les lignes «\xA0Non pointées\xA0» n'ont aucun pointage dans l'onglet ", /*#__PURE__*/React.createElement("strong", null, "Pointage"), ".")), activeTab === "custom" && /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        marginBottom: 14,
        flexWrap: "wrap",
        gap: 10
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontFamily: "'Newsreader', serif",
        fontSize: 18,
        fontWeight: 600,
        color: C?.ink || "#232A2E"
      }
    }, "🔎 Filtre personnalisé"), customHasFilters && /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: resetCustomFilters,
      style: {
        border: `1px solid ${C?.line || "#DED6C4"}`,
        background: "none",
        color: C?.inkSoft || "#6B7278",
        borderRadius: 7,
        padding: "6px 12px",
        fontSize: 12.5,
        fontWeight: 600,
        cursor: "pointer"
      }
    }, "✕ Réinitialiser les filtres")), /*#__PURE__*/React.createElement(SectionCard, {
      title: "Filtres",
      subtitle: "Combinez une période, des catégories bancaires et des lignes de budget (sélection multiple)."
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        flexWrap: "wrap",
        gap: 16,
        alignItems: "flex-end"
      }
    }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11.5,
        textTransform: "uppercase",
        letterSpacing: 0.4,
        color: C?.inkSoft || "#6B7278",
        fontWeight: 600,
        marginBottom: 5
      }
    }, "Du"), /*#__PURE__*/React.createElement("input", {
      type: "date",
      value: customDateFrom,
      onChange: e => setCustomDateFrom(e.target.value),
      style: inputStyle
    })), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11.5,
        textTransform: "uppercase",
        letterSpacing: 0.4,
        color: C?.inkSoft || "#6B7278",
        fontWeight: 600,
        marginBottom: 5
      }
    }, "Au"), /*#__PURE__*/React.createElement("input", {
      type: "date",
      value: customDateTo,
      onChange: e => setCustomDateTo(e.target.value),
      style: inputStyle
    })), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11.5,
        textTransform: "uppercase",
        letterSpacing: 0.4,
        color: C?.inkSoft || "#6B7278",
        fontWeight: 600,
        marginBottom: 5
      }
    }, "Catégories"), /*#__PURE__*/React.createElement(MultiSelectDropdown, {
      items: categoryOptions,
      selectedIds: customCategoryIds,
      onChange: setCustomCategoryIds,
      width: 220
    })), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11.5,
        textTransform: "uppercase",
        letterSpacing: 0.4,
        color: C?.inkSoft || "#6B7278",
        fontWeight: 600,
        marginBottom: 5
      }
    }, "Lignes de budget"), /*#__PURE__*/React.createElement(MultiSelectDropdown, {
      items: budgetLineOptions,
      selectedIds: customBudgetLineIds,
      onChange: setCustomBudgetLineIds,
      width: 240
    })))), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 14,
        flexWrap: "wrap",
        marginBottom: 18
      }
    }, /*#__PURE__*/React.createElement(KPI, {
      label: "Opérations trouvées",
      value: String(customKpis.count)
    }), /*#__PURE__*/React.createElement(KPI, {
      label: "Total dépenses",
      value: eur(customKpis.sumExpenses),
      accent: C?.brick || "#A8503C"
    }), /*#__PURE__*/React.createElement(KPI, {
      label: "Total recettes",
      value: eur(customKpis.sumIncome),
      accent: C?.pine || "#2F5D50"
    }), /*#__PURE__*/React.createElement(KPI, {
      label: "Dépense moyenne",
      value: eur(customKpis.avgExpense),
      accent: C?.brick || "#A8503C"
    }), /*#__PURE__*/React.createElement(KPI, {
      label: "Recette moyenne",
      value: eur(customKpis.avgIncome),
      accent: C?.pine || "#2F5D50"
    })), /*#__PURE__*/React.createElement(SectionCard, {
      title: "Détail des opérations",
      subtitle: `${customFilteredOps.length} opération(s) correspondant aux filtres actifs (comptabilisées + en attente)`
    }, customFilteredOps.length === 0 ? /*#__PURE__*/React.createElement("div", {
      style: {
        color: C?.inkSoft || "#6B7278",
        fontSize: 13,
        padding: "16px 4px"
      }
    }, "Aucune opération ne correspond aux filtres sélectionnés.") : /*#__PURE__*/React.createElement("div", {
      style: {
        overflowX: "auto",
        overflowY: "auto",
        maxHeight: 480,
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 10,
        background: C?.panel || "#FFFFFF"
      }
    }, /*#__PURE__*/React.createElement("table", {
      style: {
        width: "100%",
        borderCollapse: "collapse",
        minWidth: 780,
        fontSize: 12.5
      }
    }, /*#__PURE__*/React.createElement("thead", null, /*#__PURE__*/React.createElement("tr", null, /*#__PURE__*/React.createElement("th", hdrCellCustom("date"), "Date", customIcon("date")), /*#__PURE__*/React.createElement("th", hdrCellCustom("origin"), "Statut", customIcon("origin")), /*#__PURE__*/React.createElement("th", hdrCellCustom("label"), "Libellé", customIcon("label")), /*#__PURE__*/React.createElement("th", hdrCellCustom("categoryLabel"), "Catégorie", customIcon("categoryLabel")), /*#__PURE__*/React.createElement("th", hdrCellCustom("budgetLineLabel"), "Ligne de budget", customIcon("budgetLineLabel")), /*#__PURE__*/React.createElement("th", hdrCellCustom("amount", "right"), "Montant", customIcon("amount")))), /*#__PURE__*/React.createElement("tbody", null, customFilteredOps.map(op => {
      const om = originMeta(op.origin);
      const cellS = {
        padding: "8px 10px",
        borderBottom: `1px solid ${C?.line || "#DED6C4"}`,
        verticalAlign: "middle"
      };
      return /*#__PURE__*/React.createElement("tr", {
        key: op.key
      }, /*#__PURE__*/React.createElement("td", {
        style: {
          ...cellS,
          fontFamily: "'IBM Plex Mono', monospace",
          whiteSpace: "nowrap"
        }
      }, op.date ? new Date(op.date).toLocaleDateString("fr-FR") : "—"), /*#__PURE__*/React.createElement("td", {
        style: cellS
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          display: "inline-flex",
          alignItems: "center",
          gap: 5,
          padding: "3px 9px",
          borderRadius: 12,
          fontSize: 11,
          fontWeight: 700,
          background: om.bg,
          border: `1px solid ${om.border}`,
          color: om.dot,
          whiteSpace: "nowrap"
        }
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          width: 7,
          height: 7,
          borderRadius: "50%",
          background: om.dot
        }
      }), om.label)), /*#__PURE__*/React.createElement("td", {
        style: {
          ...cellS,
          fontWeight: 600,
          maxWidth: 220,
          overflow: "hidden",
          textOverflow: "ellipsis",
          whiteSpace: "nowrap"
        },
        title: op.label
      }, op.label), /*#__PURE__*/React.createElement("td", {
        style: {
          ...cellS,
          maxWidth: 180,
          overflow: "hidden",
          textOverflow: "ellipsis",
          whiteSpace: "nowrap"
        },
        title: op.categoryLabel
      }, op.categoryLabel), /*#__PURE__*/React.createElement("td", {
        style: {
          ...cellS,
          maxWidth: 200,
          overflow: "hidden",
          textOverflow: "ellipsis",
          whiteSpace: "nowrap"
        },
        title: op.budgetLineLabel
      }, op.budgetLineLabel), /*#__PURE__*/React.createElement("td", {
        style: {
          ...cellS,
          textAlign: "right",
          fontFamily: "'IBM Plex Mono', monospace",
          fontWeight: 600,
          color: op.amount < 0 ? C?.brick || "#A8503C" : C?.pine || "#2F5D50"
        }
      }, `${op.amount >= 0 ? "+" : ""}${eurExact(op.amount)}`));
    })))))));
  }
  exports.AnalyseView = AnalyseView;
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);