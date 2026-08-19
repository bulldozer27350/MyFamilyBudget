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
          if (col.filterValue) return String(col.filterValue(r)).toLowerCase() === term.toLowerCase();
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
  function ImportBankView({
    openHelp
  }) {
    const [bankImportData, setBankImportData] = useState(null);
    const [rawRows, setRawRows] = useState(null);
    const [colRoles, setColRoles] = useState(null);
    const [fileName, setFileName] = useState("");
    const [importSummary, setImportSummary] = useState(null);
    const [showIgnoredModal, setShowIgnoredModal] = useState(false);
    
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
        label: "Catégorie",
        align: "left",
        value: t => categories.find(c => c.id === t.categoryId)?.label || "",
        filterOptions: [{
          value: "__none__",
          label: "Non catégorisé"
        }, ...sortedCategories.map(c => ({
          value: c.label,
          label: c.label
        }))],
        filterValue: t => t.categoryId ? categories.find(c => c.id === t.categoryId)?.label || "" : "__none__"
      }],
      renderRow: t => /*#__PURE__*/React.createElement("tr", {
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
      }, t.label), /*#__PURE__*/React.createElement("td", {
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
      }, /*#__PURE__*/React.createElement("select", {
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
      }, categoryOptions.map(o => /*#__PURE__*/React.createElement("option", {
        key: o.value,
        value: o.value
      }, o.label)))))
    })));
  }
  exports.ImportBankView = ImportBankView;
  exports.SortFilterTable = SortFilterTable;
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);