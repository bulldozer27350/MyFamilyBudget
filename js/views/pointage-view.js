/**
 * Vue Pointage (PointageView : Pointage mensuel prévisionnel vs réel, panel de rapprochement & alertes)
 */
(function (exports) {
  'use strict';

  const {
    useState,
    useMemo
  } = React;
  const {
    C,
    eur,
    eurExact
  } = exports.C ? exports : window.BudgetApp || {};
  const {
    chargeMonthlyForYear,
    incomeMonthlyForYear
  } = exports.chargeMonthlyForYear ? exports : window.BudgetApp || {};
  const inputStyle = {
    border: `1px solid ${C?.line || "#DED6C4"}`,
    borderRadius: 7,
    padding: "8px 10px",
    fontSize: 14,
    width: 140
  };
  function PointageView({
    data,
    update,
    openHelp
  }) {
    const today = new Date();
    const [selYear, setSelYear] = useState(today.getFullYear());
    const [selMonth, setSelMonth] = useState(today.getMonth() + 1);
    const [filterStatus, setFilterStatus] = useState("all");
    const [searchText, setSearchText] = useState("");
    const [sortKey, setSortKey] = useState("label");
    const [sortDir, setSortDir] = useState(1);
    const [panelLine, setPanelLine] = useState(null);
    const [panelSearch, setPanelSearch] = useState("");
    const [showUnpointedModal, setShowUnpointedModal] = useState(false);
    const [panelCategoryFilter, setPanelCategoryFilter] = useState("auto");
    const monthISO = `${selYear}-${String(selMonth).padStart(2, "0")}`;
    const monthLabel = new Date(selYear, selMonth - 1, 1).toLocaleDateString("fr-FR", {
      month: "long",
      year: "numeric"
    });
    const transactions = data?.bankImport?.transactions || [];
    const categories = data?.bankImport?.categories || [];
    const matchings = data?.bankImport?.matchings || [];

    // Lignes de budget actives ce mois
    const budgetLines = useMemo(() => {
      const inflationRate = Number(data?.settings?.inflationRate) || 0.02;
      const lines = [];
      const calcCharge = exports.chargeMonthlyForYear || window.BudgetApp && window.BudgetApp.chargeMonthlyForYear || chargeMonthlyForYear;
      const calcIncome = exports.incomeMonthlyForYear || window.BudgetApp && window.BudgetApp.incomeMonthlyForYear || incomeMonthlyForYear;
      (data?.charges || []).forEach(c => {
        const startOK = !c.start || monthISO >= c.start.slice(0, 7);
        const endOK = !c.end || monthISO <= c.end.slice(0, 7);
        if (!startOK || !endOK) return;
        const montant = calcCharge(c, selYear, inflationRate);
        if (montant <= 0) return;
        lines.push({
          id: c.id,
          label: c.label,
          kind: "charge",
          monthly: montant,
          categoryId: c.categoryId || null
        });
      });
      (data?.incomes || []).forEach(inc => {
        const startOK = !inc.start || monthISO >= inc.start.slice(0, 7);
        const endOK = !inc.end || monthISO <= inc.end.slice(0, 7);
        if (!startOK || !endOK) return;
        const montant = calcIncome(inc, selYear);
        if (montant <= 0) return;
        lines.push({
          id: inc.id,
          label: inc.label,
          kind: "revenu",
          monthly: montant,
          categoryId: inc.categoryId || null
        });
      });
      (data?.placements || []).forEach(p => {
        const m = Number(p.monthly) || 0;
        if (m <= 0) return;
        const fromOK = !p.monthlyFrom || monthISO >= p.monthlyFrom.slice(0, 7);
        const untilOK = !p.monthlyUntil || monthISO <= p.monthlyUntil.slice(0, 7);
        if (!fromOK || !untilOK) return;
        lines.push({
          id: p.id,
          label: `Épargne : ${p.label}`,
          kind: "placement",
          monthly: m,
          categoryId: p.categoryId || null
        });
      });
      return lines;
    }, [data?.charges, data?.incomes, data?.placements, selYear, monthISO, data?.settings?.inflationRate]);
    const monthTxs = useMemo(() => transactions.filter(t => t.date && t.date.slice(0, 7) === monthISO), [transactions, monthISO]);
    const matching = useMemo(() => matchings.find(m => m.month === monthISO) || {
      month: monthISO,
      links: []
    }, [matchings, monthISO]);
    const saveMatching = newLinks => {
      update("bankImport", b => {
        const others = (b?.matchings || []).filter(m => m.month !== monthISO);
        return {
          ...b,
          matchings: [...others, {
            month: monthISO,
            links: newLinks
          }]
        };
      });
    };
    const activeLineIds = useMemo(() => new Set(budgetLines.map(b => b.id)), [budgetLines]);
    const pointedTxIds = useMemo(() => {
      const s = new Set();
      (matching.links || []).forEach(l => {
        if (activeLineIds.has(l.budgetLineId)) {
          (l.txIds || []).forEach(id => s.add(id));
        }
      });
      return s;
    }, [matching, activeLineIds]);
    const monthBankSummary = useMemo(() => {
      let totalBankExpenses = 0;
      let totalBankIncome = 0;
      let unpointedCount = 0;
      let unpointedExpenses = 0;
      monthTxs.forEach(t => {
        const amt = Number(t.amount) || 0;
        if (amt < 0) totalBankExpenses += Math.abs(amt);else totalBankIncome += amt;
        if (!pointedTxIds.has(t.id)) {
          unpointedCount++;
          if (amt < 0) unpointedExpenses += Math.abs(amt);
        }
      });
      return {
        totalBankExpenses,
        totalBankIncome,
        unpointedCount,
        unpointedExpenses
      };
    }, [monthTxs, pointedTxIds]);
    const realByLine = useMemo(() => {
      const lineKindMap = {};
      budgetLines.forEach(b => {
        lineKindMap[b.id] = b.kind;
      });
      const map = {};
      (matching.links || []).forEach(l => {
        const kind = lineKindMap[l.budgetLineId] || "charge";
        const total = (l.txIds || []).reduce((s, txId) => {
          const tx = transactions.find(t => t.id === txId);
          if (!tx) return s;
          const amt = Number(tx.amount) || 0;
          return s + (kind === "revenu" ? amt : -amt);
        }, 0);
        map[l.budgetLineId] = total;
      });
      return map;
    }, [matching, transactions, budgetLines]);
    const getStatus = line => {
      const links = (matching.links || []).find(l => l.budgetLineId === line.id);
      if (!links || (links.txIds || []).length === 0) return "pending";
      const reel = realByLine[line.id] || 0;
      const prevu = line.monthly;
      const diff = Math.abs(reel - prevu);
      const tolerance = Math.max(1, prevu * 0.02);
      if (diff <= tolerance) return "match";
      if (line.kind === "revenu" || line.kind === "placement") {
        return reel > prevu ? "economy" : "over";
      }
      return reel < prevu ? "economy" : "over";
    };
    const getStatusMeta = (status, kind = "charge") => {
      if (status === "match") return {
        label: "Conforme",
        dot: "#2563EB",
        bg: "#EFF6FF",
        border: "#BFDBFE"
      };
      if (status === "pending") return {
        label: "En attente",
        dot: "#D97706",
        bg: "#FFFBEB",
        border: "#FDE68A"
      };
      if (status === "economy") {
        return {
          label: kind === "revenu" ? "Surplus (+)" : kind === "placement" ? "Plus épargné (+)" : "Économie (-)",
          dot: "#16A34A",
          bg: "#F0FDF4",
          border: "#BBF7D0"
        };
      }
      if (status === "over") {
        return {
          label: kind === "revenu" ? "Déficit (-)" : kind === "placement" ? "Moins épargné (-)" : "Dépassement (+)",
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
    const autoMatch = () => {
      const newLinks = (matching.links || []).map(l => ({
        ...l,
        txIds: [...(l.txIds || [])]
      }));
      const getLink = lineId => {
        let l = newLinks.find(x => x.budgetLineId === lineId);
        if (!l) {
          l = {
            budgetLineId: lineId,
            txIds: []
          };
          newLinks.push(l);
        }
        return l;
      };
      const usedTxIds = new Set();
      newLinks.forEach(l => (l.txIds || []).forEach(id => usedTxIds.add(id)));
      budgetLines.forEach(line => {
        if (!line.categoryId) return;
        const existing = (matching.links || []).find(l => l.budgetLineId === line.id);
        if (existing && (existing.txIds || []).length > 0) return;
        const candidates = monthTxs.filter(t => t.categoryId === line.categoryId && !usedTxIds.has(t.id));
        if (candidates.length === 0) return;
        const lnk = getLink(line.id);
        candidates.forEach(t => {
          lnk.txIds.push(t.id);
          usedTxIds.add(t.id);
        });
      });
      saveMatching(newLinks);
    };
    const toggleTx = (lineId, txId) => {
      const newLinks = (matching.links || []).map(l => ({
        ...l,
        txIds: [...(l.txIds || [])]
      }));
      const getOrCreate = id => {
        let l = newLinks.find(x => x.budgetLineId === id);
        if (!l) {
          l = {
            budgetLineId: id,
            txIds: []
          };
          newLinks.push(l);
        }
        return l;
      };
      newLinks.forEach(l => {
        l.txIds = l.txIds.filter(id => id !== txId);
      });
      const lnk = getOrCreate(lineId);
      lnk.txIds.push(txId);
      saveMatching(newLinks);
    };
    const unlinkAll = lineId => {
      const newLinks = (matching.links || []).map(l => l.budgetLineId === lineId ? {
        ...l,
        txIds: []
      } : l);
      saveMatching(newLinks);
    };
    const rows = useMemo(() => {
      return budgetLines.map(line => {
        const status = getStatus(line);
        const reel = realByLine[line.id] || 0;
        const ecart = reel - line.monthly;
        const ecartPct = line.monthly > 0 ? ecart / line.monthly * 100 : 0;
        const linkedTxs = ((matching.links || []).find(l => l.budgetLineId === line.id)?.txIds || []).map(id => transactions.find(t => t.id === id)).filter(Boolean);
        const cat = categories.find(c => c.id === line.categoryId);
        return {
          ...line,
          status,
          reel,
          ecart,
          ecartPct,
          linkedTxs,
          catLabel: cat?.label || "—"
        };
      });
    }, [budgetLines, matching, realByLine, transactions, categories]);
    const displayRows = useMemo(() => {
      let r = rows;
      if (filterStatus !== "all") r = r.filter(row => row.status === filterStatus);
      if (searchText.trim()) {
        const q = searchText.trim().toLowerCase();
        r = r.filter(row => row.label.toLowerCase().includes(q) || row.catLabel.toLowerCase().includes(q) || row.linkedTxs.some(t => t.label.toLowerCase().includes(q)));
      }
      return [...r].sort((a, b) => {
        let va = a[sortKey],
          vb = b[sortKey];
        if (typeof va === "string") va = va.toLowerCase();
        if (typeof vb === "string") vb = vb.toLowerCase();
        return va < vb ? -sortDir : va > vb ? sortDir : 0;
      });
    }, [rows, filterStatus, searchText, sortKey, sortDir]);
    const counts = useMemo(() => ({
      all: rows.length,
      match: rows.filter(r => r.status === "match").length,
      economy: rows.filter(r => r.status === "economy").length,
      over: rows.filter(r => r.status === "over").length,
      pending: rows.filter(r => r.status === "pending").length
    }), [rows]);
    const colSort = key => {
      if (sortKey === key) setSortDir(-sortDir);else {
        setSortKey(key);
        setSortDir(1);
      }
    };
    const sortIcon = key => sortKey === key ? sortDir === 1 ? " ↑" : " ↓" : "";
    const availableTxs = useMemo(() => {
      const q = panelSearch.trim().toLowerCase();
      const targetCatId = panelCategoryFilter === "auto" ? panelLine?.categoryId : panelCategoryFilter === "all" ? null : panelCategoryFilter;
      return monthTxs.filter(t => {
        if (pointedTxIds.has(t.id)) return false;
        if (targetCatId && t.categoryId !== targetCatId) return false;
        if (!q) return true;
        return t.label.toLowerCase().includes(q) || String(t.amount).includes(q);
      }).sort((a, b) => a.date < b.date ? -1 : 1);
    }, [monthTxs, pointedTxIds, panelSearch, panelCategoryFilter, panelLine]);
    const headerStyle = key => ({
      padding: "9px 10px",
      textAlign: "left",
      fontSize: 11.5,
      fontWeight: 700,
      color: C?.inkSoft || "#6B7278",
      background: C?.panelAlt || "#EFEAE0",
      cursor: "pointer",
      whiteSpace: "nowrap",
      borderBottom: `2px solid ${C?.line || "#DED6C4"}`,
      userSelect: "none",
      ...(sortKey === key ? {
        color: C?.pine || "#2F5D50"
      } : {})
    });
    const cellStyle = {
      padding: "9px 10px",
      fontSize: 12.5,
      borderBottom: `1px solid ${C?.line || "#DED6C4"}`,
      verticalAlign: "middle"
    };
    const navMonth = dir => {
      let m = selMonth + dir,
        y = selYear;
      if (m > 12) {
        m = 1;
        y++;
      }
      if (m < 1) {
        m = 12;
        y--;
      }
      setSelMonth(m);
      setSelYear(y);
    };
    const totalPrevu = rows.reduce((s, r) => s + r.monthly, 0);
    const totalReel = rows.reduce((s, r) => s + r.reel, 0);
    return /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-start",
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
    }, "Pointage & Rapprochement"), openHelp && /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => openHelp("pointage"),
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
      title: "Aide sur le pointage"
    }, "?")), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12.5,
        color: C?.inkSoft || "#6B7278",
        marginTop: 3
      }
    }, "Associez vos transactions réelles aux charges et revenus récurrents.")), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        alignItems: "center",
        gap: 8,
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 10,
        padding: "8px 14px"
      }
    }, /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => navMonth(-1),
      style: {
        background: "none",
        border: "none",
        cursor: "pointer",
        fontSize: 16,
        color: C?.ink || "#232A2E",
        padding: "0 4px"
      }
    }, "◄"), /*#__PURE__*/React.createElement("div", {
      style: {
        fontFamily: "'IBM Plex Mono', monospace",
        fontSize: 14,
        fontWeight: 700,
        color: C?.navy || "#28394A",
        minWidth: 140,
        textAlign: "center",
        textTransform: "capitalize"
      }
    }, monthLabel), /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => navMonth(1),
      style: {
        background: "none",
        border: "none",
        cursor: "pointer",
        fontSize: 16,
        color: C?.ink || "#232A2E",
        padding: "0 4px"
      }
    }, "►"))), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 12,
        marginBottom: 16,
        flexWrap: "wrap"
      }
    }, [{
      label: "Budget prévu",
      value: eur(totalPrevu),
      sub: "Total des prévisions",
      color: C?.navy || "#28394A",
      clickable: false
    }, {
      label: "Débits en banque",
      value: eur(monthBankSummary.totalBankExpenses),
      sub: "Total prélevé en banque",
      color: C?.ink || "#232A2E",
      clickable: false
    }, {
      label: "Pointé sur budget",
      value: eur(totalReel),
      sub: `${counts.match + counts.economy + counts.over} / ${counts.all} lignes pointées`,
      color: C?.pine || "#2F5D50",
      clickable: false
    }, {
      label: "Dépenses non pointées",
      value: eur(monthBankSummary.unpointedExpenses),
      sub: monthBankSummary.unpointedCount > 0 ? `${monthBankSummary.unpointedCount} transaction(s) — Voir ➔` : "Toutes les dépenses pointées ✓",
      color: monthBankSummary.unpointedCount > 0 ? "#D97706" : "#16A34A",
      clickable: monthBankSummary.unpointedCount > 0
    }].map(kpi => /*#__PURE__*/React.createElement("div", {
      key: kpi.label,
      onClick: kpi.clickable ? () => setShowUnpointedModal(true) : undefined,
      style: {
        background: kpi.clickable ? "#FFFBEB" : C?.panel || "#FFFFFF",
        border: `1px solid ${kpi.clickable ? "#FDE68A" : C?.line || "#DED6C4"}`,
        borderRadius: 10,
        padding: "12px 18px",
        minWidth: 160,
        cursor: kpi.clickable ? "pointer" : "default",
        boxShadow: kpi.clickable ? "0 2px 8px rgba(217,119,6,0.12)" : "none",
        transition: "transform 0.1s, boxShadow 0.1s"
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11,
        color: kpi.clickable ? "#92400E" : C?.inkSoft || "#6B7278",
        marginBottom: 3,
        fontWeight: kpi.clickable ? 700 : 400
      }
    }, kpi.label), /*#__PURE__*/React.createElement("div", {
      style: {
        fontFamily: "'IBM Plex Mono', monospace",
        fontSize: 17,
        fontWeight: 700,
        color: kpi.color
      }
    }, kpi.value), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 10.5,
        color: kpi.clickable ? "#B45309" : C?.inkSoft || "#6B7278",
        marginTop: 3,
        fontWeight: kpi.clickable ? 700 : 400
      }
    }, kpi.sub)))), monthBankSummary.unpointedCount > 0 && /*#__PURE__*/React.createElement("div", {
      style: {
        marginBottom: 18,
        padding: "11px 16px",
        borderRadius: 10,
        background: "#FFFBEB",
        border: "1px solid #FDE68A",
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: 12,
        flexWrap: "wrap"
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        alignItems: "center",
        gap: 10
      }
    }, /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 17
      }
    }, "⚠️"), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12.5,
        color: "#92400E"
      }
    }, /*#__PURE__*/React.createElement("strong", null, monthBankSummary.unpointedCount, " transaction(s) bancaire(s) (", eur(monthBankSummary.unpointedExpenses), ")"), " non associées au budget.")), /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => setShowUnpointedModal(true),
      style: {
        padding: "6px 14px",
        borderRadius: 7,
        fontSize: 11.5,
        fontWeight: 700,
        background: "#D97706",
        color: "#fff",
        border: "none",
        cursor: "pointer",
        display: "flex",
        alignItems: "center",
        gap: 6
      }
    }, "🔍 Visualiser les débits non pointés (", monthBankSummary.unpointedCount, ")")), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 10,
        alignItems: "center",
        marginBottom: 14,
        flexWrap: "wrap"
      }
    }, [{
      key: "all",
      label: `Tous (${counts.all})`,
      dot: C?.inkSoft || "#6B7278"
    }, {
      key: "match",
      label: `🔵 Conformes (${counts.match})`,
      dot: "#2563EB"
    }, {
      key: "economy",
      label: `🟢 Économies (${counts.economy})`,
      dot: "#16A34A"
    }, {
      key: "pending",
      label: `🟡 En attente (${counts.pending})`,
      dot: "#D97706"
    }, {
      key: "over",
      label: `🔴 Dépassements (${counts.over})`,
      dot: "#DC2626"
    }].map(f => /*#__PURE__*/React.createElement("button", {
      type: "button",
      key: f.key,
      onClick: () => setFilterStatus(f.key),
      style: {
        padding: "6px 13px",
        borderRadius: 20,
        fontSize: 12,
        fontWeight: 600,
        cursor: "pointer",
        border: filterStatus === f.key ? `2px solid ${f.dot}` : `1px solid ${C?.line || "#DED6C4"}`,
        background: filterStatus === f.key ? f.dot === (C?.inkSoft || "#6B7278") ? C?.panelAlt || "#EFEAE0" : f.dot + "18" : C?.panel || "#FFFFFF",
        color: filterStatus === f.key ? f.dot === (C?.inkSoft || "#6B7278") ? C?.ink || "#232A2E" : f.dot : C?.inkSoft || "#6B7278",
        transition: "all 0.15s"
      }
    }, f.label)), /*#__PURE__*/React.createElement("input", {
      value: searchText,
      onChange: e => setSearchText(e.target.value),
      placeholder: "🔍 Rechercher…",
      style: {
        ...inputStyle,
        width: 200,
        marginLeft: 4,
        fontSize: 12.5
      }
    }), /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: autoMatch,
      style: {
        marginLeft: "auto",
        padding: "8px 16px",
        borderRadius: 8,
        fontSize: 12.5,
        fontWeight: 700,
        background: C?.pine || "#2F5D50",
        color: "#fff",
        border: "none",
        cursor: "pointer",
        display: "flex",
        alignItems: "center",
        gap: 6
      }
    }, "⚡ Rapprochement automatique")), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 18
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        flex: 1,
        overflowX: "auto",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 10,
        background: C?.panel || "#FFFFFF"
      }
    }, /*#__PURE__*/React.createElement("table", {
      style: {
        width: "100%",
        borderCollapse: "collapse",
        minWidth: 980
      }
    }, /*#__PURE__*/React.createElement("thead", null, /*#__PURE__*/React.createElement("tr", null, /*#__PURE__*/React.createElement("th", {
      style: headerStyle("status"),
      onClick: () => colSort("status")
    }, "Statut", sortIcon("status")), /*#__PURE__*/React.createElement("th", {
      style: headerStyle("catLabel"),
      onClick: () => colSort("catLabel")
    }, "Catégorie", sortIcon("catLabel")), /*#__PURE__*/React.createElement("th", {
      style: headerStyle("kind"),
      onClick: () => colSort("kind")
    }, "Type", sortIcon("kind")), /*#__PURE__*/React.createElement("th", {
      style: headerStyle("label"),
      onClick: () => colSort("label")
    }, "Libellé Budget", sortIcon("label")), /*#__PURE__*/React.createElement("th", {
      style: {
        ...headerStyle("monthly"),
        textAlign: "right"
      },
      onClick: () => colSort("monthly")
    }, "Prévu (€)", sortIcon("monthly")), /*#__PURE__*/React.createElement("th", {
      style: {
        ...headerStyle("reel"),
        textAlign: "right"
      },
      onClick: () => colSort("reel")
    }, "Réel Constaté (€)", sortIcon("reel")), /*#__PURE__*/React.createElement("th", {
      style: {
        ...headerStyle("ecart"),
        textAlign: "right"
      },
      onClick: () => colSort("ecart")
    }, "Écart", sortIcon("ecart")), /*#__PURE__*/React.createElement("th", {
      style: headerStyle("_tx")
    }, "Transaction(s) Pointée(s)"), /*#__PURE__*/React.createElement("th", {
      style: {
        ...headerStyle("_act"),
        textAlign: "center",
        minWidth: 145,
        width: 145
      }
    }, "Actions"))), /*#__PURE__*/React.createElement("tbody", null, displayRows.length === 0 && /*#__PURE__*/React.createElement("tr", null, /*#__PURE__*/React.createElement("td", {
      colSpan: 9,
      style: {
        ...cellStyle,
        textAlign: "center",
        color: C?.inkSoft || "#6B7278",
        padding: 24
      }
    }, "Aucune ligne budgétaire active pour ce mois.")), displayRows.map(row => {
      const sm = getStatusMeta(row.status, row.kind);
      const isOpen = panelLine?.id === row.id;
      const ecartColor = row.status === "match" ? "#2563EB" : row.status === "economy" ? "#16A34A" : row.status === "over" ? "#DC2626" : C?.inkSoft || "#6B7278";
      return /*#__PURE__*/React.createElement("tr", {
        key: row.id,
        style: {
          background: isOpen ? sm.bg : "transparent",
          transition: "background 0.15s"
        }
      }, /*#__PURE__*/React.createElement("td", {
        style: cellStyle
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          display: "inline-flex",
          alignItems: "center",
          gap: 5,
          padding: "3px 9px",
          borderRadius: 12,
          fontSize: 11.5,
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
          background: sm.dot,
          flexShrink: 0
        }
      }), sm.label)), /*#__PURE__*/React.createElement("td", {
        style: {
          ...cellStyle,
          color: C?.inkSoft || "#6B7278",
          fontSize: 12
        }
      }, row.catLabel), /*#__PURE__*/React.createElement("td", {
        style: {
          ...cellStyle,
          fontSize: 12
        }
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
          ...cellStyle,
          fontWeight: 600,
          maxWidth: 200,
          whiteSpace: "nowrap",
          overflow: "hidden",
          textOverflow: "ellipsis"
        },
        title: row.label
      }, row.label), /*#__PURE__*/React.createElement("td", {
        style: {
          ...cellStyle,
          textAlign: "right",
          fontFamily: "'IBM Plex Mono', monospace"
        }
      }, eur(row.monthly)), /*#__PURE__*/React.createElement("td", {
        style: {
          ...cellStyle,
          textAlign: "right",
          fontFamily: "'IBM Plex Mono', monospace",
          fontWeight: row.reel > 0 ? 600 : 400,
          color: row.reel > 0 ? C?.ink || "#232A2E" : C?.inkSoft || "#6B7278"
        }
      }, row.reel > 0 ? eur(row.reel) : "—"), /*#__PURE__*/React.createElement("td", {
        style: {
          ...cellStyle,
          textAlign: "right",
          fontFamily: "'IBM Plex Mono', monospace",
          fontWeight: 600,
          color: ecartColor
        }
      }, row.status === "pending" ? "—" : `${row.ecart >= 0 ? "+" : ""}${eur(row.ecart)} (${row.ecartPct >= 0 ? "+" : ""}${row.ecartPct.toFixed(1)}%)`), /*#__PURE__*/React.createElement("td", {
        style: {
          ...cellStyle,
          maxWidth: 240
        }
      }, row.linkedTxs.length === 0 ? /*#__PURE__*/React.createElement("span", {
        style: {
          color: C?.inkSoft || "#6B7278",
          fontSize: 11.5,
          fontStyle: "italic"
        }
      }, "Non pointé") : /*#__PURE__*/React.createElement("div", {
        style: {
          display: "flex",
          flexDirection: "column",
          gap: 3
        }
      }, row.linkedTxs.slice(0, 2).map(tx => /*#__PURE__*/React.createElement("div", {
        key: tx.id,
        style: {
          fontSize: 11,
          color: C?.ink || "#232A2E",
          whiteSpace: "nowrap",
          overflow: "hidden",
          textOverflow: "ellipsis"
        },
        title: tx.label
      }, /*#__PURE__*/React.createElement("span", {
        style: {
          fontFamily: "'IBM Plex Mono', monospace",
          color: C?.inkSoft || "#6B7278",
          marginRight: 4
        }
      }, tx.date?.slice(5)), tx.label.slice(0, 40), tx.label.length > 40 ? "…" : "", /*#__PURE__*/React.createElement("span", {
        style: {
          fontFamily: "'IBM Plex Mono', monospace",
          marginLeft: 4,
          fontWeight: 600,
          color: tx.amount < 0 ? C?.brick || "#A8503C" : C?.pine || "#2F5D50"
        }
      }, eurExact(tx.amount)))), row.linkedTxs.length > 2 && /*#__PURE__*/React.createElement("div", {
        style: {
          fontSize: 11,
          color: C?.inkSoft || "#6B7278"
        }
      }, "+", row.linkedTxs.length - 2, " autre(s)…"))), /*#__PURE__*/React.createElement("td", {
        style: {
          ...cellStyle,
          textAlign: "center",
          minWidth: 145,
          width: 145
        }
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          display: "flex",
          gap: 6,
          justifyContent: "center",
          alignItems: "center"
        }
      }, /*#__PURE__*/React.createElement("button", {
        type: "button",
        onClick: () => setPanelLine(isOpen ? null : row),
        style: {
          padding: "4px 9px",
          borderRadius: 7,
          fontSize: 11.5,
          fontWeight: 600,
          cursor: "pointer",
          background: isOpen ? C?.pine || "#2F5D50" : C?.pineSoft || "#E3ECE8",
          color: isOpen ? "#fff" : C?.pine || "#2F5D50",
          border: `1px solid ${C?.pine || "#2F5D50"}`,
          transition: "all 0.15s",
          whiteSpace: "nowrap"
        }
      }, isOpen ? "✕ Fermer" : "🔗 Pointer"), row.linkedTxs.length > 0 && /*#__PURE__*/React.createElement("button", {
        type: "button",
        onClick: () => unlinkAll(row.id),
        style: {
          padding: "4px 9px",
          borderRadius: 7,
          fontSize: 11.5,
          fontWeight: 600,
          cursor: "pointer",
          background: C?.brickSoft || "#F4E4DF",
          color: C?.brick || "#A8503C",
          border: `1px solid ${C?.brick || "#A8503C"}`,
          whiteSpace: "nowrap"
        }
      }, "✕ Délier"))));
    })))), panelLine && /*#__PURE__*/React.createElement("div", {
      style: {
        width: 360,
        flexShrink: 0,
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 10,
        background: C?.panel || "#FFFFFF",
        display: "flex",
        flexDirection: "column",
        maxHeight: 640,
        overflow: "hidden",
        boxShadow: "0 4px 20px rgba(0,0,0,0.08)"
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        padding: "14px 16px",
        borderBottom: `1px solid ${C?.line || "#DED6C4"}`,
        background: C?.pineSoft || "#E3ECE8"
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontWeight: 700,
        fontSize: 13.5,
        color: C?.pine || "#2F5D50",
        marginBottom: 2
      }
    }, "🔗 ", panelLine.label), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11.5,
        color: C?.inkSoft || "#6B7278"
      }
    }, "Sélectionnez les transactions à associer (", monthLabel, ")")), (matching.links || []).find(l => l.budgetLineId === panelLine.id)?.txIds?.length > 0 && /*#__PURE__*/React.createElement("div", {
      style: {
        padding: "10px 14px",
        borderBottom: `1px solid ${C?.line || "#DED6C4"}`,
        background: "#F0FDF4"
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11,
        fontWeight: 700,
        color: "#16A34A",
        marginBottom: 6
      }
    }, "✅ Déjà pointées sur cette ligne :"), ((matching.links || []).find(l => l.budgetLineId === panelLine.id)?.txIds || []).map(txId => transactions.find(t => t.id === txId)).filter(Boolean).map(tx => /*#__PURE__*/React.createElement("div", {
      key: tx.id,
      style: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        fontSize: 11.5,
        marginBottom: 4
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        overflow: "hidden",
        textOverflow: "ellipsis",
        whiteSpace: "nowrap",
        flex: 1,
        marginRight: 6
      },
      title: tx.label
    }, /*#__PURE__*/React.createElement("span", {
      style: {
        color: C?.inkSoft || "#6B7278",
        fontFamily: "'IBM Plex Mono', monospace",
        marginRight: 4
      }
    }, tx.date?.slice(5)), tx.label.slice(0, 28), tx.label.length > 28 ? "…" : ""), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 4,
        alignItems: "center",
        flexShrink: 0
      }
    }, /*#__PURE__*/React.createElement("span", {
      style: {
        fontFamily: "'IBM Plex Mono', monospace",
        fontWeight: 700,
        color: tx.amount < 0 ? C?.brick || "#A8503C" : C?.pine || "#2F5D50",
        fontSize: 12
      }
    }, eurExact(tx.amount)), /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => {
        const newLinks = (matching.links || []).map(l => l.budgetLineId === panelLine.id ? {
          ...l,
          txIds: l.txIds.filter(id => id !== tx.id)
        } : l);
        saveMatching(newLinks);
      },
      style: {
        background: "none",
        border: "none",
        cursor: "pointer",
        color: C?.brick || "#A8503C",
        fontSize: 14,
        padding: "0 2px"
      },
      title: "Dissocier"
    }, "✕"))))), /*#__PURE__*/React.createElement("div", {
      style: {
        padding: "10px 14px",
        borderBottom: `1px solid ${C?.line || "#DED6C4"}`
      }
    }, panelLine.categoryId && /*#__PURE__*/React.createElement("div", {
      style: {
        marginBottom: 8,
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: 6
      }
    }, (() => {
      const catObj = categories.find(c => c.id === panelLine.categoryId);
      const isFiltered = panelCategoryFilter !== "all";
      return /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("button", {
        type: "button",
        onClick: () => setPanelCategoryFilter(isFiltered ? "all" : "auto"),
        style: {
          padding: "3px 9px",
          borderRadius: 12,
          fontSize: 11,
          fontWeight: 700,
          cursor: "pointer",
          background: isFiltered ? C?.pineSoft || "#E3ECE8" : C?.panelAlt || "#EFEAE0",
          color: isFiltered ? C?.pine || "#2F5D50" : C?.inkSoft || "#6B7278",
          border: `1px solid ${isFiltered ? C?.pine || "#2F5D50" : C?.line || "#DED6C4"}`,
          display: "flex",
          alignItems: "center",
          gap: 4
        }
      }, isFiltered ? `🎯 Catégorie : ${catObj?.label || "Liée"}` : "🌐 Toutes les catégories"), isFiltered ? /*#__PURE__*/React.createElement("button", {
        type: "button",
        onClick: () => setPanelCategoryFilter("all"),
        style: {
          background: "none",
          border: "none",
          cursor: "pointer",
          fontSize: 11,
          color: C?.inkSoft || "#6B7278",
          textDecoration: "underline"
        }
      }, "Afficher tout") : /*#__PURE__*/React.createElement("button", {
        type: "button",
        onClick: () => setPanelCategoryFilter("auto"),
        style: {
          background: "none",
          border: "none",
          cursor: "pointer",
          fontSize: 11,
          color: C?.pine || "#2F5D50",
          fontWeight: 600
        }
      }, "Re-filtrer"));
    })()), /*#__PURE__*/React.createElement("input", {
      value: panelSearch,
      onChange: e => setPanelSearch(e.target.value),
      placeholder: "🔍 Filtrer par mot-clé ou montant…",
      style: {
        ...inputStyle,
        width: "100%",
        fontSize: 12
      }
    })), /*#__PURE__*/React.createElement("div", {
      style: {
        flex: 1,
        overflowY: "auto",
        padding: "8px 0"
      }
    }, availableTxs.length === 0 && /*#__PURE__*/React.createElement("div", {
      style: {
        padding: "16px",
        textAlign: "center",
        color: C?.inkSoft || "#6B7278",
        fontSize: 12.5,
        fontStyle: "italic"
      }
    }, "Toutes les transactions de ce mois sont déjà pointées."), availableTxs.map(tx => {
      const txCat = categories.find(c => c.id === tx.categoryId);
      return /*#__PURE__*/React.createElement("div", {
        key: tx.id,
        onClick: () => toggleTx(panelLine.id, tx.id),
        style: {
          padding: "9px 14px",
          cursor: "pointer",
          borderBottom: `1px solid ${C?.line || "#DED6C4"}`,
          transition: "background 0.1s"
        },
        onMouseEnter: e => e.currentTarget.style.background = C?.pineSoft || "#E3ECE8",
        onMouseLeave: e => e.currentTarget.style.background = "transparent"
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          gap: 6
        }
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          fontSize: 11,
          color: C?.inkSoft || "#6B7278",
          fontFamily: "'IBM Plex Mono', monospace",
          flexShrink: 0
        }
      }, tx.date), /*#__PURE__*/React.createElement("div", {
        style: {
          fontFamily: "'IBM Plex Mono', monospace",
          fontWeight: 700,
          fontSize: 13,
          color: tx.amount < 0 ? C?.brick || "#A8503C" : C?.pine || "#2F5D50",
          flexShrink: 0
        }
      }, eurExact(tx.amount))), /*#__PURE__*/React.createElement("div", {
        style: {
          fontSize: 12,
          color: C?.ink || "#232A2E",
          marginTop: 2,
          overflow: "hidden",
          textOverflow: "ellipsis",
          whiteSpace: "nowrap"
        },
        title: tx.label
      }, tx.label), txCat && /*#__PURE__*/React.createElement("div", {
        style: {
          fontSize: 10.5,
          color: C?.inkSoft || "#6B7278",
          marginTop: 1
        }
      }, txCat.label));
    })), /*#__PURE__*/React.createElement("div", {
      style: {
        padding: "12px 14px",
        borderTop: `1px solid ${C?.line || "#DED6C4"}`,
        background: C?.panelAlt || "#EFEAE0",
        display: "flex",
        gap: 8,
        flexShrink: 0
      }
    }, /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => setPanelLine(null),
      style: {
        flex: 1,
        padding: "8px 12px",
        borderRadius: 8,
        fontSize: 12,
        fontWeight: 700,
        background: C?.pine || "#2F5D50",
        color: "#fff",
        border: "none",
        cursor: "pointer"
      }
    }, "✓ Terminer"), /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => {
        const idx = displayRows.findIndex(r => r.id === panelLine.id);
        if (idx !== -1 && idx < displayRows.length - 1) {
          setPanelLine(displayRows[idx + 1]);
          setPanelSearch("");
        } else {
          setPanelLine(null);
        }
      },
      style: {
        flex: 1,
        padding: "8px 12px",
        borderRadius: 8,
        fontSize: 12,
        fontWeight: 700,
        background: C?.navy || "#28394A",
        color: "#fff",
        border: "none",
        cursor: "pointer"
      }
    }, "Valider & Suivant ➔")))), showUnpointedModal && /*#__PURE__*/React.createElement("div", {
      style: {
        position: "fixed",
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        background: "rgba(0,0,0,0.45)",
        backdropFilter: "blur(3px)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        zIndex: 1000,
        padding: 20
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 14,
        width: "100%",
        maxWidth: 760,
        maxHeight: "85vh",
        display: "flex",
        flexDirection: "column",
        boxShadow: "0 10px 30px rgba(0,0,0,0.2)",
        overflow: "hidden"
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        padding: "16px 20px",
        borderBottom: `1px solid ${C?.line || "#DED6C4"}`,
        background: C?.panelAlt || "#EFEAE0",
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center"
      }
    }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontWeight: 700,
        fontSize: 16,
        color: C?.navy || "#28394A",
        textTransform: "capitalize"
      }
    }, "🔍 Dépenses bancaires non pointées — ", monthLabel), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        marginTop: 2
      }
    }, monthBankSummary.unpointedCount, " mouvement(s) non associé(s) au budget (", eur(monthBankSummary.unpointedExpenses), " de débits)")), /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => setShowUnpointedModal(false),
      style: {
        background: "none",
        border: "none",
        fontSize: 20,
        cursor: "pointer",
        color: C?.inkSoft || "#6B7278"
      }
    }, "✕")), /*#__PURE__*/React.createElement("div", {
      style: {
        flex: 1,
        overflowY: "auto",
        padding: 16
      }
    }, monthTxs.filter(t => !pointedTxIds.has(t.id)).length === 0 ? /*#__PURE__*/React.createElement("div", {
      style: {
        textAlign: "center",
        padding: 30,
        color: C?.pine || "#2F5D50",
        fontWeight: 600
      }
    }, "🎉 Toutes les transactions de ce mois sont associées à votre budget !") : /*#__PURE__*/React.createElement("table", {
      style: {
        width: "100%",
        borderCollapse: "collapse",
        fontSize: 12.5
      }
    }, /*#__PURE__*/React.createElement("thead", null, /*#__PURE__*/React.createElement("tr", {
      style: {
        borderBottom: `2px solid ${C?.line || "#DED6C4"}`,
        background: C?.panelAlt || "#EFEAE0"
      }
    }, /*#__PURE__*/React.createElement("th", {
      style: {
        padding: "8px 10px",
        textAlign: "left",
        fontSize: 11,
        color: C?.inkSoft || "#6B7278"
      }
    }, "Date"), /*#__PURE__*/React.createElement("th", {
      style: {
        padding: "8px 10px",
        textAlign: "left",
        fontSize: 11,
        color: C?.inkSoft || "#6B7278"
      }
    }, "Catégorie"), /*#__PURE__*/React.createElement("th", {
      style: {
        padding: "8px 10px",
        textAlign: "left",
        fontSize: 11,
        color: C?.inkSoft || "#6B7278"
      }
    }, "Libellé Relevé Bancaire"), /*#__PURE__*/React.createElement("th", {
      style: {
        padding: "8px 10px",
        textAlign: "right",
        fontSize: 11,
        color: C?.inkSoft || "#6B7278"
      }
    }, "Montant (€)"))), /*#__PURE__*/React.createElement("tbody", null, monthTxs.filter(t => !pointedTxIds.has(t.id)).sort((a, b) => a.date < b.date ? -1 : 1).map(tx => {
      const cat = categories.find(c => c.id === tx.categoryId);
      return /*#__PURE__*/React.createElement("tr", {
        key: tx.id,
        style: {
          borderBottom: `1px solid ${C?.line || "#DED6C4"}`
        }
      }, /*#__PURE__*/React.createElement("td", {
        style: {
          padding: "9px 10px",
          fontFamily: "'IBM Plex Mono', monospace",
          fontSize: 11.5,
          color: C?.inkSoft || "#6B7278",
          whiteSpace: "nowrap"
        }
      }, tx.date), /*#__PURE__*/React.createElement("td", {
        style: {
          padding: "9px 10px",
          fontSize: 12,
          color: C?.inkSoft || "#6B7278",
          whiteSpace: "nowrap"
        }
      }, cat?.label || "—"), /*#__PURE__*/React.createElement("td", {
        style: {
          padding: "9px 10px",
          fontWeight: 600,
          color: C?.ink || "#232A2E"
        },
        title: tx.label
      }, tx.label), /*#__PURE__*/React.createElement("td", {
        style: {
          padding: "9px 10px",
          textAlign: "right",
          fontFamily: "'IBM Plex Mono', monospace",
          fontWeight: 700,
          color: tx.amount < 0 ? C?.brick || "#A8503C" : C?.pine || "#2F5D50",
          whiteSpace: "nowrap"
        }
      }, eurExact(tx.amount)));
    })))), /*#__PURE__*/React.createElement("div", {
      style: {
        padding: "12px 20px",
        borderTop: `1px solid ${C?.line || "#DED6C4"}`,
        background: C?.panelAlt || "#EFEAE0",
        display: "flex",
        justifyContent: "flex-end"
      }
    }, /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => setShowUnpointedModal(false),
      style: {
        padding: "8px 18px",
        borderRadius: 8,
        fontSize: 12.5,
        fontWeight: 700,
        background: C?.navy || "#28394A",
        color: "#fff",
        border: "none",
        cursor: "pointer"
      }
    }, "✓ Fermer")))));
  }
  exports.PointageView = PointageView;
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);