/**
 * Vue d'ensemble (Dashboard, KPIs, Graphique Trésorerie/Patrimoine, Jauges FIRE, Journal des mouvements)
 */
(function (exports) {
  'use strict';

  const {
    useState,
    useMemo,
    useRef
  } = React;
  const {
    C,
    eur
  } = exports.C ? exports : window.BudgetApp || {};
  const {
    calculateDetailedFinancialTimeline,
    projectPlacementBalanceAt,
    projectLoanCrdToDate,
    classifyAllocation
  } = exports.calculateDetailedFinancialTimeline ? exports : window.BudgetApp || {};
  const {
    SectionCard,
    KPI
  } = exports.SectionCard ? exports : window.BudgetApp || {};
  const {
    LineChartJS,
    AllocationChartJS,
    InteractiveTreasuryChart
  } = exports.LineChartJS ? exports : window.BudgetApp || {};
  const {
    HelpBadge
  } = exports.HelpBadge ? exports : window.BudgetApp || {};
  const {
    exportPatrimoineReportPDF
  } = exports.exportPatrimoineReportPDF ? exports : window.BudgetApp || {};
  const btnSmStyle = {
    fontSize: 11,
    padding: "3px 8px",
    borderRadius: 5,
    border: `1px solid ${C?.line || "#DED6C4"}`,
    background: C?.panelAlt || "#EFEAE0",
    color: C?.ink || "#232A2E",
    cursor: "pointer"
  };

  /* Une ligne de jauge pour un seul scénario (pessimiste, correct ou optimiste) */
  function ScenarioGaugeRow({
    label,
    color,
    revenu,
    retireCharges
  }) {
    const pct = retireCharges > 0 ? Math.min(100, revenu / retireCharges * 100) : 100;
    return /*#__PURE__*/React.createElement("div", {
      style: {
        flex: 1,
        minWidth: 180
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        justifyContent: "space-between",
        marginBottom: 4
      }
    }, /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 11.5,
        fontWeight: 700,
        color,
        textTransform: "uppercase",
        letterSpacing: 0.3
      }
    }, label), /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 12,
        fontWeight: 600,
        color: C?.ink || "#232A2E"
      }
    }, pct.toFixed(0), "%")), /*#__PURE__*/React.createElement("div", {
      style: {
        height: 12,
        background: C?.line || "#DED6C4",
        borderRadius: 6,
        overflow: "hidden",
        marginBottom: 4
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        height: "100%",
        width: `${pct}%`,
        background: color,
        transition: "width 0.5s ease"
      }
    })), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11,
        color: C?.inkSoft || "#6B7278"
      }
    }, eur(revenu), " / mois"));
  }

  /* Composant Jauge d'indépendance financière (FIRE) */
  function FIREGaugeCard({
    retireYear,
    retirePatrimoine,
    fireRente,
    retireCharges,
    openHelp
  }) {
    return /*#__PURE__*/React.createElement(SectionCard, {
      title: /*#__PURE__*/React.createElement("span", null, "Indépendance Financière (FIRE) — Règle des 4 %", openHelp && /*#__PURE__*/React.createElement(HelpBadge, {
        sectionKey: "overview",
        badgeId: "fire_gauge",
        onClick: openHelp,
        inline: true
      })),
      subtitle: `Hypothèse : vous liquidez la totalité de votre patrimoine mobilisable (placements ET immobilier) — projeté pour ${retireYear}, 3 scénarios`
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 24,
        flexWrap: "wrap",
        marginBottom: 10
      }
    }, /*#__PURE__*/React.createElement(ScenarioGaugeRow, {
      label: "Pessimiste",
      color: C?.brick || "#A8503C",
      revenu: fireRente.pess,
      retireCharges: retireCharges
    }), /*#__PURE__*/React.createElement(ScenarioGaugeRow, {
      label: "Correct",
      color: C?.pine || "#2F5D50",
      revenu: fireRente.corr,
      retireCharges: retireCharges
    }), /*#__PURE__*/React.createElement(ScenarioGaugeRow, {
      label: "Optimiste",
      color: C?.gold || "#93802E",
      revenu: fireRente.opti,
      retireCharges: retireCharges
    })), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11.5,
        color: C?.inkSoft || "#6B7278",
        lineHeight: 1.4
      }
    }, "Objectif à couvrir : ", eur(retireCharges), " / mois. En retirant 4 % par an de votre capital total projeté (scénario correct : ", eur(retirePatrimoine.corr), ", immobilier inclus), vous généreriez cette rente — pertinent seulement si vous comptez vendre votre immobilier à la retraite. Sinon, voir la jauge ci-dessous. L'immobilier n'a qu'une seule hypothèse de revalorisation dans l'outil, donc il est identique dans les 3 scénarios."));
  }

  /* Seconde jauge (Pensions + Placements seuls) */
  function CombinedFIREGaugeCard({
    retireYear,
    financialOnlyRente,
    totalPensions,
    retireCharges
  }) {
    return /*#__PURE__*/React.createElement(SectionCard, {
      title: "Couverture réaliste du niveau de vie (pensions + placements financiers)",
      subtitle: `Hypothèse différente de la jauge ci-dessus : l'immobilier n'est PAS vendu, les comptes de tiers (ex : enfants) sont exclus — seuls vos placements financiers mobilisables et vos pensions sont comparés à vos charges en ${retireYear}, 3 scénarios`
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 24,
        flexWrap: "wrap",
        marginBottom: 10
      }
    }, /*#__PURE__*/React.createElement(ScenarioGaugeRow, {
      label: "Pessimiste",
      color: C?.brick || "#A8503C",
      revenu: financialOnlyRente.pess + totalPensions,
      retireCharges: retireCharges
    }), /*#__PURE__*/React.createElement(ScenarioGaugeRow, {
      label: "Correct",
      color: C?.pine || "#2F5D50",
      revenu: financialOnlyRente.corr + totalPensions,
      retireCharges: retireCharges
    }), /*#__PURE__*/React.createElement(ScenarioGaugeRow, {
      label: "Optimiste",
      color: C?.gold || "#93802E",
      revenu: financialOnlyRente.opti + totalPensions,
      retireCharges: retireCharges
    })), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11.5,
        color: C?.inkSoft || "#6B7278",
        lineHeight: 1.4
      }
    }, "Objectif à couvrir : ", eur(retireCharges), " / mois. Pensions de retraite (identiques dans les 3 scénarios, indépendantes des marchés) : ", eur(totalPensions), " / mois. Rente des placements financiers (scénario correct) : ", eur(financialOnlyRente.corr), " / mois."));
  }

  /* Journal des virements et mouvements prévus */
  function ProjectedTransfersCard({
    data,
    years,
    useConstantEuros
  }) {
    const [scenario, setScenario] = useState("corr");
    const [showAll, setShowAll] = useState(false);
    const [sortConfig, setSortConfig] = useState({
      key: null,
      dir: "asc"
    });
    const [filters, setFilters] = useState({});
    const timeline = useMemo(() => calculateDetailedFinancialTimeline(data, years, scenario, useConstantEuros), [data, years, scenario, useConstantEuros]);
    const events = timeline.events || [];
    const columns = useMemo(() => [{
      key: "date",
      label: "Date",
      align: "left",
      value: e => e.dateISO,
      display: e => e.dateISO.split("-").reverse().join("/")
    }, {
      key: "source",
      label: "Source",
      align: "left",
      value: e => e.source,
      display: e => e.source
    }, {
      key: "sourceBefore",
      label: "Solde avant",
      align: "right",
      numeric: true,
      value: e => e.sourceBefore,
      display: e => eur(e.sourceBefore)
    }, {
      key: "amount",
      label: "Virement",
      align: "right",
      numeric: true,
      value: e => e.amount,
      display: e => `−${eur(e.amount)}`
    }, {
      key: "sourceAfter",
      label: "Solde après",
      align: "right",
      numeric: true,
      value: e => e.sourceAfter,
      display: e => eur(e.sourceAfter)
    }, {
      key: "target",
      label: "Cible",
      align: "left",
      value: e => e.target,
      display: e => e.target
    }, {
      key: "comment",
      label: "Commentaire",
      align: "left",
      value: e => e.comment || "",
      display: e => e.comment || "—"
    }], []);
    const activeFilterCount = Object.values(filters).filter(v => v && v.trim() !== "").length;
    const filteredSortedEvents = useMemo(() => {
      let result = events;
      if (activeFilterCount > 0) {
        result = result.filter(e => columns.every(col => {
          const term = (filters[col.key] || "").trim().toLowerCase();
          if (!term) return true;
          return String(col.display(e)).toLowerCase().includes(term);
        }));
      }
      if (sortConfig.key) {
        const col = columns.find(c => c.key === sortConfig.key);
        result = [...result].sort((a, b) => {
          const va = col.value(a),
            vb = col.value(b);
          const cmp = col.numeric ? (Number(va) || 0) - (Number(vb) || 0) : String(va).localeCompare(String(vb), "fr");
          return sortConfig.dir === "asc" ? cmp : -cmp;
        });
      }
      return result;
    }, [events, columns, filters, activeFilterCount, sortConfig]);
    const handleSort = key => {
      setSortConfig(prev => prev.key === key ? {
        key,
        dir: prev.dir === "asc" ? "desc" : "asc"
      } : {
        key,
        dir: "asc"
      });
    };
    const exportCSV = () => {
      const headers = ["Date", "Type", "Compte source", "Solde avant mouvement", "Montant", "Solde après mouvement", "Compte cible", "Commentaire"];
      const escape = v => `"${String(v ?? "").replace(/"/g, '""')}"`;
      const rows = events.map(e => [e.dateISO, e.type, e.source, e.sourceBefore.toFixed(2).replace(".", ","), e.amount.toFixed(2).replace(".", ","), e.sourceAfter.toFixed(2).replace(".", ","), e.target, e.comment].map(escape).join(";"));
      const csv = "\uFEFF" + [headers.map(escape).join(";"), ...rows].join("\r\n");
      const blob = new Blob([csv], {
        type: "text/csv;charset=utf-8;"
      });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `virements-prevus-${scenario}.csv`;
      a.click();
      URL.revokeObjectURL(url);
    };
    const visibleEvents = showAll ? filteredSortedEvents : filteredSortedEvents.slice(0, 60);
    return /*#__PURE__*/React.createElement(SectionCard, {
      title: "Virements & mouvements prévus",
      subtitle: "Tous les mouvements qui expliquent l'évolution de la trésorerie et des comptes dans la projection — uniquement les dates où quelque chose se passe. Cliquez sur une colonne pour trier, saisissez un texte dans son filtre pour la restreindre.",
      right: /*#__PURE__*/React.createElement("div", {
        style: {
          display: "flex",
          gap: 8,
          alignItems: "center",
          flexWrap: "wrap",
          justifyContent: "flex-end"
        }
      }, /*#__PURE__*/React.createElement("select", {
        value: scenario,
        onChange: e => setScenario(e.target.value),
        style: {
          padding: "5px 8px",
          borderRadius: 6,
          border: `1px solid ${C?.line || "#DED6C4"}`,
          fontSize: 12,
          background: C?.panel || "#FFFFFF",
          cursor: "pointer"
        }
      }, /*#__PURE__*/React.createElement("option", {
        value: "pess"
      }, "Pessimiste"), /*#__PURE__*/React.createElement("option", {
        value: "corr"
      }, "Correct"), /*#__PURE__*/React.createElement("option", {
        value: "opti"
      }, "Optimiste")), /*#__PURE__*/React.createElement("button", {
        onClick: exportCSV,
        style: {
          ...btnSmStyle,
          color: C?.pine || "#2F5D50",
          fontWeight: 600
        }
      }, "⬇ Exporter CSV"))
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 18,
        flexWrap: "wrap",
        marginBottom: 14,
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        alignItems: "center"
      }
    }, /*#__PURE__*/React.createElement("span", null, /*#__PURE__*/React.createElement("strong", {
      style: {
        color: C?.ink || "#232A2E"
      }
    }, filteredSortedEvents.length), activeFilterCount > 0 ? ` sur ${events.length} mouvements (filtré)` : " mouvements prévus"), /*#__PURE__*/React.createElement("span", null, "Scénario : ", /*#__PURE__*/React.createElement("strong", {
      style: {
        color: C?.pine || "#2F5D50"
      }
    }, scenario === "pess" ? "pessimiste" : scenario === "opti" ? "optimiste" : "correct")), activeFilterCount > 0 && /*#__PURE__*/React.createElement("button", {
      onClick: () => setFilters({}),
      style: btnSmStyle
    }, "✕ Réinitialiser les filtres")), events.length === 0 ? /*#__PURE__*/React.createElement("div", {
      style: {
        color: C?.inkSoft || "#6B7278",
        fontSize: 12.5
      }
    }, "Aucun mouvement prévu avec les données actuelles.") : /*#__PURE__*/React.createElement("div", {
      style: {
        maxHeight: 520,
        overflow: "auto",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 8
      }
    }, /*#__PURE__*/React.createElement("table", {
      style: {
        width: "100%",
        borderCollapse: "collapse",
        fontSize: 12,
        minWidth: 980
      }
    }, /*#__PURE__*/React.createElement("thead", {
      style: {
        position: "sticky",
        top: 0,
        background: C?.panelAlt || "#EFEAE0",
        zIndex: 1
      }
    }, /*#__PURE__*/React.createElement("tr", null, columns.map(col => /*#__PURE__*/React.createElement("th", {
      key: col.key,
      onClick: () => handleSort(col.key),
      title: "Cliquer pour trier",
      style: {
        textAlign: col.align,
        padding: "8px 7px",
        color: sortConfig.key === col.key ? C?.pine || "#2F5D50" : C?.inkSoft || "#6B7278",
        fontSize: 10.5,
        textTransform: "uppercase",
        letterSpacing: 0.3,
        borderBottom: `1px solid ${C?.line || "#DED6C4"}`,
        whiteSpace: "nowrap",
        cursor: "pointer",
        userSelect: "none"
      }
    }, col.label, sortConfig.key === col.key ? sortConfig.dir === "asc" ? " ▲" : " ▼" : ""))), /*#__PURE__*/React.createElement("tr", null, columns.map(col => /*#__PURE__*/React.createElement("th", {
      key: col.key,
      style: {
        padding: "4px 7px",
        borderBottom: `1px solid ${C?.line || "#DED6C4"}`,
        background: C?.panelAlt || "#EFEAE0"
      }
    }, /*#__PURE__*/React.createElement("input", {
      type: "text",
      value: filters[col.key] || "",
      onChange: e => setFilters(f => ({
        ...f,
        [col.key]: e.target.value
      })),
      placeholder: "Filtrer…",
      style: {
        width: "100%",
        fontSize: 11,
        padding: "3px 6px",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 4,
        background: C?.panel || "#FFFFFF",
        textAlign: col.align,
        boxSizing: "border-box"
      }
    }))))), /*#__PURE__*/React.createElement("tbody", null, visibleEvents.length === 0 ? /*#__PURE__*/React.createElement("tr", null, /*#__PURE__*/React.createElement("td", {
      colSpan: columns.length,
      style: {
        padding: "16px 7px",
        textAlign: "center",
        color: C?.inkSoft || "#6B7278"
      }
    }, "Aucun mouvement ne correspond aux filtres.")) : visibleEvents.map((e, i) => /*#__PURE__*/React.createElement("tr", {
      key: `${e.timestamp}-${i}`,
      style: {
        borderBottom: `1px solid ${C?.line || "#DED6C4"}`
      }
    }, columns.map(col => /*#__PURE__*/React.createElement("td", {
      key: col.key,
      style: {
        padding: "7px",
        textAlign: col.align,
        whiteSpace: col.key === "date" ? "nowrap" : undefined,
        fontFamily: col.numeric ? "'IBM Plex Mono', monospace" : undefined,
        color: col.key === "amount" ? C?.brick || "#A8503C" : col.key === "comment" && !e.comment ? "transparent" : undefined
      }
    }, col.display(e)))))))), filteredSortedEvents.length > 60 && /*#__PURE__*/React.createElement("div", {
      style: {
        marginTop: 10,
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        fontSize: 11.5,
        color: C?.inkSoft || "#6B7278"
      }
    }, /*#__PURE__*/React.createElement("span", null, showAll ? `Affichage des ${filteredSortedEvents.length} mouvements` : `Affichage des 60 premiers mouvements sur ${filteredSortedEvents.length}`), /*#__PURE__*/React.createElement("button", {
      onClick: () => setShowAll(v => !v),
      style: btnSmStyle
    }, showAll ? "Réduire" : "Afficher tout")), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11,
        color: C?.inkSoft || "#6B7278",
        marginTop: 12,
        lineHeight: 1.45
      }
    }, "💡 Les paiements récurrents utilisent le 1er jour du mois comme date de simulation. Les virements automatiques indiquent pourquoi le moteur les déclenche. Le CSV contient la totalité des mouvements (non filtrés), pas seulement ceux affichés à l'écran."));
  }

  /* Asset Allocation Card */
  function AssetAllocationCard({
    data,
    openHelp
  }) {
    const todayISO = useMemo(() => new Date().toISOString().slice(0, 10), []);
    const [simDate, setSimDate] = useState(todayISO);
    const [viewMode, setViewMode] = useState("bars");
    const {
      allocation,
      unmatched
    } = useMemo(() => {
      const target = simDate || todayISO;
      const getBalance = p => projectPlacementBalanceAt(p, target, "rateCorr", data.transfers);
      const initialCash = Number(data.settings.startBalance) || 0;
      return classifyAllocation(data.placements, getBalance, initialCash, data.assetCategories);
    }, [data, simDate, todayISO]);
    const totalPatrimoineBrut = allocation.reduce((s, a) => s + a.amount, 0);
    const isProjected = simDate && simDate !== todayISO;
    return /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 10,
        padding: 20,
        marginBottom: 20
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        marginBottom: 12,
        flexWrap: "wrap",
        gap: 10
      }
    }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontFamily: "'Newsreader', serif",
        fontSize: 18,
        color: C?.ink || "#232A2E",
        fontWeight: 600,
        display: "flex",
        alignItems: "center",
        gap: 8
      }
    }, /*#__PURE__*/React.createElement("span", null, "Répartition d'actifs (Asset Allocation)"), openHelp && /*#__PURE__*/React.createElement(HelpBadge, {
      sectionKey: "overview",
      badgeId: "asset_allocation",
      onClick: openHelp,
      inline: true
    })), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12.5,
        color: C?.inkSoft || "#6B7278",
        marginTop: 3
      }
    }, isProjected ? "Projection à la date choisie (versements prévus + scénario correct appliqués mois par mois)" : "Ventilation exacte issue de vos relevés financiers (Livrets, AV, SCPI, PEA, PER)")), /*#__PURE__*/React.createElement("div", {
      style: {
        fontFamily: "'IBM Plex Mono', monospace",
        fontSize: 18,
        color: C?.navy || "#28394A",
        fontWeight: 600
      }
    }, eur(totalPatrimoineBrut))), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 20,
        alignItems: "center",
        flexWrap: "wrap",
        marginBottom: 16,
        paddingBottom: 14,
        borderBottom: `1px solid ${C?.line || "#DED6C4"}`
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        alignItems: "center",
        gap: 8
      }
    }, /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        fontWeight: 600
      }
    }, "Date de simulation"), /*#__PURE__*/React.createElement("input", {
      type: "date",
      value: simDate,
      onChange: e => setSimDate(e.target.value),
      style: {
        padding: "5px 8px",
        borderRadius: 6,
        border: `1px solid ${C?.line || "#DED6C4"}`,
        fontSize: 12.5,
        background: C?.panel || "#FFFFFF"
      }
    }), isProjected && /*#__PURE__*/React.createElement("button", {
      onClick: () => setSimDate(todayISO),
      style: {
        fontSize: 11,
        color: C?.pine || "#2F5D50",
        background: "none",
        border: "none",
        cursor: "pointer",
        textDecoration: "underline",
        padding: 0
      }
    }, "revenir à aujourd'hui")), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        alignItems: "center",
        gap: 8
      }
    }, /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        fontWeight: 600
      }
    }, "Affichage"), /*#__PURE__*/React.createElement("select", {
      value: viewMode,
      onChange: e => setViewMode(e.target.value),
      style: {
        padding: "5px 8px",
        borderRadius: 6,
        border: `1px solid ${C?.line || "#DED6C4"}`,
        fontSize: 12.5,
        background: C?.panel || "#FFFFFF",
        cursor: "pointer"
      }
    }, /*#__PURE__*/React.createElement("option", {
      value: "bars"
    }, "Barre de ventilation"), /*#__PURE__*/React.createElement("option", {
      value: "pie"
    }, "Camembert"), /*#__PURE__*/React.createElement("option", {
      value: "hist"
    }, "Histogramme")))), viewMode === "bars" && /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        height: 14,
        borderRadius: 7,
        overflow: "hidden",
        marginBottom: 16
      }
    }, allocation.map(item => /*#__PURE__*/React.createElement("div", {
      key: item.label,
      style: {
        width: `${item.pct}%`,
        background: item.color
      },
      title: `${item.label} : ${item.pct.toFixed(1)}%`
    }))), (viewMode === "pie" || viewMode === "hist") && /*#__PURE__*/React.createElement("div", {
      style: {
        marginBottom: 16
      }
    }, /*#__PURE__*/React.createElement(AllocationChartJS, {
      allocation: allocation,
      mode: viewMode === "pie" ? "pie" : "hist",
      height: 260
    })), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "grid",
        gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
        gap: 12
      }
    }, allocation.map(item => /*#__PURE__*/React.createElement("div", {
      key: item.label,
      style: {
        background: C?.panelAlt || "#EFEAE0",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 8,
        padding: "10px 12px"
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        alignItems: "center",
        gap: 8,
        fontSize: 12,
        fontWeight: 600,
        color: C?.ink || "#232A2E"
      }
    }, /*#__PURE__*/React.createElement("span", {
      style: {
        width: 10,
        height: 10,
        borderRadius: "50%",
        background: item.color,
        display: "inline-block"
      }
    }), item.label), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "baseline",
        marginTop: 6
      }
    }, /*#__PURE__*/React.createElement("span", {
      style: {
        fontFamily: "'IBM Plex Mono', monospace",
        fontSize: 14,
        fontWeight: 600,
        color: C?.navy || "#28394A"
      }
    }, eur(item.amount)), /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 11.5,
        fontWeight: 600,
        color: C?.inkSoft || "#6B7278"
      }
    }, item.pct.toFixed(1), " %"))))), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11,
        color: C?.inkSoft || "#6B7278",
        marginTop: 12
      }
    }, "💡 ", /*#__PURE__*/React.createElement("em", null, "La projection applique, mois par mois jusqu'à la date choisie, les versements prévus et le taux « correct » de chaque placement. Le classement de chaque placement dans une classe d'actif dépend uniquement de sa catégorie (page Placements) et de la correspondance catégorie → classe d'actif définie dans Paramètres.")), unmatched.length > 0 && /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11.5,
        color: C?.brick || "#A8503C",
        marginTop: 8,
        background: "#FBEAEA",
        border: `1px solid ${C?.brick || "#A8503C"}`,
        borderRadius: 6,
        padding: "8px 10px"
      }
    }, "⚠️ ", /*#__PURE__*/React.createElement("strong", null, "Catégorie non reconnue"), " — comptés en \"Cash\" faute de correspondance dans Paramètres : ", unmatched.join(", "), "."));
  }

  /* Loans Summary Card */
  function LoansSummaryCard({
    data
  }) {
    const activeLoans = data?.loans || [];
    if (activeLoans.length === 0) return null;
    // CRD affiché ici = CRD théorique amorti jusqu'à aujourd'hui depuis la "Date CRD" de
    // référence saisie sur la page Patrimoine (jamais l.crd brut, qui reste figé à cette
    // date de référence tant qu'on ne l'a pas ressaisi manuellement).
    const today = new Date();
    const crdToday = l => projectLoanCrdToDate ? projectLoanCrdToDate(l, today) : Number(l.crd) || 0;
    const totalCRDInitial = activeLoans.reduce((sum, l) => sum + crdToday(l), 0);
    return /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 10,
        padding: 20,
        marginBottom: 20
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        marginBottom: 14,
        flexWrap: "wrap",
        gap: 10
      }
    }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontFamily: "'Newsreader', serif",
        fontSize: 18,
        color: C?.ink || "#232A2E",
        fontWeight: 600
      }
    }, "Passif & Crédits Immobiliers"), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12.5,
        color: C?.inkSoft || "#6B7278",
        marginTop: 3
      }
    }, "Capital Restant Dû (CRD) et tableaux d'amortissement exacts")), /*#__PURE__*/React.createElement("div", {
      style: {
        textAlign: "right"
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11,
        textTransform: "uppercase",
        letterSpacing: 0.4,
        color: C?.inkSoft || "#6B7278",
        fontWeight: 600
      }
    }, "Passif Restant Actuel"), /*#__PURE__*/React.createElement("div", {
      style: {
        fontFamily: "'IBM Plex Mono', monospace",
        fontSize: 18,
        color: C?.brick || "#A8503C",
        fontWeight: 600
      }
    }, eur(totalCRDInitial)))), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "grid",
        gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))",
        gap: 14
      }
    }, activeLoans.map((l, i) => /*#__PURE__*/React.createElement("div", {
      key: l.id || i,
      style: {
        background: C?.brickSoft || "#F4E4DF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 8,
        padding: 14
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 13,
        fontWeight: 600,
        color: C?.brick || "#A8503C"
      }
    }, l.label), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        marginTop: 4
      }
    }, "Taux hors assurance : ", /*#__PURE__*/React.createElement("strong", null, ((Number(l.rate) || 0) * 100).toFixed(2), " %"), " | Assurance : ", (Number(l.insurance) || 0).toFixed(2), " €/mois"), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        justifyContent: "space-between",
        marginTop: 10,
        fontSize: 12.5
      }
    }, /*#__PURE__*/React.createElement("span", null, "Mensualité : ", /*#__PURE__*/React.createElement("strong", null, (Number(l.monthly) || 0).toFixed(2), " €")), /*#__PURE__*/React.createElement("span", null, "CRD actuel : ", /*#__PURE__*/React.createElement("strong", {
      style: {
        fontFamily: "'IBM Plex Mono', monospace"
      }
    }, eur(crdToday(l))))), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11.5,
        color: C?.inkSoft || "#6B7278",
        marginTop: 6
      }
    }, "Fin prévue : ", /*#__PURE__*/React.createElement("strong", null, l.endDate ? l.endDate.split("-").reverse().join("/") : "Inconnue"))))));
  }

  /* Real Estate Card */
  function RealEstateCard({
    data
  }) {
    const items = data?.realEstate || [];
    const currentYear = new Date().getFullYear();
    const retireYear = (Number(data?.settings.birthYear) || 1985) + (Number(data?.settings.retireAge) || 64);
    const totalCurrentValue = items.reduce((s, r) => s + (Number(r.currentValue) || 0), 0);
    const totalAtRetire = items.reduce((s, r) => {
      const elapsed = retireYear - (Number(r.valuationYear) || currentYear);
      return s + (Number(r.currentValue) || 0) * Math.pow(1 + (Number(r.annualGrowthRate) || 0), Math.max(0, elapsed));
    }, 0);
    if (items.length === 0) return null;
    return /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 10,
        padding: 20,
        marginBottom: 20
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        marginBottom: 14,
        flexWrap: "wrap",
        gap: 10
      }
    }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontFamily: "'Newsreader', serif",
        fontSize: 18,
        color: C?.ink || "#232A2E",
        fontWeight: 600
      }
    }, "Actif Immobilier Physique"), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12.5,
        color: C?.inkSoft || "#6B7278",
        marginTop: 3
      }
    }, "Résidence principale, terrains, nu-propriété — valorisation annuelle intégrée au patrimoine net")), /*#__PURE__*/React.createElement("div", {
      style: {
        textAlign: "right"
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11,
        textTransform: "uppercase",
        letterSpacing: 0.4,
        color: C?.inkSoft || "#6B7278",
        fontWeight: 600
      }
    }, "Valeur actuelle estimée"), /*#__PURE__*/React.createElement("div", {
      style: {
        fontFamily: "'IBM Plex Mono', monospace",
        fontSize: 18,
        color: C?.navy || "#28394A",
        fontWeight: 600
      }
    }, eur(totalCurrentValue)))), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "grid",
        gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))",
        gap: 14
      }
    }, items.map((r, i) => {
      const elapsed = retireYear - (Number(r.valuationYear) || currentYear);
      const valueAtRetire = (Number(r.currentValue) || 0) * Math.pow(1 + (Number(r.annualGrowthRate) || 0), Math.max(0, elapsed));
      const growth = Number(r.annualGrowthRate) || 0;
      return /*#__PURE__*/React.createElement("div", {
        key: r.id || i,
        style: {
          background: C?.pineSoft || "#E3ECE8",
          border: `1px solid ${C?.line || "#DED6C4"}`,
          borderRadius: 8,
          padding: 14
        }
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          display: "flex",
          justifyContent: "space-between",
          alignItems: "flex-start"
        }
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          fontSize: 13,
          fontWeight: 600,
          color: C?.pine || "#2F5D50"
        }
      }, r.label || "Sans nom"), /*#__PURE__*/React.createElement("div", {
        style: {
          fontSize: 11,
          background: C?.pine || "#2F5D50",
          color: "#fff",
          borderRadius: 10,
          padding: "2px 8px",
          fontWeight: 600
        }
      }, r.type || "Bien")), /*#__PURE__*/React.createElement("div", {
        style: {
          fontFamily: "'IBM Plex Mono', monospace",
          fontSize: 20,
          color: C?.navy || "#28394A",
          fontWeight: 600,
          marginTop: 10
        }
      }, eur(Number(r.currentValue) || 0)), /*#__PURE__*/React.createElement("div", {
        style: {
          fontSize: 11.5,
          color: C?.inkSoft || "#6B7278",
          marginTop: 4
        }
      }, "Estimé en ", r.valuationYear || currentYear, " · Revalorisation : ", /*#__PURE__*/React.createElement("strong", null, "+", (growth * 100).toFixed(1), " % / an")), /*#__PURE__*/React.createElement("div", {
        style: {
          display: "flex",
          justifyContent: "space-between",
          marginTop: 10,
          fontSize: 12,
          borderTop: `1px solid ${C?.line || "#DED6C4"}`,
          paddingTop: 8
        }
      }, /*#__PURE__*/React.createElement("span", {
        style: {
          color: C?.inkSoft || "#6B7278"
        }
      }, "Valeur à la retraite (", retireYear, ")"), /*#__PURE__*/React.createElement("span", {
        style: {
          fontFamily: "'IBM Plex Mono', monospace",
          fontWeight: 600,
          color: C?.pine || "#2F5D50"
        }
      }, eur(valueAtRetire))), r.notes && /*#__PURE__*/React.createElement("div", {
        style: {
          fontSize: 11,
          color: C?.inkSoft || "#6B7278",
          marginTop: 6,
          fontStyle: "italic"
        }
      }, r.notes));
    })), items.length > 1 && /*#__PURE__*/React.createElement("div", {
      style: {
        marginTop: 14,
        display: "flex",
        justifyContent: "flex-end"
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12.5,
        color: C?.inkSoft || "#6B7278"
      }
    }, "Valeur totale projetée à la retraite (", retireYear, ") : ", /*#__PURE__*/React.createElement("strong", {
      style: {
        fontFamily: "'IBM Plex Mono', monospace",
        color: C?.pine || "#2F5D50"
      }
    }, eur(totalAtRetire)))));
  }

  /**
   * Composant principal OverviewView
   */
  function OverviewView({
    data,
    years,
    useConstantEuros,
    fluxNetActuel,
    patrimoineActuel,
    retireYear,
    patrimoine,
    pivotBalance,
    retireCharges,
    totalPensions,
    retirePatrimoine,
    fireRente,
    financialOnlyRente,
    openHelp
  }) {
    const handleExportPatrimoinePDF = () => {
      const pivotLabel = pivotBalance !== null ? data.settings.pivotDate ? `Solde réel au ${new Date(data.settings.pivotDate + 'T12:00:00').toLocaleDateString('fr-FR', {
        day: 'numeric',
        month: 'short',
        year: 'numeric'
      })}` : "Solde réel (Date Pivot)" : "Trésorerie de départ";
      exportPatrimoineReportPDF(data, {
        patrimoineActuel,
        pivotBalance,
        pivotLabel
      });
    };
    return /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        justifyContent: "flex-end",
        marginBottom: 10
      }
    }, /*#__PURE__*/React.createElement("button", {
      onClick: handleExportPatrimoinePDF,
      style: {
        ...btnSmStyle,
        fontSize: 12,
        padding: "6px 12px",
        color: C?.pine || "#2F5D50",
        fontWeight: 600
      },
      title: "Ouvre un aperçu imprimable du bilan patrimonial — choisissez « Microsoft Print to PDF » dans la boîte d'impression pour l'enregistrer en PDF"
    }, "📄 Exporter le bilan (PDF)")), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 16,
        marginBottom: 22,
        flexWrap: "wrap"
      }
    }, (() => {
      if (pivotBalance !== null) {
        const pivotLabel = data.settings.pivotDate ? `Solde réel au ${new Date(data.settings.pivotDate + 'T12:00:00').toLocaleDateString('fr-FR', {
          day: 'numeric',
          month: 'short',
          year: 'numeric'
        })}` : "Solde réel (Date Pivot)";
        return /*#__PURE__*/React.createElement(KPI, {
          label: pivotLabel,
          value: eur(pivotBalance),
          accent: C?.pine || "#2F5D50",
          sub: "📌 Date Pivot active"
        });
      }
      return /*#__PURE__*/React.createElement(KPI, {
        label: "Trésorerie de départ",
        value: eur(data?.settings.startBalance),
        accent: C?.pine || "#2F5D50"
      });
    })(), /*#__PURE__*/React.createElement(KPI, {
      label: "Patrimoine placé actuel",
      value: eur(patrimoineActuel),
      accent: C?.navy || "#28394A"
    }), /*#__PURE__*/React.createElement(KPI, {
      label: "Flux net — année en cours",
      value: eur(fluxNetActuel),
      accent: fluxNetActuel >= 0 ? C?.pine || "#2F5D50" : C?.brick || "#A8503C"
    }), /*#__PURE__*/React.createElement(KPI, {
      label: "Année de retraite visée",
      value: retireYear,
      sub: `Projection jusqu'en ${years[years.length - 1] || ""}`
    })), /*#__PURE__*/React.createElement(SectionCard, {
      title: "Trésorerie disponible cumulée, Passif & Patrimoine Net",
      subtitle: "Total des avoirs bruts, passif restant (crédits), patrimoine net réel et trésorerie dispo. Zoom molette et glisser-déposer horizontal."
    }, /*#__PURE__*/React.createElement(InteractiveTreasuryChart, {
      data: data,
      years: years,
      height: 350,
      useConstantEuros: useConstantEuros
    })), /*#__PURE__*/React.createElement(ProjectedTransfersCard, {
      data: data,
      years: years,
      useConstantEuros: useConstantEuros
    }), /*#__PURE__*/React.createElement(LoansSummaryCard, {
      data: data
    }), /*#__PURE__*/React.createElement(RealEstateCard, {
      data: data
    }), /*#__PURE__*/React.createElement(FIREGaugeCard, {
      retireYear: retireYear,
      retirePatrimoine: retirePatrimoine,
      fireRente: fireRente,
      retireCharges: retireCharges,
      openHelp: openHelp
    }), /*#__PURE__*/React.createElement(CombinedFIREGaugeCard, {
      retireYear: retireYear,
      financialOnlyRente: financialOnlyRente,
      totalPensions: totalPensions,
      retireCharges: retireCharges,
      openHelp: openHelp
    }), /*#__PURE__*/React.createElement(AssetAllocationCard, {
      data: data,
      openHelp: openHelp
    }), /*#__PURE__*/React.createElement(SectionCard, {
      title: "Patrimoine placé — 3 scénarios",
      subtitle: "Pessimiste, correct, optimiste (réglables dans l'onglet Patrimoine)"
    }, /*#__PURE__*/React.createElement(LineChartJS, {
      data: patrimoine?.totals || [],
      xKey: "year",
      series: [{
        key: "pess",
        label: "Pessimiste",
        color: C?.brick || "#A8503C"
      }, {
        key: "corr",
        label: "Correct",
        color: C?.pine || "#2F5D50",
        width: 3
      }, {
        key: "opti",
        label: "Optimiste",
        color: C?.gold || "#93802E"
      }]
    })));
  }
  exports.OverviewView = OverviewView;
  exports.FIREGaugeCard = FIREGaugeCard;
  exports.CombinedFIREGaugeCard = CombinedFIREGaugeCard;
  exports.ProjectedTransfersCard = ProjectedTransfersCard;
  exports.AssetAllocationCard = AssetAllocationCard;
  exports.LoansSummaryCard = LoansSummaryCard;
  exports.RealEstateCard = RealEstateCard;
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);