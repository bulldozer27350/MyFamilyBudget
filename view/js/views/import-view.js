/**
 * Vue Import (ImportBankView : Import CSV bancaire, Mapping, Déduplication, Catégorisation & Règles)
 */
(function (exports) {
  'use strict';

  const {
    useState,
    useMemo,
    useEffect
  } = React;
  const {
    C,
    eurExact,
    uid
  } = exports.C ? exports : window.BudgetApp || {};
  const {
    parseCSVText,
    parseDateWithFormat,
    parseAmountText,
    transactionDedupeKey,
    ruleKeyFromLabel,
    applyRulesToTransactions
  } = exports.parseCSVText ? exports : window.BudgetApp || {};
  const {
    BudgetApi
  } = exports.BudgetApi ? exports : window.BudgetApp || {};
  const {
    SectionCard,
    EditableTable
  } = exports.SectionCard ? exports : window.BudgetApp || {};
  const DATE_FORMAT_OPTIONS = ["DD/MM/YYYY", "YYYY-MM-DD", "MM/DD/YYYY", "DD-MM-YYYY", "DD.MM.YYYY"];
  const DELIMITER_OPTIONS = [{
    value: ";",
    label: "Point-virgule ( ; )"
  }, {
    value: ",",
    label: "Virgule ( , )"
  }, {
    value: "\t",
    label: "Tabulation"
  }];
  const inputStyle = {
    border: `1px solid ${C?.line || "#DED6C4"}`,
    borderRadius: 7,
    padding: "8px 10px",
    fontSize: 14,
    width: 140
  };
  const btnSmStyle = {
    fontSize: 11,
    padding: "3px 8px",
    borderRadius: 5,
    border: `1px solid ${C?.line || "#DED6C4"}`,
    background: C?.panelAlt || "#EFEAE0",
    color: C?.ink || "#232A2E",
    cursor: "pointer"
  };

  /**
   * Tableau générique triable et filtrable
   */
  function SortFilterTable({
    rows,
    columns,
    emptyLabel,
    renderRow,
    maxHeight = 480
  }) {
    const [sortConfig, setSortConfig] = useState({
      key: null,
      dir: "desc"
    });
    const [filters, setFilters] = useState({});
    const activeFilterCount = Object.values(filters).filter(v => v && String(v).trim() !== "").length;
    const filteredSorted = useMemo(() => {
      let result = rows || [];
      if (activeFilterCount > 0) {
        result = result.filter(r => columns.every(col => {
          const term = (filters[col.key] || "").trim().toLowerCase();
          if (!term) return true;
          if (col.filterValue) {
            const fv = col.filterValue(r);
            if (Array.isArray(fv)) {
              return fv.map(v => String(v).toLowerCase()).includes(term.toLowerCase());
            }
            return String(fv).toLowerCase() === term.toLowerCase();
          }
          return String(col.display ? col.display(r) : col.value(r)).toLowerCase().includes(term);
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
    }, [rows, columns, filters, activeFilterCount, sortConfig]);
    const handleSort = key => setSortConfig(prev => prev.key === key ? {
      key,
      dir: prev.dir === "asc" ? "desc" : "asc"
    } : {
      key,
      dir: "asc"
    });
    return /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 14,
        flexWrap: "wrap",
        marginBottom: 10,
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        alignItems: "center"
      }
    }, /*#__PURE__*/React.createElement("span", null, /*#__PURE__*/React.createElement("strong", {
      style: {
        color: C?.ink || "#232A2E"
      }
    }, filteredSorted.length), activeFilterCount > 0 ? ` sur ${(rows || []).length}` : ""), activeFilterCount > 0 && /*#__PURE__*/React.createElement("button", {
      onClick: () => setFilters({}),
      style: btnSmStyle
    }, "✕ Réinitialiser les filtres")), (rows || []).length === 0 ? /*#__PURE__*/React.createElement("div", {
      style: {
        color: C?.inkSoft || "#6B7278",
        fontSize: 12.5
      }
    }, emptyLabel) : /*#__PURE__*/React.createElement("div", {
      style: {
        maxHeight,
        overflow: "auto",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 8
      }
    }, /*#__PURE__*/React.createElement("table", {
      style: {
        width: "100%",
        borderCollapse: "collapse",
        fontSize: 12,
        minWidth: 760
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
    }, col.filterOptions ? /*#__PURE__*/React.createElement("select", {
      value: filters[col.key] || "",
      onChange: e => setFilters(f => ({
        ...f,
        [col.key]: e.target.value
      })),
      style: {
        width: "100%",
        fontSize: 11,
        padding: "3px 4px",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 4,
        background: C?.panel || "#FFFFFF"
      }
    }, /*#__PURE__*/React.createElement("option", {
      value: ""
    }, "Tous"), col.filterOptions.map(o => /*#__PURE__*/React.createElement("option", {
      key: o.value,
      value: o.value
    }, o.label))) : /*#__PURE__*/React.createElement("input", {
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
    }))))), /*#__PURE__*/React.createElement("tbody", null, filteredSorted.length === 0 ? /*#__PURE__*/React.createElement("tr", null, /*#__PURE__*/React.createElement("td", {
      colSpan: columns.length,
      style: {
        padding: "16px 7px",
        textAlign: "center",
        color: C?.inkSoft || "#6B7278"
      }
    }, "Aucune ligne ne correspond aux filtres.")) : filteredSorted.map(r => renderRow(r))))));
  }

  /**
   * Modale de ventilation des dépenses (splits)
   */
  function SplitModal({
    transaction,
    categories,
    onSave,
    onClose
  }) {
    const targetTotal = Math.abs(Number(transaction?.amount) || 0);
    const isExpense = (Number(transaction?.amount) || 0) < 0;

    const [splits, setSplits] = useState(() => {
      if (transaction?.splits && transaction.splits.length > 0) {
        return transaction.splits.map(s => ({
          id: s.id || uid(),
          categoryId: s.categoryId || "",
          amount: Math.abs(Number(s.amount) || 0),
          label: s.label || ""
        }));
      }
      const half = Math.round((targetTotal / 2) * 100) / 100;
      const remainder = Math.round((targetTotal - half) * 100) / 100;
      return [
        { id: uid(), categoryId: transaction?.categoryId || categories[0]?.id || "", amount: half, label: "" },
        { id: uid(), categoryId: categories[1]?.id || categories[0]?.id || "", amount: remainder, label: "" }
      ];
    });

    const [errorMsg, setErrorMsg] = useState("");

    const currentSum = useMemo(() => {
      return splits.reduce((sum, s) => sum + (Number(s.amount) || 0), 0);
    }, [splits]);

    const reliquat = Math.round((targetTotal - currentSum) * 100) / 100;
    const isBalanced = Math.abs(reliquat) < 0.005;

    const updateSplit = (id, field, value) => {
      setSplits(prev => prev.map(s => {
        if (s.id !== id) return s;
        if (field === "amount") {
          const num = parseFloat(String(value).replace(",", "."));
          return { ...s, amount: isNaN(num) ? "" : num };
        }
        return { ...s, [field]: value };
      }));
    };

    const addLine = () => {
      const nextAmount = reliquat > 0 ? reliquat : 0;
      setSplits(prev => [
        ...prev,
        { id: uid(), categoryId: categories[0]?.id || "", amount: nextAmount, label: "" }
      ]);
    };

    const removeLine = id => {
      if (splits.length <= 1) return;
      setSplits(prev => prev.filter(s => s.id !== id));
    };

    const fillRemaining = id => {
      const otherSum = splits.filter(s => s.id !== id).reduce((sum, s) => sum + (Number(s.amount) || 0), 0);
      const rest = Math.max(0, Math.round((targetTotal - otherSum) * 100) / 100);
      updateSplit(id, "amount", rest);
    };

    const handleSave = () => {
      if (!isBalanced) {
        setErrorMsg(`La ventilation n'est pas équilibrée. Il reste ${eurExact(reliquat)} à ventiler.`);
        return;
      }
      if (splits.some(s => !s.categoryId)) {
        setErrorMsg("Veuillez sélectionner une catégorie pour chaque sous-ligne.");
        return;
      }
      if (splits.some(s => (Number(s.amount) || 0) <= 0)) {
        setErrorMsg("Chaque sous-ligne doit avoir un montant strictement positif.");
        return;
      }

      const finalSplits = splits.map(s => {
        const positiveAmt = Number(s.amount) || 0;
        const signedAmt = isExpense ? -Math.abs(positiveAmt) : Math.abs(positiveAmt);
        return {
          id: s.id,
          categoryId: s.categoryId,
          amount: signedAmt,
          label: (s.label || "").trim()
        };
      });

      onSave(transaction.id, finalSplits);
    };

    const handleClearSplits = () => {
      if (window.confirm("Voulez-vous supprimer cette ventilation et revenir à une catégorisation simple ?")) {
        onSave(transaction.id, []);
      }
    };

    return /*#__PURE__*/React.createElement("div", {
      style: {
        position: "fixed",
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        background: "rgba(0,0,0,0.5)",
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
        maxWidth: 740,
        maxHeight: "90vh",
        display: "flex",
        flexDirection: "column",
        boxShadow: "0 12px 36px rgba(0,0,0,0.25)",
        overflow: "hidden"
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        padding: "16px 20px",
        borderBottom: `1px solid ${C?.line || "#DED6C4"}`,
        background: C?.panelAlt || "#EFEAE0",
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-start"
      }
    }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontWeight: 700,
        fontSize: 17,
        color: C?.navy || "#28394A",
        display: "flex",
        alignItems: "center",
        gap: 8
      }
    }, /*#__PURE__*/React.createElement("span", null, "✂ Ventiler une transaction")), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 13,
        color: C?.ink || "#232A2E",
        marginTop: 4,
        fontWeight: 500
      }
    }, /*#__PURE__*/React.createElement("span", {
      style: {
        fontFamily: "'IBM Plex Mono', monospace",
        color: C?.inkSoft || "#6B7278",
        marginRight: 8
      }
    }, transaction.date ? transaction.date.split("-").reverse().join("/") : ""), /*#__PURE__*/React.createElement("strong", {
      style: {
        marginRight: 10
      }
    }, transaction.label), /*#__PURE__*/React.createElement("span", {
      style: {
        fontFamily: "'IBM Plex Mono', monospace",
        fontWeight: 700,
        color: isExpense ? C?.brick || "#A8503C" : C?.pine || "#2F5D50"
      }
    }, eurExact(transaction.amount)))), /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: onClose,
      style: {
        background: "none",
        border: "none",
        fontSize: 22,
        cursor: "pointer",
        color: C?.inkSoft || "#6B7278",
        lineHeight: 1
      }
    }, "✕")), /*#__PURE__*/React.createElement("div", {
      style: {
        flex: 1,
        overflowY: "auto",
        padding: 20
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12.5,
        color: C?.inkSoft || "#6B7278",
        marginBottom: 16,
        lineHeight: 1.4
      }
    }, "Répartissez le montant de ", /*#__PURE__*/React.createElement("strong", null, eurExact(targetTotal)), " sur plusieurs catégories (ex : ticket de supermarché partagé entre Alimentation, Habillement et Entretien)."), /*#__PURE__*/React.createElement("div", {
      style: {
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 8,
        overflow: "hidden",
        marginBottom: 16
      }
    }, /*#__PURE__*/React.createElement("table", {
      style: {
        width: "100%",
        borderCollapse: "collapse",
        fontSize: 12.5
      }
    }, /*#__PURE__*/React.createElement("thead", null, /*#__PURE__*/React.createElement("tr", {
      style: {
        background: C?.panelAlt || "#EFEAE0",
        borderBottom: `1px solid ${C?.line || "#DED6C4"}`
      }
    }, /*#__PURE__*/React.createElement("th", {
      style: {
        padding: "8px 10px",
        textAlign: "left",
        width: 150
      }
    }, "Montant (€)"), /*#__PURE__*/React.createElement("th", {
      style: {
        padding: "8px 10px",
        textAlign: "left",
        width: 220
      }
    }, "Catégorie"), /*#__PURE__*/React.createElement("th", {
      style: {
        padding: "8px 10px",
        textAlign: "left"
      }
    }, "Note / Sous-libellé (optionnel)"), /*#__PURE__*/React.createElement("th", {
      style: {
        padding: "8px 10px",
        textAlign: "center",
        width: 40
      }
    }))), /*#__PURE__*/React.createElement("tbody", null, splits.map((s, idx) => /*#__PURE__*/React.createElement("tr", {
      key: s.id || idx,
      style: {
        borderBottom: `1px solid ${C?.line || "#DED6C4"}`
      }
    }, /*#__PURE__*/React.createElement("td", {
      style: {
        padding: "8px 10px"
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        alignItems: "center",
        gap: 4
      }
    }, /*#__PURE__*/React.createElement("input", {
      type: "number",
      step: "0.01",
      min: "0",
      value: s.amount !== undefined ? s.amount : "",
      onChange: e => updateSplit(s.id, "amount", e.target.value),
      placeholder: "0.00",
      style: {
        fontFamily: "'IBM Plex Mono', monospace",
        fontSize: 12.5,
        fontWeight: 600,
        padding: "5px 8px",
        borderRadius: 5,
        border: `1px solid ${C?.line || "#DED6C4"}`,
        width: 80,
        textAlign: "right",
        boxSizing: "border-box"
      }
    }), /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278"
      }
    }, "€"), splits.length > 1 && !isBalanced && reliquat > 0 && /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => fillRemaining(s.id),
      title: "Ajuster avec le reste à ventiler",
      style: {
        fontSize: 10,
        padding: "2px 5px",
        borderRadius: 4,
        border: `1px solid ${C?.pine || "#2F5D50"}`,
        background: C?.pineSoft || "#E3ECE8",
        color: C?.pine || "#2F5D50",
        cursor: "pointer",
        whiteSpace: "nowrap"
      }
    }, "🪄 Reste"))), /*#__PURE__*/React.createElement("td", {
      style: {
        padding: "8px 10px"
      }
    }, /*#__PURE__*/React.createElement("select", {
      value: s.categoryId || "",
      onChange: e => updateSplit(s.id, "categoryId", e.target.value),
      style: {
        fontSize: 12,
        padding: "5px 8px",
        borderRadius: 5,
        border: `1px solid ${C?.line || "#DED6C4"}`,
        width: "100%",
        background: C?.panel || "#FFFFFF",
        cursor: "pointer"
      }
    }, /*#__PURE__*/React.createElement("option", {
      value: ""
    }, "— Choisir une catégorie —"), categories.map(c => /*#__PURE__*/React.createElement("option", {
      key: c.id,
      value: c.id
    }, c.label)))), /*#__PURE__*/React.createElement("td", {
      style: {
        padding: "8px 10px"
      }
    }, /*#__PURE__*/React.createElement("input", {
      type: "text",
      value: s.label || "",
      onChange: e => updateSplit(s.id, "label", e.target.value),
      placeholder: `Note sous-ligne ${idx + 1}`,
      style: {
        fontSize: 12,
        padding: "5px 8px",
        borderRadius: 5,
        border: `1px solid ${C?.line || "#DED6C4"}`,
        width: "100%",
        boxSizing: "border-box"
      }
    })), /*#__PURE__*/React.createElement("td", {
      style: {
        padding: "8px 10px",
        textAlign: "center"
      }
    }, splits.length > 1 && /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => removeLine(s.id),
      style: {
        background: "none",
        border: "none",
        color: C?.brick || "#A8503C",
        cursor: "pointer",
        fontSize: 14,
        fontWeight: 700,
        padding: "2px 6px"
      },
      title: "Supprimer cette sous-ligne"
    }, "✕"))))))), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        marginBottom: 16
      }
    }, /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: addLine,
      style: {
        ...btnSmStyle,
        padding: "6px 12px",
        fontSize: 12,
        fontWeight: 600,
        color: C?.pine || "#2F5D50",
        background: C?.pineSoft || "#E3ECE8",
        borderColor: C?.pine || "#2F5D50"
      }
    }, "➕ Ajouter une sous-ligne")), /*#__PURE__*/React.createElement("div", {
      style: {
        padding: "12px 16px",
        borderRadius: 8,
        border: `1px solid ${isBalanced ? C?.pine || "#2F5D50" : C?.brick || "#A8503C"}`,
        background: isBalanced ? C?.pineSoft || "#E3ECE8" : C?.brickSoft || "#F4E4DF",
        color: isBalanced ? C?.pine || "#2F5D50" : C?.brick || "#A8503C",
        fontSize: 13,
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        flexWrap: "wrap",
        gap: 10
      }
    }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("strong", null, "Total ventilé : "), /*#__PURE__*/React.createElement("span", {
      style: {
        fontFamily: "'IBM Plex Mono', monospace",
        fontWeight: 700
      }
    }, eurExact(currentSum)), /*#__PURE__*/React.createElement("span", {
      style: {
        margin: "0 8px",
        color: C?.inkSoft || "#6B7278"
      }
    }, "/"), /*#__PURE__*/React.createElement("span", null, "Montant attendu : "), /*#__PURE__*/React.createElement("span", {
      style: {
        fontFamily: "'IBM Plex Mono', monospace",
        fontWeight: 700
      }
    }, eurExact(targetTotal))), /*#__PURE__*/React.createElement("div", null, isBalanced ? /*#__PURE__*/React.createElement("span", {
      style: {
        fontWeight: 700
      }
    }, "✓ Ventilation équilibrée (0,00 € restant)") : /*#__PURE__*/React.createElement("span", {
      style: {
        fontWeight: 700
      }
    }, `⚠️ Reliquat à ventiler : ${eurExact(reliquat)}`))), errorMsg && /*#__PURE__*/React.createElement("div", {
      style: {
        color: C?.brick || "#A8503C",
        fontSize: 12,
        marginTop: 8,
        fontWeight: 600
      }
    }, errorMsg)), /*#__PURE__*/React.createElement("div", {
      style: {
        padding: "14px 20px",
        borderTop: `1px solid ${C?.line || "#DED6C4"}`,
        background: C?.panelAlt || "#EFEAE0",
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        flexWrap: "wrap",
        gap: 10
      }
    }, /*#__PURE__*/React.createElement("div", null, transaction.splits && transaction.splits.length > 0 && /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: handleClearSplits,
      style: {
        ...btnSmStyle,
        color: C?.brick || "#A8503C",
        borderColor: C?.brick || "#A8503C",
        background: "#fff",
        padding: "7px 12px",
        fontSize: 12
      }
    }, "🗑 Supprimer la ventilation")), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 10
      }
    }, /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: onClose,
      style: {
        ...btnSmStyle,
        padding: "8px 16px",
        fontSize: 12.5
      }
    }, "Annuler"), /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: handleSave,
      disabled: !isBalanced || splits.length < 1,
      style: {
        padding: "8px 18px",
        borderRadius: 8,
        fontSize: 12.5,
        fontWeight: 700,
        background: isBalanced ? C?.pine || "#2F5D50" : C?.inkSoft || "#6B7278",
        color: "#fff",
        border: "none",
        cursor: isBalanced ? "pointer" : "not-allowed",
        opacity: isBalanced ? 1 : 0.6
      }
    }, "✓ Enregistrer la ventilation")))));
  }

  function ImportBankView({
    openHelp
  }) {
    const [bankImportData, setBankImportData] = useState(null);
    const [rawRows, setRawRows] = useState(null);
    const [colRoles, setColRoles] = useState(null);
    const [fileName, setFileName] = useState("");
    const [importSummary, setImportSummary] = useState(null);
    const [showIgnoredModal, setShowIgnoredModal] = useState(false);
    const [splitModalTx, setSplitModalTx] = useState(null);
    
    // Charger les données d'import bancaire via l'API asynchrone
    useEffect(() => {
      const loadBankImportData = async () => {
        try {
          const result = await BudgetApi.getBankImport();
          setBankImportData(result);
        } catch (error) {
          console.error("Erreur lors du chargement des données d'import:", error);
        }
      };
      loadBankImportData();
      
      // S'abonner aux changements
      const unsubscribe = BudgetApi.onBankImportChanged(() => {
        loadBankImportData();
      });
      
      return unsubscribe;
    }, []);

    const mapping = bankImportData?.columnMapping || {};
    const categories = bankImportData?.categories || [];
    const rules = bankImportData?.rules || [];
    const transactions = bankImportData?.transactions || [];
    
    const updateMapping = fn => {
      const newMapping = fn(mapping);
      BudgetApi.updateBankImportMapping(newMapping).then(() => {
        setBankImportData(prev => prev ? { ...prev, columnMapping: newMapping } : null);
      });
    };
    const sortedCategories = useMemo(() => [...categories].sort((a, b) => (a.label || "").localeCompare(b.label || "", "fr")), [categories]);
    const categoryOptions = [{
      value: "",
      label: "— Non catégorisé —"
    }, ...sortedCategories.map(c => ({
      value: c.id,
      label: c.label
    }))];
    const handleFile = file => {
      if (!file) return;
      setFileName(file.name);
      setImportSummary(null);
      const reader = new FileReader();
      reader.onload = e => {
        const rows = parseCSVText(String(e.target.result), mapping.delimiter);
        const dataRows = mapping.hasHeader ? rows.slice(1) : rows;
        setRawRows(dataRows);
        const nCols = dataRows[0] ? dataRows[0].length : 0;
        const roles = [];
        for (let i = 0; i < nCols; i++) {
          if (i === mapping.dateCol) roles.push("date");else if (i === mapping.labelCol) roles.push("label");else if (i === mapping.typeCol) roles.push("type");else if (i === mapping.amountCol) roles.push("amount");else roles.push("ignore");
        }
        if (!roles.includes("date") && !roles.includes("label") && !roles.includes("amount")) {
          if (nCols > 0) roles[0] = "date";
          if (nCols > 1) roles[1] = "label";
          if (nCols > 2) roles[2] = "amount";
        }
        setColRoles(roles);
      };
      reader.readAsText(file, "UTF-8");
    };
    const setRole = (colIdx, role) => {
      setColRoles(prev => prev.map((r, i) => {
        if (i === colIdx) return role;
        return role !== "ignore" && r === role ? "ignore" : r;
      }));
    };
    const doImport = async () => {
      if (!rawRows || !colRoles) return;
      try {
        const result = await BudgetApi.importBankTransactions(rawRows, colRoles, mapping);
        setImportSummary(result);
        if (!result.error) {
          setRawRows(null);
          setColRoles(null);
          setFileName("");
          // Recharger les données après l'import
          const updatedData = await BudgetApi.getBankImport();
          setBankImportData(updatedData);
        }
      } catch (error) {
        console.error("Erreur lors de l'import:", error);
        setImportSummary({
          error: "Erreur lors de l'import : " + error.message
        });
      }
    };
    const forceImportDuplicate = async tx => {
      try {
        await BudgetApi.forceImportBankTransaction(tx);
        setImportSummary(prev => prev ? {
          ...prev,
          imported: prev.imported + 1,
          duplicates: Math.max(0, prev.duplicates - 1),
          ignoredDuplicates: (prev.ignoredDuplicates || []).filter(t => t.id !== tx.id)
        } : null);
        // Recharger les données après l'import forcé
        const updatedData = await BudgetApi.getBankImport();
        setBankImportData(updatedData);
      } catch (error) {
        console.error("Erreur lors de l'import forcé:", error);
      }
    };
    const roleLabel = {
      date: "Date",
      label: "Libellé",
      type: "Type",
      amount: "Montant",
      ignore: "Ignorer"
    };
    const recalculateRules = async () => {
      try {
        await BudgetApi.recalculateBankImportRules();
        const updatedData = await BudgetApi.getBankImport();
        setBankImportData(updatedData);
      } catch (error) {
        console.error("Erreur lors du recalcul des règles:", error);
      }
    };
    const setTransactionCategory = async (txId, categoryId, ruleKeyword) => {
      try {
        await BudgetApi.setBankImportTransactionCategory(txId, categoryId, ruleKeyword);
        const updatedData = await BudgetApi.getBankImport();
        setBankImportData(updatedData);
      } catch (error) {
        console.error("Erreur lors de la mise à jour de la catégorie:", error);
      }
    };
    const uncategorizedCount = transactions.filter(t => !t.categoryId).length;
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
    }, "Import des Relevés Bancaires"), openHelp && /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => openHelp("import"),
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
      title: "Aide sur l'import"
    }, "?")), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12.5,
        color: C?.inkSoft || "#6B7278",
        marginTop: 3
      }
    }, "Importez vos fichiers CSV bancaires, configurez les règles d'auto-catégorisation et gérez vos catégories."))), /*#__PURE__*/React.createElement(SectionCard, {
      title: "Format du fichier & import",
      subtitle: "Configurez une fois le format de votre banque — réutilisé automatiquement aux imports suivants."
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 24,
        flexWrap: "wrap",
        marginBottom: 16
      }
    }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        marginBottom: 6
      }
    }, "Séparateur de colonnes"), /*#__PURE__*/React.createElement("select", {
      value: mapping.delimiter,
      onChange: e => updateMapping(m => ({
        ...m,
        delimiter: e.target.value
      })),
      style: {
        ...inputStyle,
        width: 190,
        cursor: "pointer"
      }
    }, DELIMITER_OPTIONS.map(o => /*#__PURE__*/React.createElement("option", {
      key: o.value,
      value: o.value
    }, o.label)))), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        marginBottom: 6
      }
    }, "Format de date"), /*#__PURE__*/React.createElement("select", {
      value: mapping.dateFormat,
      onChange: e => updateMapping(m => ({
        ...m,
        dateFormat: e.target.value
      })),
      style: {
        ...inputStyle,
        width: 160,
        cursor: "pointer"
      }
    }, DATE_FORMAT_OPTIONS.map(f => /*#__PURE__*/React.createElement("option", {
      key: f,
      value: f
    }, f)))), /*#__PURE__*/React.createElement("label", {
      style: {
        display: "flex",
        alignItems: "center",
        gap: 8,
        cursor: "pointer",
        marginTop: 20
      }
    }, /*#__PURE__*/React.createElement("input", {
      type: "checkbox",
      checked: !!mapping.hasHeader,
      onChange: e => updateMapping(m => ({
        ...m,
        hasHeader: e.target.checked
      }))
    }), /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 12.5,
        color: C?.ink || "#232A2E"
      }
    }, "La 1ère ligne du fichier est un en-tête (à ignorer)"))), /*#__PURE__*/React.createElement("div", {
      style: {
        marginBottom: 14
      }
    }, /*#__PURE__*/React.createElement("label", {
      style: {
        display: "inline-flex",
        alignItems: "center",
        gap: 8,
        padding: "9px 16px",
        borderRadius: 8,
        border: `1px solid ${C?.line || "#DED6C4"}`,
        cursor: "pointer",
        fontSize: 13,
        fontWeight: 600,
        color: C?.pine || "#2F5D50",
        background: C?.pineSoft || "#E3ECE8"
      }
    }, "📄 Choisir un fichier CSV…", /*#__PURE__*/React.createElement("input", {
      type: "file",
      accept: ".csv,.txt",
      style: {
        display: "none"
      },
      onChange: e => handleFile(e.target.files[0])
    })), fileName && /*#__PURE__*/React.createElement("span", {
      style: {
        marginLeft: 12,
        fontSize: 12.5,
        color: C?.inkSoft || "#6B7278"
      }
    }, fileName)), rawRows && colRoles && /*#__PURE__*/React.createElement("div", {
      style: {
        marginBottom: 14
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12.5,
        color: C?.inkSoft || "#6B7278",
        marginBottom: 8
      }
    }, "Assignez le rôle de chaque colonne détectée (aperçu des 5 premières lignes), puis validez l'import."), /*#__PURE__*/React.createElement("div", {
      style: {
        overflowX: "auto",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 8
      }
    }, /*#__PURE__*/React.createElement("table", {
      style: {
        width: "100%",
        borderCollapse: "collapse",
        fontSize: 12,
        minWidth: 600
      }
    }, /*#__PURE__*/React.createElement("thead", null, /*#__PURE__*/React.createElement("tr", null, colRoles.map((role, i) => /*#__PURE__*/React.createElement("th", {
      key: i,
      style: {
        padding: "6px 8px",
        borderBottom: `1px solid ${C?.line || "#DED6C4"}`,
        background: C?.panelAlt || "#EFEAE0"
      }
    }, /*#__PURE__*/React.createElement("select", {
      value: role,
      onChange: e => setRole(i, e.target.value),
      style: {
        fontSize: 11.5,
        padding: "3px 4px",
        borderRadius: 4,
        border: `1px solid ${C?.line || "#DED6C4"}`,
        cursor: "pointer",
        width: "100%"
      }
    }, Object.keys(roleLabel).map(r => /*#__PURE__*/React.createElement("option", {
      key: r,
      value: r
    }, roleLabel[r]))))))), /*#__PURE__*/React.createElement("tbody", null, rawRows.slice(0, 5).map((row, ri) => /*#__PURE__*/React.createElement("tr", {
      key: ri,
      style: {
        borderBottom: `1px solid ${C?.line || "#DED6C4"}`
      }
    }, colRoles.map((role, ci) => /*#__PURE__*/React.createElement("td", {
      key: ci,
      style: {
        padding: "6px 8px",
        color: role === "ignore" ? C?.inkSoft || "#6B7278" : C?.ink || "#232A2E",
        fontStyle: role === "ignore" ? "italic" : "normal"
      }
    }, row[ci]))))))), /*#__PURE__*/React.createElement("div", {
      style: {
        marginTop: 10,
        display: "flex",
        gap: 10,
        alignItems: "center"
      }
    }, /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: doImport,
      style: {
        ...btnSmStyle,
        background: C?.pine || "#2F5D50",
        color: "#fff",
        fontWeight: 600,
        padding: "8px 16px"
      }
    }, "✓ Importer ", rawRows.length, " lignes"), /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => {
        setRawRows(null);
        setColRoles(null);
        setFileName("");
      },
      style: btnSmStyle
    }, "Annuler"))), importSummary && /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12.5,
        padding: "11px 16px",
        borderRadius: 8,
        background: importSummary.error ? C?.brickSoft || "#F4E4DF" : C?.pineSoft || "#E3ECE8",
        color: importSummary.error ? C?.brick || "#A8503C" : C?.pine || "#2F5D50",
        border: `1px solid ${importSummary.error ? C?.brick || "#A8503C" : C?.pine || "#2F5D50"}`,
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        flexWrap: "wrap",
        gap: 10
      }
    }, /*#__PURE__*/React.createElement("div", null, importSummary.error || `${importSummary.imported} transaction(s) importée(s), ${importSummary.duplicates} doublon(s) déjà présent(s) ignoré(s), ${importSummary.autoCategorized} catégorisée(s) automatiquement.`), importSummary.ignoredDuplicates && importSummary.ignoredDuplicates.length > 0 && /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => setShowIgnoredModal(true),
      style: {
        padding: "4px 10px",
        borderRadius: 6,
        fontSize: 11.5,
        fontWeight: 700,
        cursor: "pointer",
        background: C?.pine || "#2F5D50",
        color: "#fff",
        border: "none"
      }
    }, "🔍 Revoir les ", importSummary.duplicates, " doublon(s) ignoré(s)")), showIgnoredModal && importSummary?.ignoredDuplicates && /*#__PURE__*/React.createElement("div", {
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
        color: C?.navy || "#28394A"
      }
    }, "🔍 Transactions ignorées lors de l'import (Doublons potentiels)"), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        marginTop: 2
      }
    }, importSummary.ignoredDuplicates.length, " ligne(s) déjà présente(s).")), /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => setShowIgnoredModal(false),
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
    }, importSummary.ignoredDuplicates.length === 0 ? /*#__PURE__*/React.createElement("div", {
      style: {
        textAlign: "center",
        padding: 20,
        color: C?.pine || "#2F5D50",
        fontWeight: 600
      }
    }, "🎉 Toutes les transactions ont été intégrées !") : /*#__PURE__*/React.createElement("table", {
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
    }, "Libellé Relevé"), /*#__PURE__*/React.createElement("th", {
      style: {
        padding: "8px 10px",
        textAlign: "right",
        fontSize: 11,
        color: C?.inkSoft || "#6B7278"
      }
    }, "Montant (€)"), /*#__PURE__*/React.createElement("th", {
      style: {
        padding: "8px 10px",
        textAlign: "center",
        fontSize: 11,
        color: C?.inkSoft || "#6B7278"
      }
    }, "Action"))), /*#__PURE__*/React.createElement("tbody", null, importSummary.ignoredDuplicates.map(tx => /*#__PURE__*/React.createElement("tr", {
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
    }, eurExact(tx.amount)), /*#__PURE__*/React.createElement("td", {
      style: {
        padding: "9px 10px",
        textAlign: "center"
      }
    }, /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => forceImportDuplicate(tx),
      style: {
        padding: "4px 10px",
        borderRadius: 6,
        fontSize: 11.5,
        fontWeight: 700,
        cursor: "pointer",
        background: C?.pine || "#2F5D50",
        color: "#fff",
        border: "none"
      }
    }, "➕ Importer quand même"))))))), /*#__PURE__*/React.createElement("div", {
      style: {
        padding: "12px 20px",
        borderTop: `1px solid ${C?.line || "#DED6C4"}`,
        background: C?.panelAlt || "#EFEAE0",
        display: "flex",
        justifyContent: "flex-end"
      }
    }, /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => setShowIgnoredModal(false),
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
    }, "✓ Fermer"))))), /*#__PURE__*/React.createElement(SectionCard, {
      title: "Catégories",
      subtitle: "Liste libre — créez les catégories de dépenses ou revenus nécessaires."
    }, /*#__PURE__*/React.createElement(EditableTable, {
      columns: [{
        key: "label",
        label: "Catégorie",
        type: "text"
      }, {
        key: "kind",
        label: "Type",
        type: "select",
        options: ["Dépense", "Revenu", "Les deux"]
      }, {
        key: "compressible",
        label: "Poste réductible",
        type: "select",
        options: ["Non", "Oui"]
      }],
      rows: sortedCategories,
      onCell: (id, field, value) => {
        BudgetApi.updateBankImportCategory(id, field, value).then(() => {
          BudgetApi.getBankImport().then(setBankImportData);
        });
      },
      onRemove: (id) => {
        BudgetApi.removeBankImportCategory(id).then(() => {
          BudgetApi.getBankImport().then(setBankImportData);
        });
      },
      onAdd: () => {
        BudgetApi.addBankImportCategory({
          id: uid(),
          label: "Nouvelle catégorie",
          kind: "Dépense",
          compressible: "Non"
        }).then(() => {
          BudgetApi.getBankImport().then(setBankImportData);
        });
      }
    })), /*#__PURE__*/React.createElement(SectionCard, {
      title: "Règles de catégorisation",
      subtitle: "Un mot-clé s'applique dès qu'il apparaît n'importe où dans le libellé bancaire."
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        marginBottom: 10
      }
    }, /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: recalculateRules,
      style: btnSmStyle
    }, "↻ Réappliquer les règles aux transactions non catégorisées")), /*#__PURE__*/React.createElement(EditableTable, {
      columns: [{
        key: "matchText",
        label: "Mot-clé (libellé sans chiffres)",
        type: "text"
      }, {
        key: "categoryId",
        label: "Catégorie",
        type: "select",
        options: categoryOptions
      }],
      rows: rules,
      onCell: (id, field, value) => {
        BudgetApi.updateBankImportRule(id, field, value).then(() => {
          BudgetApi.getBankImport().then(setBankImportData);
        });
      },
      onRemove: (id) => {
        BudgetApi.removeBankImportRule(id).then(() => {
          BudgetApi.getBankImport().then(setBankImportData);
        });
      },
      onAdd: () => {
        BudgetApi.addBankImportRule({
          id: uid(),
          matchText: "",
          categoryId: categories[0]?.id || ""
        }).then(() => {
          BudgetApi.getBankImport().then(setBankImportData);
        });
      }
    })), /*#__PURE__*/React.createElement(SectionCard, {
      title: "Transactions",
      subtitle: `${transactions.length} transaction(s) importée(s)${uncategorizedCount ? ` — ${uncategorizedCount} non catégorisée(s)` : ""}.`
    }, /*#__PURE__*/React.createElement(SortFilterTable, {
      rows: transactions,
      emptyLabel: "Aucune transaction importée pour l'instant — utilisez le formulaire ci-dessus.",
      columns: [{
        key: "date",
        label: "Date",
        align: "left",
        numeric: false,
        value: t => t.date,
        display: t => t.date.split("-").reverse().join("/")
      }, {
        key: "label",
        label: "Libellé",
        align: "left",
        value: t => t.label
      }, {
        key: "amount",
        label: "Montant",
        align: "right",
        numeric: true,
        value: t => t.amount,
        display: t => eurExact(t.amount)
      }, {
        key: "categoryId",
        label: "Catégorie / Ventilation",
        align: "left",
        value: t => t.splits && t.splits.length > 0
          ? `Ventilée (${t.splits.length})`
          : (categories.find(c => c.id === t.categoryId)?.label || ""),
        filterOptions: [{
          value: "__none__",
          label: "Non catégorisé"
        }, {
          value: "__split__",
          label: "✂ Ventilées"
        }, ...sortedCategories.map(c => ({
          value: c.label,
          label: c.label
        }))],
        filterValue: t => {
          if (t.splits && t.splits.length > 0) {
            const splitCatLabels = t.splits.map(s => categories.find(c => c.id === s.categoryId)?.label).filter(Boolean);
            return ["__split__", ...splitCatLabels];
          }
          return t.categoryId ? categories.find(c => c.id === t.categoryId)?.label || "" : "__none__";
        }
      }],
      renderRow: t => {
        const hasSplits = Array.isArray(t.splits) && t.splits.length > 0;
        return /*#__PURE__*/React.createElement("tr", {
          key: t.id,
          style: {
            borderBottom: `1px solid ${C?.line || "#DED6C4"}`
          }
        }, /*#__PURE__*/React.createElement("td", {
          style: {
            padding: "7px",
            whiteSpace: "nowrap"
          }
        }, t.date.split("-").reverse().join("/")), /*#__PURE__*/React.createElement("td", {
          style: {
            padding: "7px"
          }
        }, /*#__PURE__*/React.createElement("div", null, t.label, hasSplits && /*#__PURE__*/React.createElement("div", {
          style: {
            fontSize: 11,
            color: C?.inkSoft || "#6B7278",
            marginTop: 4,
            paddingLeft: 8,
            borderLeft: `2px solid ${C?.pine || "#2F5D50"}`
          }
        }, t.splits.map((s, idx) => {
          const sCat = categories.find(c => c.id === s.categoryId);
          return /*#__PURE__*/React.createElement("div", {
            key: s.id || idx,
            style: {
              display: "flex",
              gap: 8,
              alignItems: "center"
            }
          }, /*#__PURE__*/React.createElement("span", {
            style: {
              fontWeight: 600
            }
          }, sCat?.label || "Non catégorisé"), s.label && /*#__PURE__*/React.createElement("span", {
            style: {
              fontStyle: "italic",
              color: C?.inkSoft || "#6B7278"
            }
          }, s.label), /*#__PURE__*/React.createElement("span", {
            style: {
              fontFamily: "'IBM Plex Mono', monospace"
            }
          }, eurExact(s.amount)));
        })))), /*#__PURE__*/React.createElement("td", {
          style: {
            padding: "7px",
            textAlign: "right",
            fontFamily: "'IBM Plex Mono', monospace",
            color: t.amount < 0 ? C?.brick || "#A8503C" : C?.pine || "#2F5D50"
          }
        }, eurExact(t.amount)), /*#__PURE__*/React.createElement("td", {
          style: {
            padding: "7px"
          }
        }, /*#__PURE__*/React.createElement("div", {
          style: {
            display: "flex",
            alignItems: "center",
            gap: 8,
            flexWrap: "wrap"
          }
        }, hasSplits ? /*#__PURE__*/React.createElement("span", {
          style: {
            fontSize: 12,
            fontWeight: 700,
            padding: "3px 8px",
            borderRadius: 6,
            background: C?.pineSoft || "#E3ECE8",
            color: C?.pine || "#2F5D50",
            border: `1px solid ${C?.pine || "#2F5D50"}`
          }
        }, `✂ Ventilée (${t.splits.length})`) : /*#__PURE__*/React.createElement("select", {
          value: t.categoryId || "",
          onChange: e => {
            let ruleKeyword = null;
            if (e.target.value) {
              const suggestion = ruleKeyFromLabel(t.label);
              const input = window.prompt(`Mot-clé à mémoriser pour classer automatiquement les prochaines transactions similaires dans cette catégorie. Laissez vide pour ne classer que cette transaction.`, suggestion);
              ruleKeyword = input && input.trim() ? input.trim() : null;
            }
            setTransactionCategory(t.id, e.target.value, ruleKeyword);
          },
          style: {
            fontSize: 12,
            padding: "4px 6px",
            borderRadius: 5,
            border: `1px solid ${C?.line || "#DED6C4"}`,
            cursor: "pointer",
            background: t.categoryId ? C?.panel || "#FFFFFF" : C?.goldSoft || "#F0EAD3"
          }
        }, categoryOptions.map(o => /*#__PURE__*/React.createElement("option", o.value === "" ? { key: o.value, value: o.value } : { key: o.value, value: o.value }, o.label))), /*#__PURE__*/React.createElement("button", {
          type: "button",
          onClick: () => setSplitModalTx(t),
          title: hasSplits ? "Modifier la ventilation" : "Ventiler cette dépense sur plusieurs catégories",
          style: {
            fontSize: 11.5,
            padding: "3px 8px",
            borderRadius: 5,
            border: `1px solid ${C?.line || "#DED6C4"}`,
            background: hasSplits ? C?.panelAlt || "#EFEAE0" : "#fff",
            color: C?.navy || "#28394A",
            cursor: "pointer",
            fontWeight: 600,
            whiteSpace: "nowrap"
          }
        }, hasSplits ? "✏ Modifier ventilation" : "✂ Ventiler"))));
      }
    })), splitModalTx && /*#__PURE__*/React.createElement(SplitModal, {
      transaction: splitModalTx,
      categories: sortedCategories,
      onSave: async (txId, splits) => {
        await BudgetApi.updateBankTransactionSplits(txId, splits);
        setSplitModalTx(null);
        const updated = await BudgetApi.getBankImport();
        setBankImportData(updated);
      },
      onClose: () => setSplitModalTx(null)
    }));
  }
  exports.ImportBankView = ImportBankView;
  exports.SortFilterTable = SortFilterTable;
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);