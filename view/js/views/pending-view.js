/**
 * Vue Opérations en cours (PendingOperationsView : Chèques émis, CB différées & Rapprochement bancaire)
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
    eur,
    eurExact,
    uid
  } = exports.C ? exports : window.BudgetApp || {};
  const {
    parseCSVText,
    parseDateWithFormat,
    parseAmountText,
    transactionDedupeKey,
    applyRulesToTransactions
  } = exports.parseCSVText ? exports : window.BudgetApp || {};
  const api = exports.BudgetApi ? exports.BudgetApi : window.BudgetApp?.BudgetApi;
  const OP_TYPES = {
    cheque: {
      label: "Chèque",
      icon: "🏷️",
      color: "#4338CA",
      bg: "#EEF2FF",
      border: "#C7D2FE"
    },
    cb: {
      label: "CB différée",
      icon: "💳",
      color: "#0284C7",
      bg: "#E0F2FE",
      border: "#BAE6FD"
    },
    virement: {
      label: "Virement",
      icon: "🔄",
      color: "#059669",
      bg: "#ECFDF5",
      border: "#A7F3D0"
    },
    prelevement: {
      label: "Prélèvement",
      icon: "📄",
      color: "#D97706",
      bg: "#FFFBEB",
      border: "#FDE68A"
    },
    autre: {
      label: "Autre",
      icon: "🔘",
      color: "#4B5563",
      bg: "#F3F4F6",
      border: "#E5E7EB"
    }
  };
  const inputStyle = {
    border: `1px solid ${C?.line || "#DED6C4"}`,
    borderRadius: 7,
    padding: "8px 10px",
    fontSize: 14,
    width: 140
  };
  function PendingOperationsView({
    data,
    update,
    setSection,
    openHelp
  }) {
    const today = new Date();
    const todayISO = today.toISOString().slice(0, 10);
    const [selYear, setSelYear] = useState(today.getFullYear());
    const [selMonth, setSelMonth] = useState(today.getMonth() + 1);
    const [filterView, setFilterView] = useState("month");
    const [filterType, setFilterType] = useState("all");
    const [searchText, setSearchText] = useState("");
    const [sortKey, setSortKey] = useState("date");
    const [sortDir, setSortDir] = useState(-1);
    const [showAddModal, setShowAddModal] = useState(false);
    const [editingOp, setEditingOp] = useState(null);
    const [modalPreset, setModalPreset] = useState("");
    const [modalData, setModalData] = useState({
      date: todayISO,
      expectedDate: "",
      type: "cheque",
      refNumber: "",
      label: "",
      amount: "",
      isDebit: true,
      categoryId: "",
      notes: "",
      splits: []
    });
    const [matchingOp, setMatchingOp] = useState(null);
    const [matchingSearch, setMatchingSearch] = useState("");
    const [toast, setToast] = useState(null);
    const [showReliquats, setShowReliquats] = useState(true);

    // États spécifiques à l'import des encours CB
    const [showImportModal, setShowImportModal] = useState(false);
    const [importFileName, setImportFileName] = useState("");
    const [importRawRows, setImportRawRows] = useState(null);
    const [importColRoles, setImportColRoles] = useState(null);
    const [importDelimiter, setImportDelimiter] = useState(";");
    const [importDateFormat, setImportDateFormat] = useState("DD-MM-YYYY");
    const [importHasHeader, setImportHasHeader] = useState(true);
    const [importUsePurchaseDate, setImportUsePurchaseDate] = useState(false);
    const [importSummary, setImportSummary] = useState(null);
    const [showIgnoredModal, setShowIgnoredModal] = useState(false);
    const [showDuplicateModal, setShowDuplicateModal] = useState(false);

    // État asynchrone issu de la façade API
    const [apiData, setApiData] = useState(null);
    const [loading, setLoading] = useState(!data);

    const getApi = () => exports.BudgetApi || window.BudgetApp?.BudgetApi || api;

    const loadDataFromApi = async () => {
      const apiInstance = getApi();
      if (apiInstance && apiInstance.getPendingOperations) {
        try {
          const res = await apiInstance.getPendingOperations();
          if (res) {
            setApiData(res);
          }
          setLoading(false);
          return res;
        } catch (err) {
          console.error("Erreur chargement opérations en cours via BudgetApi:", err);
          setLoading(false);
        }
      } else if (data) {
        setApiData({
          pendingOperations: data?.bankImport?.pendingOperations || [],
          transactions: data?.bankImport?.transactions || [],
          categories: data?.bankImport?.categories || [],
          rules: data?.bankImport?.rules || [],
          charges: data?.charges || [],
          incomes: data?.incomes || [],
          oneoff: data?.oneoff || [],
          settings: data?.settings || {}
        });
        setLoading(false);
      }
    };

    useEffect(() => {
      loadDataFromApi();
      const apiInstance = getApi();
      if (apiInstance && apiInstance.onPendingOperationsChanged) {
        const unsub = apiInstance.onPendingOperationsChanged(() => {
          loadDataFromApi();
        });
        return () => unsub && unsub();
      }
    }, [data]);

    const monthISO = `${selYear}-${String(selMonth).padStart(2, "0")}`;
    const monthLabel = new Date(selYear, selMonth - 1, 1).toLocaleDateString("fr-FR", {
      month: "long",
      year: "numeric"
    });
    const isCurrentMonth = selYear === today.getFullYear() && selMonth === today.getMonth() + 1;

    const sourceData = React.useMemo(() => {
      const storePending = Array.isArray(data?.bankImport?.pendingOperations) ? data.bankImport.pendingOperations : [];
      const apiPending = Array.isArray(apiData?.pendingOperations) ? apiData.pendingOperations : [];
      
      const pendingMap = new Map();
      apiPending.forEach(op => { if (op && op.id) pendingMap.set(op.id, op); });
      storePending.forEach(op => { if (op && op.id) pendingMap.set(op.id, op); });
      const mergedPending = Array.from(pendingMap.values());

      const storeTx = Array.isArray(data?.bankImport?.transactions) ? data.bankImport.transactions : [];
      const apiTx = Array.isArray(apiData?.transactions) ? apiData.transactions : [];
      const txMap = new Map();
      apiTx.forEach(t => { if (t && t.id) txMap.set(t.id, t); });
      storeTx.forEach(t => { if (t && t.id) txMap.set(t.id, t); });
      const mergedTx = Array.from(txMap.values());

      const cats = (Array.isArray(data?.bankImport?.categories) && data.bankImport.categories.length)
        ? data.bankImport.categories
        : (Array.isArray(apiData?.categories) ? apiData.categories : []);
      const rls = (Array.isArray(data?.bankImport?.rules) && data.bankImport.rules.length)
        ? data.bankImport.rules
        : (Array.isArray(apiData?.rules) ? apiData.rules : []);
      const chg = Array.isArray(data?.charges) ? data.charges : (Array.isArray(apiData?.charges) ? apiData.charges : []);
      const inc = Array.isArray(data?.incomes) ? data.incomes : (Array.isArray(apiData?.incomes) ? apiData.incomes : []);
      const one = Array.isArray(data?.oneoff) ? data.oneoff : (Array.isArray(apiData?.oneoff) ? apiData.oneoff : []);
      const set = data?.settings || apiData?.settings || {};

      return {
        pendingOperations: mergedPending,
        transactions: mergedTx,
        categories: cats,
        rules: rls,
        charges: chg,
        incomes: inc,
        oneoff: one,
        settings: set
      };
    }, [apiData, data]);

    const pendingOperations = sourceData.pendingOperations || [];
    const transactions = sourceData.transactions || [];
    const categories = sourceData.categories || [];
    const sortedCategories = useMemo(() => [...categories].sort((a, b) => (a.label || "").localeCompare(b.label || "", "fr")), [categories]);
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
    const goToCurrentMonth = () => {
      setSelYear(today.getFullYear());
      setSelMonth(today.getMonth() + 1);
    };
    const showToast = (message, actionLabel = null, actionFn = null) => {
      setToast({
        message,
        actionLabel,
        actionFn
      });
      setTimeout(() => setToast(null), 6000);
    };
    const startBalance = Number(sourceData?.settings?.startBalance) || 0;
    const cumulTxsMonth = useMemo(() => {
      return transactions.filter(t => t.date && t.date.slice(0, 7) <= monthISO).reduce((s, t) => s + (Number(t.amount) || 0), 0);
    }, [transactions, monthISO]);
    const soldeBanque = startBalance + cumulTxsMonth;
    const activePendingDebits = useMemo(() => {
      return pendingOperations.filter(op => op.status === "pending" && (Number(op.amount) || 0) < 0 && (!op.date || op.date.slice(0, 7) <= monthISO));
    }, [pendingOperations, monthISO]);
    const totalDebitsEnCours = activePendingDebits.reduce((s, op) => s + (Number(op.amount) || 0), 0);
    const activePendingCredits = useMemo(() => {
      return pendingOperations.filter(op => op.status === "pending" && (Number(op.amount) || 0) > 0 && (!op.date || op.date.slice(0, 7) <= monthISO));
    }, [pendingOperations, monthISO]);
    const totalCreditsEnAttente = activePendingCredits.reduce((s, op) => s + (Number(op.amount) || 0), 0);
    const soldeDisponibleReel = soldeBanque + totalDebitsEnCours + totalCreditsEnAttente;
    const reliquats = useMemo(() => {
      return pendingOperations.filter(op => op.status === "pending" && op.date && op.date.slice(0, 7) < monthISO);
    }, [pendingOperations, monthISO]);
    const reliquatsTotal = reliquats.reduce((s, op) => s + (Number(op.amount) || 0), 0);
    const monthOps = useMemo(() => {
      return pendingOperations.filter(op => op.date && op.date.slice(0, 7) === monthISO);
    }, [pendingOperations, monthISO]);
    const allPendingOps = useMemo(() => {
      return pendingOperations.filter(op => op.status === "pending");
    }, [pendingOperations]);
    const historyOps = useMemo(() => {
      return pendingOperations.filter(op => op.status === "cleared");
    }, [pendingOperations]);
    const currentDataset = useMemo(() => {
      if (filterView === "all_pending") return allPendingOps;
      if (filterView === "history") return historyOps;
      return monthOps;
    }, [filterView, monthOps, allPendingOps, historyOps]);
    const displayRows = useMemo(() => {
      let list = currentDataset;
      if (filterType !== "all") {
        list = list.filter(op => op.type === filterType);
      }
      if (searchText.trim()) {
        const q = searchText.trim().toLowerCase();
        list = list.filter(op => {
          const cat = categories.find(c => c.id === op.categoryId);
          return (op.label || "").toLowerCase().includes(q) || (op.refNumber || "").toLowerCase().includes(q) || (op.notes || "").toLowerCase().includes(q) || cat && cat.label.toLowerCase().includes(q) || String(op.amount).includes(q);
        });
      }
      return [...list].sort((a, b) => {
        let va = a[sortKey],
          vb = b[sortKey];
        if (sortKey === "amount") {
          va = Number(va) || 0;
          vb = Number(vb) || 0;
        } else {
          va = String(va || "").toLowerCase();
          vb = String(vb || "").toLowerCase();
        }
        return va < vb ? -sortDir : va > vb ? sortDir : 0;
      });
    }, [currentDataset, filterType, searchText, sortKey, sortDir, categories]);
    const budgetPresets = useMemo(() => {
      const list = [];
      (sourceData.charges || []).forEach(c => {
        list.push({
          key: `charge_${c.id}`,
          label: `Charge : ${c.label} (${eur(c.monthly)}/m)`,
          raw: c,
          kind: "charge"
        });
      });
      (sourceData.incomes || []).forEach(i => {
        list.push({
          key: `income_${i.id}`,
          label: `Revenu : ${i.label} (${eur(i.monthly)}/m)`,
          raw: i,
          kind: "income"
        });
      });
      (sourceData.oneoff || []).forEach(o => {
        list.push({
          key: `oneoff_${o.id}`,
          label: `Dépense ponctuelle : ${o.label} (${eur(o.amount)})`,
          raw: o,
          kind: "oneoff"
        });
      });
      return list;
    }, [sourceData.charges, sourceData.incomes, sourceData.oneoff]);
    const handleSelectPreset = key => {
      setModalPreset(key);
      if (!key) return;
      const preset = budgetPresets.find(p => p.key === key);
      if (!preset) return;
      if (preset.kind === "charge") {
        setModalData(prev => ({
          ...prev,
          label: preset.raw.label || "",
          categoryId: preset.raw.categoryId || prev.categoryId,
          amount: String(Math.abs(Number(preset.raw.monthly) || 0)),
          isDebit: true,
          type: "cb"
        }));
      } else if (preset.kind === "income") {
        setModalData(prev => ({
          ...prev,
          label: preset.raw.label || "",
          categoryId: preset.raw.categoryId || prev.categoryId,
          amount: String(Math.abs(Number(preset.raw.monthly) || 0)),
          isDebit: false,
          type: "virement"
        }));
      } else if (preset.kind === "oneoff") {
        setModalData(prev => ({
          ...prev,
          label: preset.raw.label || "",
          amount: String(Math.abs(Number(preset.raw.amount) || 0)),
          isDebit: true,
          type: "cheque"
        }));
      }
    };
    const openAddModal = () => {
      setEditingOp(null);
      setModalPreset("");
      setModalData({
        date: isCurrentMonth ? todayISO : `${monthISO}-01`,
        expectedDate: "",
        type: "cheque",
        refNumber: "",
        label: "",
        amount: "",
        isDebit: true,
        categoryId: "",
        notes: "",
        splits: []
      });
      setShowAddModal(true);
    };
    const openEditModal = op => {
      setEditingOp(op);
      setModalPreset("");
      setModalData({
        date: op.date || "",
        expectedDate: op.expectedDate || "",
        type: op.type || "cheque",
        refNumber: op.refNumber || "",
        label: op.label || "",
        amount: String(Math.abs(Number(op.amount) || 0)),
        isDebit: (Number(op.amount) || 0) <= 0,
        categoryId: op.categoryId || "",
        notes: op.notes || "",
        splits: Array.isArray(op.splits) ? op.splits.map(s => ({
          ...s,
          amount: String(Math.abs(Number(s.amount) || 0))
        })) : []
      });
      setShowAddModal(true);
    };
    const handleSaveModal = async e => {
      e.preventDefault();
      const amtNum = parseFloat(String(modalData.amount).replace(",", "."));
      if (isNaN(amtNum) || amtNum <= 0) {
        alert("Veuillez saisir un montant supérieur à 0.");
        return;
      }
      if (!modalData.label.trim()) {
        alert("Veuillez saisir un libellé ou bénéficiaire.");
        return;
      }
      const signedAmount = (modalData.isDebit ? -1 : 1) * Math.abs(amtNum);
      const opDate = modalData.date || (isCurrentMonth ? todayISO : `${monthISO}-01`);

      const formattedSplits = (modalData.splits || []).map(sp => ({
        id: sp.id || (typeof uid === 'function' ? uid() : `split_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`),
        label: sp.label || "",
        categoryId: sp.categoryId || "",
        amount: (modalData.isDebit ? -1 : 1) * Math.abs(parseFloat(String(sp.amount).replace(",", ".")) || 0)
      })).filter(sp => Math.abs(sp.amount) > 0);

      const opPayload = {
        date: opDate,
        expectedDate: modalData.expectedDate || "",
        type: modalData.type || "cheque",
        refNumber: (modalData.refNumber || "").trim(),
        label: modalData.label.trim(),
        amount: signedAmount,
        categoryId: modalData.categoryId || "",
        notes: modalData.notes || "",
        splits: formattedSplits
      };

      const generateId = typeof uid === 'function' ? uid : () => `op_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`;
      const targetOpId = editingOp ? editingOp.id : generateId();
      const fullOpObj = {
        id: targetOpId,
        status: editingOp ? (editingOp.status || "pending") : "pending",
        linkedTxId: editingOp ? (editingOp.linkedTxId || null) : null,
        clearedDate: editingOp ? (editingOp.clearedDate || null) : null,
        ...opPayload
      };

      // 1. Mise à jour synchrone du store React
      if (update) {
        if (editingOp) {
          update("bankImport", b => ({
            ...b,
            pendingOperations: (b?.pendingOperations || []).map(op => op.id === editingOp.id ? {
              ...op,
              ...opPayload
            } : op)
          }));
        } else {
          update("bankImport", b => ({
            ...b,
            pendingOperations: [...(b?.pendingOperations || []).filter(o => o.id !== targetOpId), fullOpObj]
          }));
        }
      }

      // 2. Mise à jour synchrone de l'état local apiData pour rafraîchissement immédiat de la vue
      setApiData(prev => {
        const curOps = Array.isArray(prev?.pendingOperations) ? [...prev.pendingOperations] : [];
        const nextOps = editingOp
          ? curOps.map(op => op.id === editingOp.id ? { ...op, ...opPayload } : op)
          : [...curOps.filter(o => o.id !== targetOpId), fullOpObj];
        return {
          ...(prev || {}),
          pendingOperations: nextOps
        };
      });

      // 3. Navigation automatique vers le mois de l'opération et ajustement des filtres
      const opMonthISO = opDate.slice(0, 7);
      const opParts = opMonthISO.split("-");
      const targetYear = parseInt(opParts[0], 10);
      const targetMonth = parseInt(opParts[1], 10);
      if (!isNaN(targetYear) && !isNaN(targetMonth)) {
        setSelYear(targetYear);
        setSelMonth(targetMonth);
      }
      if (filterView === "history") {
        setFilterView("month");
      }
      if (filterType !== "all" && filterType !== (modalData.type || "cheque")) {
        setFilterType("all");
      }

      if (editingOp) {
        showToast("Opération modifiée avec succès.");
      } else {
        const opMonthName = new Date(targetYear, targetMonth - 1, 1).toLocaleDateString("fr-FR", {
          month: "long",
          year: "numeric"
        });
        showToast(`Opération « ${opPayload.label} » (${opPayload.amount < 0 ? '-' : '+'}${Math.abs(opPayload.amount).toFixed(2)} €) enregistrée pour le ${opDate.split("-").reverse().join("/")} (classée en ${opMonthName}).`);
      }
      setShowAddModal(false);

      // 4. Appel asynchrone du service / API en arrière-plan sans bloquer l'interface
      const apiInstance = getApi();
      if (apiInstance && apiInstance.savePendingOperation) {
        apiInstance.savePendingOperation(opPayload, editingOp ? editingOp.id : targetOpId).then(res => {
          if (res && res.id && res.id !== targetOpId) {
            setApiData(prev => prev ? {
              ...prev,
              pendingOperations: (prev.pendingOperations || []).map(o => o.id === targetOpId ? res : o)
            } : prev);
          }
        }).catch(err => {
          console.error("Erreur savePendingOperation:", err);
        });
      }
    };

    const handleDeleteOp = async opId => {
      if (window.confirm("Supprimer définitivement cette opération engagée ?")) {
        if (update) {
          update("bankImport", b => ({
            ...b,
            pendingOperations: (b?.pendingOperations || []).filter(op => op.id !== opId)
          }));
        }
        setApiData(prev => {
          if (!prev) return prev;
          return {
            ...prev,
            pendingOperations: (prev.pendingOperations || []).filter(op => op.id !== opId)
          };
        });
        const apiInstance = getApi();
        if (apiInstance && apiInstance.deletePendingOperation) {
          apiInstance.deletePendingOperation(opId).catch(err => console.error(err));
        }
        showToast("Opération supprimée.");
      }
    };

    const handleUnlink = async opId => {
      if (update) {
        update("bankImport", b => ({
          ...b,
          pendingOperations: (b?.pendingOperations || []).map(op => op.id === opId ? {
            ...op,
            status: "pending",
            linkedTxId: null,
            clearedDate: null
          } : op)
        }));
      }
      setApiData(prev => {
        if (!prev) return prev;
        return {
          ...prev,
          pendingOperations: (prev.pendingOperations || []).map(op => op.id === opId ? {
            ...op,
            status: "pending",
            linkedTxId: null,
            clearedDate: null
          } : op)
        };
      });
      const apiInstance = getApi();
      if (apiInstance && apiInstance.unlinkPendingOperation) {
        apiInstance.unlinkPendingOperation(opId).catch(err => console.error(err));
      }
      showToast("Opération déliée (remise en circulation).");
    };

    const usedTxIds = useMemo(() => {
      const s = new Set();
      pendingOperations.forEach(op => {
        if (op.linkedTxId && (!matchingOp || op.id !== matchingOp.id)) {
          s.add(op.linkedTxId);
        }
      });
      return s;
    }, [pendingOperations, matchingOp]);
    const candidateTxs = useMemo(() => {
      if (!matchingOp) return [];
      const q = matchingSearch.trim().toLowerCase();
      const targetAmt = Math.abs(Number(matchingOp.amount) || 0);
      return transactions.filter(t => !usedTxIds.has(t.id)).filter(t => {
        if (!q) return true;
        return (t.label || "").toLowerCase().includes(q) || String(t.amount).includes(q) || (t.date || "").includes(q);
      }).map(t => {
        const txAmt = Math.abs(Number(t.amount) || 0);
        const isExactAmt = Math.abs(txAmt - targetAmt) < 0.01;
        const matchesRef = matchingOp.refNumber && matchingOp.refNumber.length >= 3 && (t.label || "").toLowerCase().includes(matchingOp.refNumber.toLowerCase());
        const isSuggested = isExactAmt || matchesRef;
        return {
          ...t,
          isExactAmt,
          matchesRef,
          isSuggested
        };
      }).sort((a, b) => {
        if (a.isSuggested && !b.isSuggested) return -1;
        if (!a.isSuggested && b.isSuggested) return 1;
        return a.date < b.date ? 1 : -1;
      });
    }, [matchingOp, transactions, usedTxIds, matchingSearch]);

    const handleLinkTransaction = async tx => {
      if (!matchingOp) return;
      const targetOpId = matchingOp.id;

      let transferredSplits = null;
      if (Array.isArray(matchingOp.splits) && matchingOp.splits.length > 0) {
        const realAmt = Number(tx.amount) || 0;
        const opSplitSum = matchingOp.splits.reduce((s, sp) => s + (Number(sp.amount) || 0), 0);
        if (Math.abs(opSplitSum) > 0.001 && Math.abs(realAmt - opSplitSum) > 0.001) {
          const ratio = realAmt / opSplitSum;
          transferredSplits = matchingOp.splits.map(sp => ({
            ...sp,
            id: sp.id || (typeof uid === 'function' ? uid() : `split_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`),
            amount: Math.round((Number(sp.amount) || 0) * ratio * 100) / 100
          }));
          const newSum = transferredSplits.reduce((s, sp) => s + sp.amount, 0);
          const diff = Math.round((realAmt - newSum) * 100) / 100;
          if (diff !== 0 && transferredSplits.length > 0) {
            transferredSplits[transferredSplits.length - 1].amount = Math.round((transferredSplits[transferredSplits.length - 1].amount + diff) * 100) / 100;
          }
        } else {
          transferredSplits = matchingOp.splits.map(sp => ({
            ...sp,
            id: sp.id || (typeof uid === 'function' ? uid() : `split_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`),
            amount: Number(sp.amount) || 0
          }));
        }
      }

      if (update) {
        update("bankImport", b => ({
          ...b,
          pendingOperations: (b?.pendingOperations || []).map(op => op.id === targetOpId ? {
            ...op,
            status: "cleared",
            linkedTxId: tx.id,
            clearedDate: tx.date
          } : op),
          transactions: transferredSplits ? (b?.transactions || []).map(t => t.id === tx.id ? { ...t, splits: transferredSplits } : t) : (b?.transactions || [])
        }));
      }
      setApiData(prev => {
        if (!prev) return prev;
        return {
          ...prev,
          pendingOperations: (prev.pendingOperations || []).map(op => op.id === targetOpId ? {
            ...op,
            status: "cleared",
            linkedTxId: tx.id,
            clearedDate: tx.date
          } : op),
          transactions: transferredSplits ? (prev.transactions || []).map(t => t.id === tx.id ? { ...t, splits: transferredSplits } : t) : (prev.transactions || [])
        };
      });
      const apiInstance = getApi();
      if (apiInstance && apiInstance.linkPendingOperation) {
        apiInstance.linkPendingOperation(targetOpId, tx.id, tx.date).catch(err => console.error(err));
      }
      if (transferredSplits && apiInstance && apiInstance.updateBankTransactionSplits) {
        apiInstance.updateBankTransactionSplits(tx.id, transferredSplits).catch(err => console.error(err));
      }
      showToast(`Opération rapprochée avec le débit du ${tx.date.split("-").reverse().join("/")}${transferredSplits ? " (ventilations transférées)" : ""}.`);
      setMatchingOp(null);
    };
    const handleAutoMatch = async () => {
      if (api && api.autoMatchPendingOperations) {
        const res = await api.autoMatchPendingOperations();
        await loadDataFromApi();
        if (res && res.matchCount > 0) {
          showToast(`🎉 ${res.matchCount} opération(s) rapprochée(s) automatiquement !`);
        } else {
          showToast("ℹ️ Aucune nouvelle correspondance automatique évidente trouvée.");
        }
        return;
      }
      let matchCount = 0;
      const currentlyLinked = new Set();
      pendingOperations.forEach(op => {
        if (op.linkedTxId) currentlyLinked.add(op.linkedTxId);
      });
      const updated = pendingOperations.map(op => {
        if (op.status === "cleared" && op.linkedTxId) return op;
        const opTargetAmt = Number(op.amount) || 0;
        if (op.refNumber && op.refNumber.length >= 3) {
          const foundByRef = transactions.find(t => !currentlyLinked.has(t.id) && (t.label || "").toLowerCase().includes(op.refNumber.toLowerCase()) && Math.abs(Math.abs(Number(t.amount) || 0) - Math.abs(opTargetAmt)) < 0.01);
          if (foundByRef) {
            currentlyLinked.add(foundByRef.id);
            matchCount++;
            return {
              ...op,
              status: "cleared",
              linkedTxId: foundByRef.id,
              clearedDate: foundByRef.date
            };
          }
        }
        const opDateMs = op.date ? new Date(op.date).getTime() : 0;
        const exactCandidates = transactions.filter(t => {
          if (currentlyLinked.has(t.id)) return false;
          const txAmt = Number(t.amount) || 0;
          if (Math.abs(Math.abs(txAmt) - Math.abs(opTargetAmt)) >= 0.01) return false;
          if (opDateMs && t.date) {
            const tDateMs = new Date(t.date).getTime();
            const diffDays = Math.abs(tDateMs - opDateMs) / (1000 * 60 * 60 * 24);
            if (diffDays > 90) return false;
          }
          return true;
        });
        if (exactCandidates.length === 1) {
          const found = exactCandidates[0];
          currentlyLinked.add(found.id);
          matchCount++;
          return {
            ...op,
            status: "cleared",
            linkedTxId: found.id,
            clearedDate: found.date
          };
        }
        return op;
      });
      if (matchCount > 0) {
        if (update) {
          update("bankImport", b => ({
            ...b,
            pendingOperations: updated
          }));
        }
        showToast(`🎉 ${matchCount} opération(s) rapprochée(s) automatiquement !`);
      } else {
        showToast("ℹ️ Aucune nouvelle correspondance automatique évidente trouvée.");
      }
    };

    // --- Fonctions d'import des encours CB ---
    const parsePurchaseDateFromLabel = label => {
      if (!label) return null;
      const m = label.match(/DU\s+(\d{2})(\d{2})(\d{2,4})/i);
      if (m) {
        const d = parseInt(m[1], 10);
        const mo = parseInt(m[2], 10);
        let y = parseInt(m[3], 10);
        if (y < 100) y = 2000 + y;
        if (d >= 1 && d <= 31 && mo >= 1 && mo <= 12) {
          return `${y}-${String(mo).padStart(2, "0")}-${String(d).padStart(2, "0")}`;
        }
      }
      return null;
    };

    const handleImportFile = file => {
      if (!file) return;
      setImportFileName(file.name);
      setImportSummary(null);
      const reader = new FileReader();
      reader.onload = e => {
        const text = String(e.target.result || "");
        // Auto-détection du séparateur
        const countSemi = (text.match(/;/g) || []).length;
        const countComma = (text.match(/,/g) || []).length;
        const countTab = (text.match(/\t/g) || []).length;
        let delim = importDelimiter;
        if (countSemi > countComma && countSemi > countTab) delim = ";";
        else if (countComma > countSemi && countComma > countTab) delim = ",";
        else if (countTab > countSemi && countTab > countComma) delim = "\t";
        setImportDelimiter(delim);

        const rows = parseCSVText(text, delim);
        if (!rows || rows.length === 0) return;

        // Auto-détection de la ligne d'en-tête
        let headerRowIdx = 0;
        let dCol = 0, lCol = 1, aCol = 2;
        let foundHeader = false;

        for (let i = 0; i < Math.min(rows.length, 10); i++) {
          const r = rows[i];
          const isDateH = r.some(c => /date/i.test(c));
          const isLabelH = r.some(c => /libell|label|description|operation/i.test(c));
          const isAmtH = r.some(c => /montant|amount|debit|credit/i.test(c));
          if ((isDateH && isLabelH) || (isDateH && isAmtH)) {
            headerRowIdx = i;
            foundHeader = true;
            r.forEach((cell, ci) => {
              const c = (cell || "").toLowerCase();
              if (c.includes("date")) dCol = ci;
              else if (c.includes("libell") || c.includes("label") || c.includes("description") || c.includes("operation")) lCol = ci;
              if (c.includes("montant") || c.includes("amount") || c.includes("débit") || c.includes("debit")) aCol = ci;
            });
            break;
          }
        }

        const dataRows = foundHeader ? rows.slice(headerRowIdx + 1) : rows;
        setImportRawRows(dataRows);

        const nCols = dataRows[0] ? dataRows[0].length : 0;
        const roles = [];
        for (let i = 0; i < nCols; i++) {
          if (i === dCol) roles.push("date");
          else if (i === lCol) roles.push("label");
          else if (i === aCol) roles.push("amount");
          else roles.push("ignore");
        }
        if (!roles.includes("date") && !roles.includes("label") && !roles.includes("amount")) {
          if (nCols > 0) roles[0] = "date";
          if (nCols > 1) roles[1] = "label";
          if (nCols > 2) roles[2] = "amount";
        }
        setImportColRoles(roles);
      };
      reader.readAsText(file, "UTF-8");
    };

    const setImportRole = (colIdx, role) => {
      setImportColRoles(prev => prev.map((r, i) => {
        if (i === colIdx) return role;
        return role !== "ignore" && r === role ? "ignore" : r;
      }));
    };

    const doImportPending = async () => {
      if (!importRawRows || !importColRoles) return;
      const dateCol = importColRoles.indexOf("date");
      const labelCol = importColRoles.indexOf("label");
      const amountCol = importColRoles.indexOf("amount");

      if (dateCol === -1 || labelCol === -1 || amountCol === -1) {
        setImportSummary({
          error: "Il faut au minimum assigner les rôles Date, Libellé et Montant à une colonne."
        });
        return;
      }

      if (api && api.importPendingCB) {
        const summary = await api.importPendingCB(importRawRows, importColRoles, {
          dateFormat: importDateFormat,
          usePurchaseDate: importUsePurchaseDate
        });
        setImportSummary(summary);
        await loadDataFromApi();
        if (summary.imported > 0) {
          const firstOpDate = summary.firstOpDate;
          const targetMonthISO = firstOpDate ? firstOpDate.slice(0, 7) : monthISO;
          if (targetMonthISO !== monthISO) {
            const parts = targetMonthISO.split("-");
            const targetY = parseInt(parts[0], 10);
            const targetM = parseInt(parts[1], 10);
            const targetMonthName = new Date(targetY, targetM - 1, 1).toLocaleDateString("fr-FR", {
              month: "long",
              year: "numeric"
            });
            setSelYear(targetY);
            setSelMonth(targetM);
            showToast(`🎉 ${summary.imported} opération(s) CB importée(s) (classées en ${targetMonthName}).`);
          } else {
            showToast(`🎉 ${summary.imported} opération(s) CB importée(s) avec succès !`);
          }
        }
        return;
      }

      // Comptabilisation des opérations en cours existantes pour la déduplication exacte
      const existingCounts = {};
      pendingOperations.forEach(op => {
        const k = transactionDedupeKey(op);
        existingCounts[k] = (existingCounts[k] || 0) + 1;
      });

      const fileKeyCounts = {};
      let imported = [], ignoredDuplicates = [];
      const rules = sourceData.rules || [];

      importRawRows.forEach(row => {
        const rawDate = row[dateCol];
        let dateISO = parseDateWithFormat(rawDate, importDateFormat) || parseDateWithFormat(rawDate, "DD/MM/YYYY") || parseDateWithFormat(rawDate, "YYYY-MM-DD");
        if (!dateISO) return;

        const rawLabel = (row[labelCol] || "").trim();
        if (!rawLabel) return;

        const amt = parseAmountText(row[amountCol]);

        // Gestion de la date d'émission (date d'achat extraite ou date du relevé)
        const purchaseDate = parsePurchaseDateFromLabel(rawLabel);
        let finalOpDate = dateISO;
        let expectedDebitDate = dateISO;

        if (importUsePurchaseDate && purchaseDate) {
          finalOpDate = purchaseDate;
          expectedDebitDate = dateISO;
        }

        const op = {
          id: uid(),
          date: finalOpDate,
          expectedDate: expectedDebitDate,
          type: "cb",
          refNumber: "",
          label: rawLabel,
          amount: amt,
          categoryId: "",
          status: "pending",
          linkedTxId: null,
          clearedDate: null,
          notes: purchaseDate && !importUsePurchaseDate ? `Achat le ${purchaseDate.split("-").reverse().join("/")}` : ""
        };

        const key = transactionDedupeKey(op);
        fileKeyCounts[key] = (fileKeyCounts[key] || 0) + 1;
        const currentStored = existingCounts[key] || 0;

        if (fileKeyCounts[key] <= currentStored) {
          ignoredDuplicates.push(op);
        } else {
          existingCounts[key] = (existingCounts[key] || 0) + 1;
          imported.push(op);
        }
      });

      // Application automatique du moteur de règles de catégorisation
      imported = applyRulesToTransactions(imported, rules);
      const autoCategorized = imported.filter(op => op.categoryId).length;

      if (imported.length > 0) {
        if (update) {
          update("bankImport", b => ({
            ...b,
            pendingOperations: [...(b.pendingOperations || []), ...imported]
          }));
        }
      }

      setImportSummary({
        imported: imported.length,
        duplicates: ignoredDuplicates.length,
        autoCategorized,
        ignoredDuplicates
      });

      if (imported.length > 0) {
        const firstOpDate = imported[0].date;
        const targetMonthISO = firstOpDate ? firstOpDate.slice(0, 7) : monthISO;
        if (targetMonthISO !== monthISO) {
          const parts = targetMonthISO.split("-");
          const targetY = parseInt(parts[0], 10);
          const targetM = parseInt(parts[1], 10);
          const targetMonthName = new Date(targetY, targetM - 1, 1).toLocaleDateString("fr-FR", {
            month: "long",
            year: "numeric"
          });
          setSelYear(targetY);
          setSelMonth(targetM);
          showToast(`🎉 ${imported.length} opération(s) CB importée(s) (classées en ${targetMonthName}).`);
        } else {
          showToast(`🎉 ${imported.length} opération(s) CB importée(s) avec succès !`);
        }
      }
    };

    const forceImportPendingDuplicate = async op => {
      const rules = sourceData.rules || [];
      const categorizedOp = applyRulesToTransactions([op], rules)[0] || op;
      if (update) {
        update("bankImport", b => ({
          ...b,
          pendingOperations: [...(b?.pendingOperations || []), categorizedOp]
        }));
      }
      setApiData(prev => {
        if (!prev) return prev;
        return {
          ...prev,
          pendingOperations: [...(prev.pendingOperations || []), categorizedOp]
        };
      });
      const apiInstance = getApi();
      if (apiInstance && apiInstance.forceImportPendingOperation) {
        apiInstance.forceImportPendingOperation(op).catch(err => console.error(err));
      }
      setImportSummary(prev => prev ? {
        ...prev,
        imported: prev.imported + 1,
        duplicates: Math.max(0, prev.duplicates - 1),
        ignoredDuplicates: (prev.ignoredDuplicates || []).filter(o => o.id !== op.id)
      } : null);
      showToast(`Opération « ${op.label} » ajoutée.`);
    };

    const handleMergeCandidate = async (manualOpId, bankOp) => {
      const merger = ops => {
        const manualOp = (ops || []).find(o => o.id === manualOpId);
        const cat = manualOp && manualOp.categoryId ? manualOp.categoryId : (bankOp.categoryId || "");
        return (ops || []).filter(o => o.id !== bankOp.id).map(o => o.id === manualOpId ? {
          ...o,
          date: bankOp.date || o.date,
          expectedDate: bankOp.expectedDate || o.expectedDate,
          label: bankOp.label || o.label,
          amount: bankOp.amount !== undefined ? bankOp.amount : o.amount,
          categoryId: cat,
          status: "pending",
          linkedTxId: null,
          clearedDate: null,
          notes: bankOp.notes || o.notes
        } : o);
      };
      if (update) {
        update("bankImport", b => ({
          ...b,
          pendingOperations: merger(b?.pendingOperations || [])
        }));
      }
      setApiData(prev => {
        if (!prev) return prev;
        return {
          ...prev,
          pendingOperations: merger(prev.pendingOperations || [])
        };
      });
      const apiInstance = getApi();
      if (apiInstance && apiInstance.mergePendingOperation) {
        apiInstance.mergePendingOperation(manualOpId, bankOp).catch(err => console.error(err));
      }
      setImportSummary(prev => prev ? {
        ...prev,
        duplicateCandidates: (prev.duplicateCandidates || []).filter(dc => dc.incomingOp.id !== bankOp.id)
      } : null);
      showToast(`🔀 Opération fusionnée avec succès (catégorie conservée, statut en cours) !`);
    };

    const handleImportCandidateSeparately = async (bankOp) => {
      setImportSummary(prev => prev ? {
        ...prev,
        duplicateCandidates: (prev.duplicateCandidates || []).filter(dc => dc.incomingOp.id !== bankOp.id)
      } : null);
      showToast(`Opération « ${bankOp.label} » conservée comme ligne distincte.`);
    };

    const handleIgnoreCandidate = async (bankOp) => {
      if (update) {
        update("bankImport", b => ({
          ...b,
          pendingOperations: (b?.pendingOperations || []).filter(o => o.id !== bankOp.id)
        }));
      }
      setApiData(prev => {
        if (!prev) return prev;
        return {
          ...prev,
          pendingOperations: (prev.pendingOperations || []).filter(o => o.id !== bankOp.id)
        };
      });
      const apiInstance = getApi();
      if (apiInstance && apiInstance.deletePendingOperation) {
        apiInstance.deletePendingOperation(bankOp.id).catch(err => console.error(err));
      }
      setImportSummary(prev => prev ? {
        ...prev,
        imported: Math.max(0, prev.imported - 1),
        duplicateCandidates: (prev.duplicateCandidates || []).filter(dc => dc.incomingOp.id !== bankOp.id)
      } : null);
      showToast(`Opération « ${bankOp.label} » ignorée.`);
    };

    const resetImportModal = () => {
      setShowImportModal(false);
      setImportRawRows(null);
      setImportColRoles(null);
      setImportFileName("");
      setImportSummary(null);
      setShowIgnoredModal(false);
      setShowDuplicateModal(false);
    };

    const colSort = key => {
      if (sortKey === key) setSortDir(-sortDir);else {
        setSortKey(key);
        setSortDir(1);
      }
    };
    const sortIcon = key => sortKey === key ? sortDir === 1 ? " ↑" : " ↓" : "";
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
    return /*#__PURE__*/React.createElement(React.Fragment, null, toast && /*#__PURE__*/React.createElement("div", {
      style: {
        position: "fixed",
        bottom: 24,
        right: 24,
        zIndex: 1500,
        background: C?.navy || "#28394A",
        color: "#fff",
        padding: "12px 18px",
        borderRadius: 10,
        boxShadow: "0 8px 24px rgba(0,0,0,0.25)",
        display: "flex",
        alignItems: "center",
        gap: 14,
        fontSize: 13,
        animation: "fadeIn 0.2s ease-out"
      }
    }, /*#__PURE__*/React.createElement("div", null, toast.message), toast.actionLabel && /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => {
        toast.actionFn && toast.actionFn();
        setToast(null);
      },
      style: {
        background: C?.pine || "#2F5D50",
        color: "#fff",
        border: "none",
        borderRadius: 6,
        padding: "4px 10px",
        fontSize: 12,
        fontWeight: 700,
        cursor: "pointer"
      }
    }, toast.actionLabel), /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => setToast(null),
      style: {
        background: "none",
        border: "none",
        color: "#9FB0BE",
        cursor: "pointer",
        fontSize: 16
      }
    }, "✕")), /*#__PURE__*/React.createElement("div", {
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
    }, "Opérations en cours & Chèques"), openHelp && /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => openHelp("pending"),
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
      title: "Aide sur les opérations en cours"
    }, "?")), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12.5,
        color: C?.inkSoft || "#6B7278",
        marginTop: 3
      }
    }, "Suivez vos chèques émis, CB différées et visualisez votre ", /*#__PURE__*/React.createElement("strong", null, "solde réel disponible"), ".")), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        alignItems: "center",
        gap: 8
      }
    }, !isCurrentMonth && /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: goToCurrentMonth,
      style: {
        padding: "6px 12px",
        borderRadius: 8,
        fontSize: 11.5,
        fontWeight: 700,
        background: C?.pineSoft || "#E3ECE8",
        color: C?.pine || "#2F5D50",
        border: `1px solid ${C?.pine || "#2F5D50"}`,
        cursor: "pointer"
      }
    }, "📅 Revenir au mois en cours"), /*#__PURE__*/React.createElement("div", {
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
    }, "►")))), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 12,
        marginBottom: 18,
        flexWrap: "wrap"
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        flex: "1 1 200px",
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 10,
        padding: "14px 18px"
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11,
        color: C?.inkSoft || "#6B7278",
        marginBottom: 3,
        fontWeight: 600
      }
    }, "Solde Banque (Relevé)"), /*#__PURE__*/React.createElement("div", {
      style: {
        fontFamily: "'IBM Plex Mono', monospace",
        fontSize: 18,
        fontWeight: 700,
        color: C?.navy || "#28394A"
      }
    }, eurExact(soldeBanque)), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 10.5,
        color: C?.inkSoft || "#6B7278",
        marginTop: 4
      }
    }, "Fin de ", monthLabel)), /*#__PURE__*/React.createElement("div", {
      style: {
        flex: "1 1 200px",
        background: totalDebitsEnCours < 0 ? "#FEF2F2" : C?.panel || "#FFFFFF",
        border: `1px solid ${totalDebitsEnCours < 0 ? "#FECACA" : C?.line || "#DED6C4"}`,
        borderRadius: 10,
        padding: "14px 18px"
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11,
        color: totalDebitsEnCours < 0 ? "#991B1B" : C?.inkSoft || "#6B7278",
        marginBottom: 3,
        fontWeight: 600
      }
    }, "Chèques & Débits en cours"), /*#__PURE__*/React.createElement("div", {
      style: {
        fontFamily: "'IBM Plex Mono', monospace",
        fontSize: 18,
        fontWeight: 700,
        color: totalDebitsEnCours < 0 ? "#DC2626" : C?.inkSoft || "#6B7278"
      }
    }, eurExact(totalDebitsEnCours)), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 10.5,
        color: totalDebitsEnCours < 0 ? "#B91C1C" : C?.inkSoft || "#6B7278",
        marginTop: 4
      }
    }, activePendingDebits.length, " débit(s) non prélevé(s)")), /*#__PURE__*/React.createElement("div", {
      style: {
        flex: "1 1 200px",
        background: totalCreditsEnAttente > 0 ? "#F0FDF4" : C?.panel || "#FFFFFF",
        border: `1px solid ${totalCreditsEnAttente > 0 ? "#BBF7D0" : C?.line || "#DED6C4"}`,
        borderRadius: 10,
        padding: "14px 18px"
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11,
        color: totalCreditsEnAttente > 0 ? "#166534" : C?.inkSoft || "#6B7278",
        marginBottom: 3,
        fontWeight: 600
      }
    }, "Crédits & Salaires attendus"), /*#__PURE__*/React.createElement("div", {
      style: {
        fontFamily: "'IBM Plex Mono', monospace",
        fontSize: 18,
        fontWeight: 700,
        color: totalCreditsEnAttente > 0 ? "#16A34A" : C?.inkSoft || "#6B7278"
      }
    }, "+", eurExact(totalCreditsEnAttente)), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 10.5,
        color: totalCreditsEnAttente > 0 ? "#15803D" : C?.inkSoft || "#6B7278",
        marginTop: 4
      }
    }, activePendingCredits.length, " rentrée(s) en attente")), /*#__PURE__*/React.createElement("div", {
      style: {
        flex: "1 1 220px",
        background: soldeDisponibleReel >= (Number(data?.settings?.cashFloor) || 0) ? "#F0FDF4" : soldeDisponibleReel >= 0 ? "#FFFBEB" : "#FEF2F2",
        border: `2px solid ${soldeDisponibleReel >= (Number(data?.settings?.cashFloor) || 0) ? "#86EFAC" : soldeDisponibleReel >= 0 ? "#FDE68A" : "#FECACA"}`,
        borderRadius: 10,
        padding: "14px 18px",
        boxShadow: "0 2px 8px rgba(0,0,0,0.04)"
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11,
        color: soldeDisponibleReel >= 0 ? C?.pine || "#2F5D50" : "#991B1B",
        marginBottom: 3,
        fontWeight: 700
      }
    }, "🛡️ Solde Réellement Disponible"), /*#__PURE__*/React.createElement("div", {
      style: {
        fontFamily: "'IBM Plex Mono', monospace",
        fontSize: 20,
        fontWeight: 800,
        color: soldeDisponibleReel >= 0 ? C?.pine || "#2F5D50" : "#DC2626"
      }
    }, eurExact(soldeDisponibleReel)), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 10.5,
        color: soldeDisponibleReel >= 0 ? C?.inkSoft || "#6B7278" : "#B91C1C",
        marginTop: 4
      }
    }, "Après dénouement des engagements"))), reliquats.length > 0 && filterView === "month" && /*#__PURE__*/React.createElement("div", {
      style: {
        marginBottom: 18,
        padding: "12px 16px",
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
        fontSize: 20
      }
    }, "⚠️"), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 13,
        fontWeight: 700,
        color: "#92400E"
      }
    }, reliquats.length, " engagement(s) émis avant ", monthLabel, " toujours en circulation (", eurExact(reliquatsTotal), ")"), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11.5,
        color: "#B45309",
        marginTop: 2
      }
    }, "Ces chèques/différés passés ne sont pas encore débités en banque."))), /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => setShowReliquats(!showReliquats),
      style: {
        padding: "6px 14px",
        borderRadius: 7,
        fontSize: 11.5,
        fontWeight: 700,
        background: "#D97706",
        color: "#fff",
        border: "none",
        cursor: "pointer"
      }
    }, showReliquats ? "Masquer les reliquats ▲" : "Afficher et pointer les reliquats ▼")), reliquats.length > 0 && filterView === "month" && showReliquats && /*#__PURE__*/React.createElement("div", {
      style: {
        marginBottom: 20,
        border: "1px solid #FDE68A",
        borderRadius: 10,
        background: "#FEFDF8",
        overflow: "hidden"
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        padding: "8px 14px",
        background: "#FEF3C7",
        borderBottom: "1px solid #FDE68A",
        fontSize: 12,
        fontWeight: 700,
        color: "#92400E",
        display: "flex",
        justifyContent: "space-between"
      }
    }, /*#__PURE__*/React.createElement("span", null, "⏳ Détail des engagements des mois antérieurs non débités"), /*#__PURE__*/React.createElement("span", null, "Total : ", eurExact(reliquatsTotal))), /*#__PURE__*/React.createElement("div", {
      style: {
        overflowX: "auto"
      }
    }, /*#__PURE__*/React.createElement("table", {
      style: {
        width: "100%",
        borderCollapse: "collapse",
        fontSize: 12
      }
    }, /*#__PURE__*/React.createElement("thead", null, /*#__PURE__*/React.createElement("tr", {
      style: {
        background: "#FFFBEB",
        borderBottom: "1px solid #FDE68A"
      }
    }, /*#__PURE__*/React.createElement("th", {
      style: {
        padding: "6px 10px",
        textAlign: "left",
        color: "#92400E"
      }
    }, "Date émission"), /*#__PURE__*/React.createElement("th", {
      style: {
        padding: "6px 10px",
        textAlign: "left",
        color: "#92400E"
      }
    }, "Type & Réf"), /*#__PURE__*/React.createElement("th", {
      style: {
        padding: "6px 10px",
        textAlign: "left",
        color: "#92400E"
      }
    }, "Bénéficiaire / Libellé"), /*#__PURE__*/React.createElement("th", {
      style: {
        padding: "6px 10px",
        textAlign: "right",
        color: "#92400E"
      }
    }, "Montant"), /*#__PURE__*/React.createElement("th", {
      style: {
        padding: "6px 10px",
        textAlign: "center",
        color: "#92400E"
      }
    }, "Actions"))), /*#__PURE__*/React.createElement("tbody", null, reliquats.map(op => {
      const tm = OP_TYPES[op.type] || OP_TYPES.autre;
      return /*#__PURE__*/React.createElement("tr", {
        key: op.id,
        style: {
          borderBottom: "1px solid #FEF3C7"
        }
      }, /*#__PURE__*/React.createElement("td", {
        style: {
          padding: "7px 10px",
          fontFamily: "'IBM Plex Mono', monospace",
          color: C?.inkSoft || "#6B7278"
        }
      }, op.date ? op.date.split("-").reverse().join("/") : "—"), /*#__PURE__*/React.createElement("td", {
        style: {
          padding: "7px 10px"
        }
      }, /*#__PURE__*/React.createElement("span", {
        style: {
          padding: "2px 6px",
          borderRadius: 6,
          fontSize: 10.5,
          fontWeight: 600,
          background: tm.bg,
          color: tm.color,
          border: `1px solid ${tm.border}`
        }
      }, tm.icon, " ", tm.label, op.refNumber ? ` #${op.refNumber}` : "")), /*#__PURE__*/React.createElement("td", {
        style: {
          padding: "7px 10px",
          fontWeight: 600,
          color: C?.ink || "#232A2E"
        }
      }, op.label), /*#__PURE__*/React.createElement("td", {
        style: {
          padding: "7px 10px",
          textAlign: "right",
          fontFamily: "'IBM Plex Mono', monospace",
          fontWeight: 700,
          color: op.amount < 0 ? C?.brick || "#A8503C" : C?.pine || "#2F5D50"
        }
      }, eurExact(op.amount)), /*#__PURE__*/React.createElement("td", {
        style: {
          padding: "7px 10px",
          textAlign: "center"
        }
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          display: "flex",
          gap: 6,
          justifyContent: "center"
        }
      }, /*#__PURE__*/React.createElement("button", {
        type: "button",
        onClick: () => {
          setMatchingOp(op);
          setMatchingSearch("");
        },
        style: {
          padding: "3px 8px",
          borderRadius: 6,
          fontSize: 11,
          fontWeight: 700,
          background: C?.pine || "#2F5D50",
          color: "#fff",
          border: "none",
          cursor: "pointer"
        }
      }, "🔗 Pointer"), /*#__PURE__*/React.createElement("button", {
        type: "button",
        onClick: () => openEditModal(op),
        style: {
          padding: "3px 7px",
          borderRadius: 6,
          fontSize: 11,
          background: C?.panelAlt || "#EFEAE0",
          color: C?.ink || "#232A2E",
          border: `1px solid ${C?.line || "#DED6C4"}`,
          cursor: "pointer"
        }
      }, "✏️"), /*#__PURE__*/React.createElement("button", {
        type: "button",
        onClick: () => handleDeleteOp(op.id),
        style: {
          padding: "3px 7px",
          borderRadius: 6,
          fontSize: 11,
          background: C?.brickSoft || "#F4E4DF",
          color: C?.brick || "#A8503C",
          border: "none",
          cursor: "pointer"
        }
      }, "🗑️"))));
    }))))), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 10,
        alignItems: "center",
        marginBottom: 14,
        flexWrap: "wrap",
        justifyContent: "space-between"
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 8,
        alignItems: "center",
        flexWrap: "wrap"
      }
    }, [{
      key: "month",
      label: `📅 Mois de ${monthLabel} (${monthOps.length})`
    }, {
      key: "all_pending",
      label: `⏳ Tous les en-cours (${allPendingOps.length})`
    }, {
      key: "history",
      label: `🗄️ Historique rapproché (${historyOps.length})`
    }].map(tab => /*#__PURE__*/React.createElement("button", {
      type: "button",
      key: tab.key,
      onClick: () => setFilterView(tab.key),
      style: {
        padding: "7px 14px",
        borderRadius: 20,
        fontSize: 12,
        fontWeight: 700,
        cursor: "pointer",
        border: filterView === tab.key ? `2px solid ${C?.navy || "#28394A"}` : `1px solid ${C?.line || "#DED6C4"}`,
        background: filterView === tab.key ? C?.navy || "#28394A" : C?.panel || "#FFFFFF",
        color: filterView === tab.key ? "#fff" : C?.inkSoft || "#6B7278",
        transition: "all 0.15s ease"
      }
    }, tab.label)), /*#__PURE__*/React.createElement("select", {
      value: filterType,
      onChange: e => setFilterType(e.target.value),
      style: {
        ...inputStyle,
        width: 140,
        fontSize: 12,
        padding: "5px 8px",
        cursor: "pointer"
      }
    }, /*#__PURE__*/React.createElement("option", {
      value: "all"
    }, "Tous les types"), /*#__PURE__*/React.createElement("option", {
      value: "cheque"
    }, "🏷️ Chèques"), /*#__PURE__*/React.createElement("option", {
      value: "cb"
    }, "💳 CB différées"), /*#__PURE__*/React.createElement("option", {
      value: "virement"
    }, "🔄 Virements"), /*#__PURE__*/React.createElement("option", {
      value: "prelevement"
    }, "📄 Prélèvements"), /*#__PURE__*/React.createElement("option", {
      value: "autre"
    }, "🔘 Autres")), /*#__PURE__*/React.createElement("input", {
      value: searchText,
      onChange: e => setSearchText(e.target.value),
      placeholder: "🔍 Filtrer…",
      style: {
        ...inputStyle,
        width: 160,
        fontSize: 12,
        padding: "5px 10px"
      }
    })), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 8,
        alignItems: "center"
      }
    }, /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: handleAutoMatch,
      style: {
        padding: "8px 14px",
        borderRadius: 8,
        fontSize: 12,
        fontWeight: 700,
        background: C?.panelAlt || "#EFEAE0",
        color: C?.navy || "#28394A",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        cursor: "pointer",
        display: "flex",
        alignItems: "center",
        gap: 6
      },
      title: "Rapproche automatiquement les chèques et montants évidents"
    }, "⚡ Rapprochement auto"), /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => setShowImportModal(true),
      style: {
        padding: "8px 14px",
        borderRadius: 8,
        fontSize: 12,
        fontWeight: 700,
        background: C?.pineSoft || "#E3ECE8",
        color: C?.pine || "#2F5D50",
        border: `1px solid ${C?.pine || "#2F5D50"}`,
        cursor: "pointer",
        display: "flex",
        alignItems: "center",
        gap: 6
      },
      title: "Importer un fichier CSV d'encours de carte bancaire différée"
    }, "📥 Importer encours CB"), /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: openAddModal,
      style: {
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
        gap: 6,
        boxShadow: "0 2px 6px rgba(0,0,0,0.1)"
      }
    }, "➕ Nouvelle opération"))), /*#__PURE__*/React.createElement("div", {
      style: {
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 10,
        background: C?.panel || "#FFFFFF",
        overflowX: "auto"
      }
    }, /*#__PURE__*/React.createElement("table", {
      style: {
        width: "100%",
        borderCollapse: "collapse",
        minWidth: 960
      }
    }, /*#__PURE__*/React.createElement("thead", null, /*#__PURE__*/React.createElement("tr", null, /*#__PURE__*/React.createElement("th", {
      style: headerStyle("status"),
      onClick: () => colSort("status")
    }, "Statut", sortIcon("status")), /*#__PURE__*/React.createElement("th", {
      style: headerStyle("date"),
      onClick: () => colSort("date")
    }, "Date émission", sortIcon("date")), /*#__PURE__*/React.createElement("th", {
      style: headerStyle("expectedDate"),
      onClick: () => colSort("expectedDate")
    }, "Date prévue", sortIcon("expectedDate")), /*#__PURE__*/React.createElement("th", {
      style: headerStyle("type"),
      onClick: () => colSort("type")
    }, "Type & Réf", sortIcon("type")), /*#__PURE__*/React.createElement("th", {
      style: headerStyle("label"),
      onClick: () => colSort("label")
    }, "Bénéficiaire / Libellé", sortIcon("label")), /*#__PURE__*/React.createElement("th", {
      style: headerStyle("categoryId"),
      onClick: () => colSort("categoryId")
    }, "Catégorie", sortIcon("categoryId")), /*#__PURE__*/React.createElement("th", {
      style: {
        ...headerStyle("amount"),
        textAlign: "right"
      },
      onClick: () => colSort("amount")
    }, "Montant (€)", sortIcon("amount")), /*#__PURE__*/React.createElement("th", {
      style: headerStyle("linkedTxId")
    }, "Rapprochement Bancaire"), /*#__PURE__*/React.createElement("th", {
      style: {
        ...headerStyle("_act"),
        textAlign: "center",
        width: 140
      }
    }, "Actions"))), /*#__PURE__*/React.createElement("tbody", null, displayRows.length === 0 && /*#__PURE__*/React.createElement("tr", null, /*#__PURE__*/React.createElement("td", {
      colSpan: 9,
      style: {
        ...cellStyle,
        textAlign: "center",
        color: C?.inkSoft || "#6B7278",
        padding: 32
      }
    }, filterView === "month" ? `Aucune opération enregistrée pour le mois de ${monthLabel}.` : "Aucune opération ne correspond aux filtres actuels.")), displayRows.map(op => {
      const tm = OP_TYPES[op.type] || OP_TYPES.autre;
      const cat = categories.find(c => c.id === op.categoryId);
      const isCleared = op.status === "cleared" && op.linkedTxId;
      const isLate = !isCleared && op.expectedDate && op.expectedDate < todayISO;
      const linkedTx = isCleared ? transactions.find(t => t.id === op.linkedTxId) : null;
      return /*#__PURE__*/React.createElement("tr", {
        key: op.id,
        style: {
          background: isCleared ? "transparent" : isLate ? "#FEF2F2" : "#FFFDF8",
          transition: "background 0.15s"
        }
      }, /*#__PURE__*/React.createElement("td", {
        style: cellStyle
      }, isCleared ? /*#__PURE__*/React.createElement("span", {
        style: {
          display: "inline-flex",
          alignItems: "center",
          gap: 5,
          padding: "3px 9px",
          borderRadius: 12,
          fontSize: 11,
          fontWeight: 700,
          background: "#EFF6FF",
          color: "#2563EB",
          border: "1px solid #BFDBFE"
        }
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          width: 6,
          height: 6,
          borderRadius: "50%",
          background: "#2563EB"
        }
      }), "Encaissé") : isLate ? /*#__PURE__*/React.createElement("span", {
        style: {
          display: "inline-flex",
          alignItems: "center",
          gap: 5,
          padding: "3px 9px",
          borderRadius: 12,
          fontSize: 11,
          fontWeight: 700,
          background: "#FEF2F2",
          color: "#DC2626",
          border: "1px solid #FECACA"
        }
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          width: 6,
          height: 6,
          borderRadius: "50%",
          background: "#DC2626"
        }
      }), "⏱ Terme dépassé") : /*#__PURE__*/React.createElement("span", {
        style: {
          display: "inline-flex",
          alignItems: "center",
          gap: 5,
          padding: "3px 9px",
          borderRadius: 12,
          fontSize: 11,
          fontWeight: 700,
          background: "#FFFBEB",
          color: "#D97706",
          border: "1px solid #FDE68A"
        }
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          width: 6,
          height: 6,
          borderRadius: "50%",
          background: "#D97706"
        }
      }), "En circulation")), /*#__PURE__*/React.createElement("td", {
        style: {
          ...cellStyle,
          fontFamily: "'IBM Plex Mono', monospace",
          fontSize: 12
        }
      }, op.date ? op.date.split("-").reverse().join("/") : "—"), /*#__PURE__*/React.createElement("td", {
        style: {
          ...cellStyle,
          fontFamily: "'IBM Plex Mono', monospace",
          fontSize: 12,
          color: isLate ? "#DC2626" : C?.inkSoft || "#6B7278"
        }
      }, op.expectedDate ? op.expectedDate.split("-").reverse().join("/") : "—"), /*#__PURE__*/React.createElement("td", {
        style: cellStyle
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          display: "flex",
          alignItems: "center",
          gap: 5
        }
      }, /*#__PURE__*/React.createElement("span", {
        style: {
          padding: "2px 7px",
          borderRadius: 6,
          fontSize: 11,
          fontWeight: 600,
          background: tm.bg,
          color: tm.color,
          border: `1px solid ${tm.border}`
        }
      }, tm.icon, " ", tm.label), op.refNumber && /*#__PURE__*/React.createElement("span", {
        style: {
          fontFamily: "'IBM Plex Mono', monospace",
          fontSize: 11,
          color: C?.inkSoft || "#6B7278"
        }
      }, "#", op.refNumber))), /*#__PURE__*/React.createElement("td", {
        style: {
          ...cellStyle,
          fontWeight: 600,
          color: C?.ink || "#232A2E",
          maxWidth: 220
        }
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          overflow: "hidden",
          textOverflow: "ellipsis",
          whiteSpace: "nowrap"
        },
        title: op.label
      }, op.label), op.splits && op.splits.length > 0 && /*#__PURE__*/React.createElement("div", {
        style: {
          marginTop: 4,
          display: "flex",
          flexDirection: "column",
          gap: 2
        }
      }, /*#__PURE__*/React.createElement("span", {
        style: {
          display: "inline-block",
          fontSize: 10,
          fontWeight: 700,
          padding: "1px 5px",
          borderRadius: 4,
          background: C?.pineSoft || "#E3ECE8",
          color: C?.pine || "#2F5D50",
          border: `1px solid ${C?.pine || "#2F5D50"}`,
          width: "fit-content"
        }
      }, `✂ Ventilée (${op.splits.length})`), op.splits.map((s, idx) => {
        const sCat = categories.find(c => c.id === s.categoryId);
        return /*#__PURE__*/React.createElement("div", {
          key: s.id || idx,
          style: {
            fontSize: 11,
            color: C?.inkSoft || "#6B7278",
            display: "flex",
            gap: 4,
            alignItems: "center"
          }
        }, /*#__PURE__*/React.createElement("span", null, "• ", sCat ? sCat.label : (s.label || "Sous-ligne")), /*#__PURE__*/React.createElement("span", {
          style: {
            fontFamily: "'IBM Plex Mono', monospace",
            fontWeight: 600
          }
        }, "(", eurExact(s.amount), ")"));
      })), op.notes && /*#__PURE__*/React.createElement("div", {
        style: {
          fontSize: 11,
          color: C?.inkSoft || "#6B7278",
          fontWeight: 400,
          overflow: "hidden",
          textOverflow: "ellipsis",
          whiteSpace: "nowrap"
        }
      }, op.notes)), /*#__PURE__*/React.createElement("td", {
        style: {
          ...cellStyle,
          fontSize: 12,
          color: C?.inkSoft || "#6B7278"
        }
      }, op.splits && op.splits.length > 0 ? `✂ Multi-catégories (${op.splits.length})` : (cat ? cat.label : "—")), /*#__PURE__*/React.createElement("td", {
        style: {
          ...cellStyle,
          textAlign: "right",
          fontFamily: "'IBM Plex Mono', monospace",
          fontWeight: 700,
          color: op.amount < 0 ? C?.brick || "#A8503C" : C?.pine || "#2F5D50"
        }
      }, eurExact(op.amount)), /*#__PURE__*/React.createElement("td", {
        style: {
          ...cellStyle,
          fontSize: 11.5,
          maxWidth: 240
        }
      }, isCleared && linkedTx ? /*#__PURE__*/React.createElement("div", {
        style: {
          display: "flex",
          flexDirection: "column",
          gap: 2
        }
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          color: "#2563EB",
          fontWeight: 600
        }
      }, "✓ Débité le ", linkedTx.date ? linkedTx.date.split("-").reverse().join("/") : "—"), /*#__PURE__*/React.createElement("div", {
        style: {
          color: C?.inkSoft || "#6B7278",
          fontSize: 11,
          overflow: "hidden",
          textOverflow: "ellipsis",
          whiteSpace: "nowrap"
        },
        title: linkedTx.label
      }, linkedTx.label, " (", eurExact(linkedTx.amount), ")")) : /*#__PURE__*/React.createElement("span", {
        style: {
          color: C?.inkSoft || "#6B7278",
          fontStyle: "italic"
        }
      }, "Non constaté en banque")), /*#__PURE__*/React.createElement("td", {
        style: {
          ...cellStyle,
          textAlign: "center"
        }
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          display: "flex",
          gap: 5,
          justifyContent: "center",
          alignItems: "center"
        }
      }, !isCleared ? /*#__PURE__*/React.createElement("button", {
        type: "button",
        onClick: () => {
          setMatchingOp(op);
          setMatchingSearch("");
        },
        style: {
          padding: "4px 9px",
          borderRadius: 6,
          fontSize: 11,
          fontWeight: 700,
          cursor: "pointer",
          background: C?.pine || "#2F5D50",
          color: "#fff",
          border: "none",
          whiteSpace: "nowrap"
        }
      }, "🔗 Pointer") : /*#__PURE__*/React.createElement("button", {
        type: "button",
        onClick: () => handleUnlink(op.id),
        style: {
          padding: "4px 8px",
          borderRadius: 6,
          fontSize: 11,
          fontWeight: 600,
          cursor: "pointer",
          background: C?.brickSoft || "#F4E4DF",
          color: C?.brick || "#A8503C",
          border: `1px solid ${C?.brick || "#A8503C"}`,
          whiteSpace: "nowrap"
        }
      }, "✕ Délier"), /*#__PURE__*/React.createElement("button", {
        type: "button",
        onClick: () => openEditModal(op),
        style: {
          padding: "4px 7px",
          borderRadius: 6,
          fontSize: 11,
          background: C?.panelAlt || "#EFEAE0",
          color: C?.ink || "#232A2E",
          border: `1px solid ${C?.line || "#DED6C4"}`,
          cursor: "pointer"
        },
        title: "Modifier"
      }, "✏️"), /*#__PURE__*/React.createElement("button", {
        type: "button",
        onClick: () => handleDeleteOp(op.id),
        style: {
          padding: "4px 7px",
          borderRadius: 6,
          fontSize: 11,
          background: C?.brickSoft || "#F4E4DF",
          color: C?.brick || "#A8503C",
          border: "none",
          cursor: "pointer"
        },
        title: "Supprimer"
      }, "🗑️"))));
    })))), showAddModal && /*#__PURE__*/React.createElement("div", {
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
        zIndex: 1100,
        padding: 16
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 14,
        width: "100%",
        maxWidth: 580,
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
        alignItems: "center"
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontWeight: 700,
        fontSize: 16,
        color: C?.navy || "#28394A"
      }
    }, editingOp ? "✏️ Modifier l'opération engagée" : "➕ Nouvelle opération engagée"), /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => setShowAddModal(false),
      style: {
        background: "none",
        border: "none",
        fontSize: 20,
        cursor: "pointer",
        color: C?.inkSoft || "#6B7278"
      }
    }, "✕")), /*#__PURE__*/React.createElement("form", {
      onSubmit: handleSaveModal,
      style: {
        padding: 20,
        overflowY: "auto",
        display: "flex",
        flexDirection: "column",
        gap: 14
      }
    }, !editingOp && /*#__PURE__*/React.createElement("div", {
      style: {
        padding: "10px 14px",
        background: C?.pineSoft || "#E3ECE8",
        borderRadius: 8,
        border: `1px solid ${C?.pine || "#2F5D50"}`
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11.5,
        fontWeight: 700,
        color: C?.pine || "#2F5D50",
        marginBottom: 4
      }
    }, "⚡ Pré-remplir depuis une ligne du budget (optionnel) :"), /*#__PURE__*/React.createElement("select", {
      value: modalPreset,
      onChange: e => handleSelectPreset(e.target.value),
      style: {
        ...inputStyle,
        width: "100%",
        fontSize: 12.5,
        padding: "6px 8px",
        background: C?.panel || "#FFFFFF",
        cursor: "pointer"
      }
    }, /*#__PURE__*/React.createElement("option", {
      value: ""
    }, "— Saisie manuelle libre —"), /*#__PURE__*/React.createElement("optgroup", {
      label: "Charges récurrentes"
    }, budgetPresets.filter(p => p.kind === "charge").map(p => /*#__PURE__*/React.createElement("option", {
      key: p.key,
      value: p.key
    }, p.label))), /*#__PURE__*/React.createElement("optgroup", {
      label: "Revenus récurrents"
    }, budgetPresets.filter(p => p.kind === "income").map(p => /*#__PURE__*/React.createElement("option", {
      key: p.key,
      value: p.key
    }, p.label))), /*#__PURE__*/React.createElement("optgroup", {
      label: "Dépenses ponctuelles"
    }, budgetPresets.filter(p => p.kind === "oneoff").map(p => /*#__PURE__*/React.createElement("option", {
      key: p.key,
      value: p.key
    }, p.label))))), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 12
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        flex: 1
      }
    }, /*#__PURE__*/React.createElement("label", {
      style: {
        fontSize: 11.5,
        fontWeight: 700,
        color: C?.inkSoft || "#6B7278",
        display: "block",
        marginBottom: 4
      }
    }, "Type d'opération"), /*#__PURE__*/React.createElement("select", {
      value: modalData.type,
      onChange: e => setModalData({
        ...modalData,
        type: e.target.value
      }),
      style: {
        ...inputStyle,
        width: "100%",
        fontSize: 13,
        cursor: "pointer"
      }
    }, /*#__PURE__*/React.createElement("option", {
      value: "cheque"
    }, "🏷️ Chèque"), /*#__PURE__*/React.createElement("option", {
      value: "cb"
    }, "💳 CB différée"), /*#__PURE__*/React.createElement("option", {
      value: "virement"
    }, "🔄 Virement"), /*#__PURE__*/React.createElement("option", {
      value: "prelevement"
    }, "📄 Prélèvement"), /*#__PURE__*/React.createElement("option", {
      value: "autre"
    }, "🔘 Autre"))), /*#__PURE__*/React.createElement("div", {
      style: {
        flex: 1
      }
    }, /*#__PURE__*/React.createElement("label", {
      style: {
        fontSize: 11.5,
        fontWeight: 700,
        color: C?.inkSoft || "#6B7278",
        display: "block",
        marginBottom: 4
      }
    }, modalData.type === "cheque" ? "N° de Chèque (ex. 0004821)" : "Référence / N° (optionnel)"), /*#__PURE__*/React.createElement("input", {
      value: modalData.refNumber,
      onChange: e => setModalData({
        ...modalData,
        refNumber: e.target.value
      }),
      placeholder: modalData.type === "cheque" ? "ex. 0004821" : "ex. Réf 123",
      style: {
        ...inputStyle,
        width: "100%",
        fontSize: 13
      }
    }))), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("label", {
      style: {
        fontSize: 11.5,
        fontWeight: 700,
        color: C?.inkSoft || "#6B7278",
        display: "block",
        marginBottom: 4
      }
    }, "Bénéficiaire / Libellé ", /*#__PURE__*/React.createElement("span", {
      style: {
        color: C?.brick || "#A8503C"
      }
    }, "*")), /*#__PURE__*/React.createElement("input", {
      required: true,
      value: modalData.label,
      onChange: e => setModalData({
        ...modalData,
        label: e.target.value
      }),
      placeholder: "ex. Dr Martin, Peintre Toiture, Salaire...",
      style: {
        ...inputStyle,
        width: "100%",
        fontSize: 13
      }
    })), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 12,
        alignItems: "flex-end"
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        width: 150
      }
    }, /*#__PURE__*/React.createElement("label", {
      style: {
        fontSize: 11.5,
        fontWeight: 700,
        color: C?.inkSoft || "#6B7278",
        display: "block",
        marginBottom: 4
      }
    }, "Sens du flux"), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 7,
        overflow: "hidden"
      }
    }, /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => setModalData({
        ...modalData,
        isDebit: true
      }),
      style: {
        flex: 1,
        padding: "8px 4px",
        fontSize: 12,
        fontWeight: 700,
        border: "none",
        cursor: "pointer",
        background: modalData.isDebit ? C?.brick || "#A8503C" : C?.panelAlt || "#EFEAE0",
        color: modalData.isDebit ? "#fff" : C?.inkSoft || "#6B7278"
      }
    }, "Débit (-)"), /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => setModalData({
        ...modalData,
        isDebit: false
      }),
      style: {
        flex: 1,
        padding: "8px 4px",
        fontSize: 12,
        fontWeight: 700,
        border: "none",
        cursor: "pointer",
        background: !modalData.isDebit ? C?.pine || "#2F5D50" : C?.panelAlt || "#EFEAE0",
        color: !modalData.isDebit ? "#fff" : C?.inkSoft || "#6B7278"
      }
    }, "Crédit (+)"))), /*#__PURE__*/React.createElement("div", {
      style: {
        flex: 1
      }
    }, /*#__PURE__*/React.createElement("label", {
      style: {
        fontSize: 11.5,
        fontWeight: 700,
        color: C?.inkSoft || "#6B7278",
        display: "block",
        marginBottom: 4
      }
    }, "Montant (€) ", /*#__PURE__*/React.createElement("span", {
      style: {
        color: C?.brick || "#A8503C"
      }
    }, "*")), /*#__PURE__*/React.createElement("input", {
      required: true,
      type: "number",
      step: "0.01",
      min: "0.01",
      value: modalData.amount,
      onChange: e => setModalData({
        ...modalData,
        amount: e.target.value
      }),
      placeholder: "0.00",
      style: {
        ...inputStyle,
        width: "100%",
        fontSize: 14,
        fontFamily: "'IBM Plex Mono', monospace",
        fontWeight: 700
      }
    }))), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 12
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        flex: 1
      }
    }, /*#__PURE__*/React.createElement("label", {
      style: {
        fontSize: 11.5,
        fontWeight: 700,
        color: C?.inkSoft || "#6B7278",
        display: "block",
        marginBottom: 4
      }
    }, "Date d'émission / engagement ", /*#__PURE__*/React.createElement("span", {
      style: {
        color: C?.brick || "#A8503C"
      }
    }, "*")), /*#__PURE__*/React.createElement("input", {
      required: true,
      type: "date",
      value: modalData.date,
      onChange: e => setModalData({
        ...modalData,
        date: e.target.value
      }),
      style: {
        ...inputStyle,
        width: "100%",
        fontSize: 13
      }
    })), /*#__PURE__*/React.createElement("div", {
      style: {
        flex: 1
      }
    }, /*#__PURE__*/React.createElement("label", {
      style: {
        fontSize: 11.5,
        fontWeight: 700,
        color: C?.inkSoft || "#6B7278",
        display: "block",
        marginBottom: 4
      }
    }, "Date d'effet prévue (optionnelle)"), /*#__PURE__*/React.createElement("input", {
      type: "date",
      value: modalData.expectedDate,
      onChange: e => setModalData({
        ...modalData,
        expectedDate: e.target.value
      }),
      placeholder: "Laisser vide pour chèque",
      style: {
        ...inputStyle,
        width: "100%",
        fontSize: 13
      }
    }))), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("label", {
      style: {
        fontSize: 11.5,
        fontWeight: 700,
        color: C?.inkSoft || "#6B7278",
        display: "block",
        marginBottom: 4
      }
    }, "Catégorie bancaire"), /*#__PURE__*/React.createElement("select", {
      value: modalData.categoryId,
      onChange: e => setModalData({
        ...modalData,
        categoryId: e.target.value
      }),
      style: {
        ...inputStyle,
        width: "100%",
        fontSize: 13,
        cursor: "pointer"
      }
    }, /*#__PURE__*/React.createElement("option", {
      value: ""
    }, "— Non catégorisé —"), sortedCategories.map(c => /*#__PURE__*/React.createElement("option", {
      key: c.id,
      value: c.id
    }, c.label)))), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("label", {
      style: {
        fontSize: 11.5,
        fontWeight: 700,
        color: C?.inkSoft || "#6B7278",
        display: "block",
        marginBottom: 4
      }
    }, "Notes & Détails (optionnel)"), /*#__PURE__*/React.createElement("input", {
      value: modalData.notes,
      onChange: e => setModalData({
        ...modalData,
        notes: e.target.value
      }),
      placeholder: "ex. Échéance n°2/3...",
      style: {
        ...inputStyle,
        width: "100%",
        fontSize: 12.5
      }
    })), /*#__PURE__*/React.createElement("div", {
      style: {
        marginTop: 6,
        padding: 12,
        borderRadius: 8,
        background: C?.panelAlt || "#EFEAE0",
        border: `1px solid ${C?.line || "#DED6C4"}`
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        marginBottom: 8
      }
    }, /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 12,
        fontWeight: 700,
        color: C?.navy || "#28394A"
      }
    }, "✂ Ventilation multi-catégories (optionnel) :"), /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => {
        const generateId = typeof uid === 'function' ? uid : () => `split_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`;
        setModalData(prev => ({
          ...prev,
          splits: [...(prev.splits || []), { id: generateId(), label: "", categoryId: "", amount: "" }]
        }));
      },
      style: {
        padding: "4px 8px",
        fontSize: 11,
        fontWeight: 700,
        borderRadius: 6,
        background: C?.pine || "#2F5D50",
        color: "#fff",
        border: "none",
        cursor: "pointer"
      }
    }, "➕ Ajouter sous-ligne")), modalData.splits && modalData.splits.length > 0 && /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        flexDirection: "column",
        gap: 8
      }
    }, modalData.splits.map((sp, sIdx) => /*#__PURE__*/React.createElement("div", {
      key: sp.id || sIdx,
      style: {
        display: "flex",
        gap: 6,
        alignItems: "center"
      }
    }, /*#__PURE__*/React.createElement("select", {
      value: sp.categoryId || "",
      onChange: e => {
        const next = [...modalData.splits];
        next[sIdx].categoryId = e.target.value;
        setModalData({ ...modalData, splits: next });
      },
      style: { ...inputStyle, width: 140, fontSize: 11.5, padding: "4px 6px" }
    }, /*#__PURE__*/React.createElement("option", { value: "" }, "— Catégorie —"), sortedCategories.map(c => /*#__PURE__*/React.createElement("option", { key: c.id, value: c.id }, c.label))), /*#__PURE__*/React.createElement("input", {
      placeholder: "Sous-libellé (opt.)",
      value: sp.label || "",
      onChange: e => {
        const next = [...modalData.splits];
        next[sIdx].label = e.target.value;
        setModalData({ ...modalData, splits: next });
      },
      style: { ...inputStyle, flex: 1, fontSize: 11.5, padding: "4px 6px" }
    }), /*#__PURE__*/React.createElement("input", {
      type: "number",
      step: "0.01",
      placeholder: "Montant (€)",
      value: sp.amount || "",
      onChange: e => {
        const next = [...modalData.splits];
        next[sIdx].amount = e.target.value;
        setModalData({ ...modalData, splits: next });
      },
      style: { ...inputStyle, width: 90, fontSize: 11.5, padding: "4px 6px", fontFamily: "'IBM Plex Mono', monospace" }
    }), /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => {
        const next = modalData.splits.filter((_, idx) => idx !== sIdx);
        setModalData({ ...modalData, splits: next });
      },
      style: { background: "none", border: "none", cursor: "pointer", color: C?.brick || "#A8503C", fontSize: 14 },
      title: "Supprimer la sous-ligne"
    }, "🗑️"))), (() => {
      const totalNum = parseFloat(String(modalData.amount).replace(",", ".")) || 0;
      const sumSplits = modalData.splits.reduce((s, sp) => s + (parseFloat(String(sp.amount).replace(",", ".")) || 0), 0);
      const rem = Math.round((totalNum - sumSplits) * 100) / 100;
      return /*#__PURE__*/React.createElement("div", {
        style: {
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          fontSize: 11,
          marginTop: 4,
          padding: "4px 8px",
          background: "#fff",
          borderRadius: 6,
          border: `1px solid ${C?.line || "#DED6C4"}`
        }
      }, /*#__PURE__*/React.createElement("span", null, "Ventilations : ", /*#__PURE__*/React.createElement("strong", null, sumSplits.toFixed(2), " €"), " / ", totalNum.toFixed(2), " €"), Math.abs(rem) > 0.001 ? /*#__PURE__*/React.createElement("span", {
        style: { color: rem > 0 ? C?.brick || "#A8503C" : "#2563EB", fontWeight: 700 }
      }, "Reste: ", rem > 0 ? `+${rem.toFixed(2)}` : rem.toFixed(2), " €", /*#__PURE__*/React.createElement("button", {
        type: "button",
        onClick: () => {
          if (modalData.splits.length > 0) {
            const next = [...modalData.splits];
            const lastIdx = next.length - 1;
            const curLastAmt = parseFloat(String(next[lastIdx].amount).replace(",", ".")) || 0;
            next[lastIdx].amount = (curLastAmt + rem).toFixed(2);
            setModalData({ ...modalData, splits: next });
          }
        },
        style: { marginLeft: 6, padding: "2px 6px", fontSize: 10, fontWeight: 700, borderRadius: 4, background: C?.pine || "#2F5D50", color: "#fff", border: "none", cursor: "pointer" }
      }, "🪄 Ajuster")) : /*#__PURE__*/React.createElement("span", {
        style: { color: C?.pine || "#2F5D50", fontWeight: 700 }
      }, "✓ Équilibré !"));
    })())), /*#__PURE__*/React.createElement("div", {
      style: {
        marginTop: 10,
        display: "flex",
        gap: 10,
        justifyContent: "flex-end"
      }
    }, /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => setShowAddModal(false),
      style: {
        padding: "8px 16px",
        borderRadius: 8,
        fontSize: 13,
        fontWeight: 600,
        background: C?.panelAlt || "#EFEAE0",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        color: C?.inkSoft || "#6B7278",
        cursor: "pointer"
      }
    }, "Annuler"), /*#__PURE__*/React.createElement("button", {
      type: "submit",
      style: {
        padding: "8px 20px",
        borderRadius: 8,
        fontSize: 13,
        fontWeight: 700,
        background: C?.pine || "#2F5D50",
        color: "#fff",
        border: "none",
        cursor: "pointer"
      }
    }, editingOp ? "Enregistrer les modifications" : "Valider l'opération"))))), matchingOp && /*#__PURE__*/React.createElement("div", {
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
        zIndex: 1200,
        padding: 16
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 14,
        width: "100%",
        maxWidth: 680,
        maxHeight: "88vh",
        display: "flex",
        flexDirection: "column",
        boxShadow: "0 12px 36px rgba(0,0,0,0.3)",
        overflow: "hidden"
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        padding: "16px 20px",
        borderBottom: `1px solid ${C?.line || "#DED6C4"}`,
        background: C?.pineSoft || "#E3ECE8",
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center"
      }
    }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontWeight: 700,
        fontSize: 15,
        color: C?.pine || "#2F5D50"
      }
    }, "🔗 Rapprocher : ", matchingOp.label), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        marginTop: 2
      }
    }, "Émis le ", matchingOp.date ? matchingOp.date.split("-").reverse().join("/") : "—", " • Montant prévu : ", /*#__PURE__*/React.createElement("strong", null, eurExact(matchingOp.amount)), matchingOp.refNumber && ` • N° #${matchingOp.refNumber}`)), /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => setMatchingOp(null),
      style: {
        background: "none",
        border: "none",
        fontSize: 20,
        cursor: "pointer",
        color: C?.inkSoft || "#6B7278"
      }
    }, "✕")), /*#__PURE__*/React.createElement("div", {
      style: {
        padding: "12px 20px",
        borderBottom: `1px solid ${C?.line || "#DED6C4"}`,
        background: C?.panelAlt || "#EFEAE0"
      }
    }, /*#__PURE__*/React.createElement("input", {
      value: matchingSearch,
      onChange: e => setMatchingSearch(e.target.value),
      placeholder: "🔍 Rechercher dans les transactions bancaires importées…",
      style: {
        ...inputStyle,
        width: "100%",
        fontSize: 13
      }
    })), /*#__PURE__*/React.createElement("div", {
      style: {
        flex: 1,
        overflowY: "auto",
        padding: 14
      }
    }, candidateTxs.length === 0 ? /*#__PURE__*/React.createElement("div", {
      style: {
        padding: 30,
        textAlign: "center",
        color: C?.inkSoft || "#6B7278",
        fontSize: 13
      }
    }, "Aucune transaction bancaire disponible ne correspond à votre recherche.") : /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        flexDirection: "column",
        gap: 8
      }
    }, candidateTxs.map(tx => /*#__PURE__*/React.createElement("div", {
      key: tx.id,
      style: {
        padding: "11px 14px",
        borderRadius: 8,
        background: tx.isSuggested ? "#F0FDF4" : C?.panel || "#FFFFFF",
        border: `1px solid ${tx.isSuggested ? "#86EFAC" : C?.line || "#DED6C4"}`,
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: 12
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        flex: 1,
        minWidth: 0
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        alignItems: "center",
        gap: 8,
        marginBottom: 3
      }
    }, /*#__PURE__*/React.createElement("span", {
      style: {
        fontFamily: "'IBM Plex Mono', monospace",
        fontSize: 11.5,
        color: C?.inkSoft || "#6B7278",
        fontWeight: 600
      }
    }, tx.date ? tx.date.split("-").reverse().join("/") : "—"), tx.isSuggested && /*#__PURE__*/React.createElement("span", {
      style: {
        padding: "1px 6px",
        borderRadius: 6,
        fontSize: 10.5,
        fontWeight: 700,
        background: "#DCFCE7",
        color: "#15803D"
      }
    }, "🎯 Correspondance suggérée")), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12.5,
        fontWeight: 600,
        color: C?.ink || "#232A2E",
        overflow: "hidden",
        textOverflow: "ellipsis",
        whiteSpace: "nowrap"
      },
      title: tx.label
    }, tx.label)), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        alignItems: "center",
        gap: 12,
        flexShrink: 0
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontFamily: "'IBM Plex Mono', monospace",
        fontSize: 14,
        fontWeight: 700,
        color: tx.amount < 0 ? C?.brick || "#A8503C" : C?.pine || "#2F5D50"
      }
    }, eurExact(tx.amount)), /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => handleLinkTransaction(tx),
      style: {
        padding: "6px 12px",
        borderRadius: 7,
        fontSize: 12,
        fontWeight: 700,
        background: C?.pine || "#2F5D50",
        color: "#fff",
        border: "none",
        cursor: "pointer"
      }
    }, "Associer")))))), /*#__PURE__*/React.createElement("div", {
      style: {
        padding: "12px 20px",
        borderTop: `1px solid ${C?.line || "#DED6C4"}`,
        background: C?.panelAlt || "#EFEAE0",
        display: "flex",
        justifyContent: "flex-end"
      }
    }, /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => setMatchingOp(null),
      style: {
        padding: "7px 16px",
        borderRadius: 7,
        fontSize: 12.5,
        fontWeight: 600,
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        color: C?.inkSoft || "#6B7278",
        cursor: "pointer"
      }
    }, "Fermer")))), showImportModal && /*#__PURE__*/React.createElement("div", {
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
        zIndex: 1100,
        padding: 16
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 14,
        width: "100%",
        maxWidth: 820,
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
        background: C?.pineSoft || "#E3ECE8",
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center"
      }
    }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontWeight: 700,
        fontSize: 16,
        color: C?.pine || "#2F5D50"
      }
    }, "📥 Import des encours de CB différées"), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        marginTop: 2
      }
    }, "Importez vos débits CB différés en cours. Les règles de catégorisation et la déduplication exacte sont appliquées automatiquement.")), /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: resetImportModal,
      style: {
        background: "none",
        border: "none",
        fontSize: 20,
        cursor: "pointer",
        color: C?.inkSoft || "#6B7278"
      }
    }, "✕")), /*#__PURE__*/React.createElement("div", {
      style: {
        padding: 20,
        overflowY: "auto",
        display: "flex",
        flexDirection: "column",
        gap: 16
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 16,
        flexWrap: "wrap",
        alignItems: "center",
        background: C?.panelAlt || "#EFEAE0",
        padding: "12px 16px",
        borderRadius: 8
      }
    }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11.5,
        color: C?.inkSoft || "#6B7278",
        marginBottom: 4,
        fontWeight: 600
      }
    }, "Séparateur"), /*#__PURE__*/React.createElement("select", {
      value: importDelimiter,
      onChange: e => setImportDelimiter(e.target.value),
      style: {
        ...inputStyle,
        width: 150,
        fontSize: 12,
        cursor: "pointer"
      }
    }, /*#__PURE__*/React.createElement("option", {
      value: ";"
    }, "Point-virgule ( ; )"), /*#__PURE__*/React.createElement("option", {
      value: ","
    }, "Virgule ( , )"), /*#__PURE__*/React.createElement("option", {
      value: "\t"
    }, "Tabulation"))), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11.5,
        color: C?.inkSoft || "#6B7278",
        marginBottom: 4,
        fontWeight: 600
      }
    }, "Format de date"), /*#__PURE__*/React.createElement("select", {
      value: importDateFormat,
      onChange: e => setImportDateFormat(e.target.value),
      style: {
        ...inputStyle,
        width: 140,
        fontSize: 12,
        cursor: "pointer"
      }
    }, ["DD-MM-YYYY", "DD/MM/YYYY", "YYYY-MM-DD", "MM/DD/YYYY"].map(f => /*#__PURE__*/React.createElement("option", {
      key: f,
      value: f
    }, f)))), /*#__PURE__*/React.createElement("label", {
      style: {
        display: "flex",
        alignItems: "center",
        gap: 6,
        cursor: "pointer",
        fontSize: 12,
        color: C?.ink || "#232A2E",
        marginTop: 18
      }
    }, /*#__PURE__*/React.createElement("input", {
      type: "checkbox",
      checked: importUsePurchaseDate,
      onChange: e => setImportUsePurchaseDate(e.target.checked)
    }), /*#__PURE__*/React.createElement("span", {
      title: "Extrait la date d'achat du libellé (ex: FACTURE CARTE DU 290726) comme date d'émission"
    }, "Extraire la date d'achat du libellé"))), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        alignItems: "center",
        gap: 12
      }
    }, /*#__PURE__*/React.createElement("label", {
      style: {
        display: "inline-flex",
        alignItems: "center",
        gap: 8,
        padding: "9px 16px",
        borderRadius: 8,
        border: `1px solid ${C?.pine || "#2F5D50"}`,
        cursor: "pointer",
        fontSize: 13,
        fontWeight: 600,
        color: "#fff",
        background: C?.pine || "#2F5D50"
      }
    }, "📄 Choisir le fichier d'encours CB…", /*#__PURE__*/React.createElement("input", {
      type: "file",
      accept: ".csv,.txt",
      style: {
        display: "none"
      },
      onChange: e => handleImportFile(e.target.files[0])
    })), importFileName && /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 12.5,
        fontWeight: 600,
        color: C?.navy || "#28394A"
      }
    }, importFileName)), importRawRows && importColRoles && /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        marginBottom: 8
      }
    }, "Vérifiez les rôles des colonnes détectées (aperçu des 5 premières lignes) :"), /*#__PURE__*/React.createElement("div", {
      style: {
        overflowX: "auto",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 8
      }
    }, /*#__PURE__*/React.createElement("table", {
      style: {
        width: "100%",
        borderCollapse: "collapse",
        fontSize: 12
      }
    }, /*#__PURE__*/React.createElement("thead", null, /*#__PURE__*/React.createElement("tr", null, importColRoles.map((role, i) => /*#__PURE__*/React.createElement("th", {
      key: i,
      style: {
        padding: "6px 8px",
        borderBottom: `1px solid ${C?.line || "#DED6C4"}`,
        background: C?.panelAlt || "#EFEAE0"
      }
    }, /*#__PURE__*/React.createElement("select", {
      value: role,
      onChange: e => setImportRole(i, e.target.value),
      style: {
        fontSize: 11.5,
        padding: "3px 4px",
        borderRadius: 4,
        border: `1px solid ${C?.line || "#DED6C4"}`,
        cursor: "pointer",
        width: "100%"
      }
    }, [{
      v: "date",
      l: "Date"
    }, {
      v: "label",
      l: "Libellé"
    }, {
      v: "amount",
      l: "Montant"
    }, {
      v: "ignore",
      l: "Ignorer"
    }].map(opt => /*#__PURE__*/React.createElement("option", {
      key: opt.v,
      value: opt.v
    }, opt.l))))))), /*#__PURE__*/React.createElement("tbody", null, importRawRows.slice(0, 5).map((row, ri) => /*#__PURE__*/React.createElement("tr", {
      key: ri,
      style: {
        borderBottom: `1px solid ${C?.line || "#DED6C4"}`
      }
    }, importColRoles.map((role, ci) => /*#__PURE__*/React.createElement("td", {
      key: ci,
      style: {
        padding: "6px 8px",
        color: role === "ignore" ? C?.inkSoft || "#6B7278" : C?.ink || "#232A2E",
        fontStyle: role === "ignore" ? "italic" : "normal"
      }
    }, row[ci]))))))), /*#__PURE__*/React.createElement("div", {
      style: {
        marginTop: 12,
        display: "flex",
        gap: 10,
        alignItems: "center"
      }
    }, /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: doImportPending,
      style: {
        padding: "9px 18px",
        borderRadius: 8,
        fontSize: 13,
        fontWeight: 700,
        background: C?.pine || "#2F5D50",
        color: "#fff",
        border: "none",
        cursor: "pointer"
      }
    }, "✓ Importer les ", importRawRows.length, " opérations CB"), /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => {
        setImportRawRows(null);
        setImportColRoles(null);
        setImportFileName("");
      },
      style: {
        padding: "8px 14px",
        borderRadius: 8,
        fontSize: 12,
        background: C?.panelAlt || "#EFEAE0",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        color: C?.inkSoft || "#6B7278",
        cursor: "pointer"
      }
    }, "Annuler"))), importSummary && /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12.5,
        padding: "12px 16px",
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
    }, /*#__PURE__*/React.createElement("div", null, importSummary.error || `✅ ${importSummary.imported} opération(s) importée(s), ${importSummary.duplicates} doublon(s) ignoré(s), ${importSummary.autoCategorized} catégorisée(s) automatiquement selon vos règles.`), importSummary.duplicateCandidates && importSummary.duplicateCandidates.length > 0 && /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => setShowDuplicateModal(true),
      style: {
        padding: "5px 12px",
        borderRadius: 6,
        fontSize: 12,
        fontWeight: 700,
        cursor: "pointer",
        background: C?.gold || "#C99700",
        color: "#fff",
        border: "none"
      }
    }, "🔀 Revoir les ", importSummary.duplicateCandidates.length, " correspondance(s) manuelle(s)"), importSummary.ignoredDuplicates && importSummary.ignoredDuplicates.length > 0 && /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => setShowIgnoredModal(true),
      style: {
        padding: "5px 12px",
        borderRadius: 6,
        fontSize: 12,
        fontWeight: 700,
        cursor: "pointer",
        background: C?.pine || "#2F5D50",
        color: "#fff",
        border: "none"
      }
    }, "🔍 Revoir les ", importSummary.duplicates, " doublon(s) ignoré(s)"))), /*#__PURE__*/React.createElement("div", {
      style: {
        padding: "12px 20px",
        borderTop: `1px solid ${C?.line || "#DED6C4"}`,
        background: C?.panelAlt || "#EFEAE0",
        display: "flex",
        justifyContent: "flex-end"
      }
    }, /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: resetImportModal,
      style: {
        padding: "8px 18px",
        borderRadius: 8,
        fontSize: 13,
        fontWeight: 600,
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        color: C?.inkSoft || "#6B7278",
        cursor: "pointer"
      }
    }, "Fermer")))), showIgnoredModal && importSummary?.ignoredDuplicates && /*#__PURE__*/React.createElement("div", {
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
        zIndex: 1200,
        padding: 20
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 14,
        width: "100%",
        maxWidth: 780,
        maxHeight: "85vh",
        display: "flex",
        flexDirection: "column",
        boxShadow: "0 12px 36px rgba(0,0,0,0.3)",
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
    }, "🔍 Opérations CB ignorées (Doublons détectés)"), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        marginTop: 2
      }
    }, importSummary.ignoredDuplicates.length, " opération(s) déjà présente(s) dans vos en-cours.")), /*#__PURE__*/React.createElement("button", {
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
    }, /*#__PURE__*/React.createElement("table", {
      style: {
        width: "100%",
        borderCollapse: "collapse",
        fontSize: 12
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
        fontSize: 11
      }
    }, "Date"), /*#__PURE__*/React.createElement("th", {
      style: {
        padding: "8px 10px",
        textAlign: "left",
        fontSize: 11
      }
    }, "Libellé"), /*#__PURE__*/React.createElement("th", {
      style: {
        padding: "8px 10px",
        textAlign: "right",
        fontSize: 11
      }
    }, "Montant"), /*#__PURE__*/React.createElement("th", {
      style: {
        padding: "8px 10px",
        textAlign: "center",
        fontSize: 11
      }
    }, "Action"))), /*#__PURE__*/React.createElement("tbody", null, importSummary.ignoredDuplicates.map(op => /*#__PURE__*/React.createElement("tr", {
      key: op.id,
      style: {
        borderBottom: `1px solid ${C?.line || "#DED6C4"}`
      }
    }, /*#__PURE__*/React.createElement("td", {
      style: {
        padding: "8px 10px",
        fontFamily: "'IBM Plex Mono', monospace",
        color: C?.inkSoft || "#6B7278"
      }
    }, op.date ? op.date.split("-").reverse().join("/") : "—"), /*#__PURE__*/React.createElement("td", {
      style: {
        padding: "8px 10px",
        fontWeight: 600,
        color: C?.ink || "#232A2E"
      }
    }, op.label), /*#__PURE__*/React.createElement("td", {
      style: {
        padding: "8px 10px",
        textAlign: "right",
        fontFamily: "'IBM Plex Mono', monospace",
        fontWeight: 700,
        color: op.amount < 0 ? C?.brick || "#A8503C" : C?.pine || "#2F5D50"
      }
    }, eurExact(op.amount)), /*#__PURE__*/React.createElement("td", {
      style: {
        padding: "8px 10px",
        textAlign: "center"
      }
    }, /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => forceImportPendingDuplicate(op),
      style: {
        padding: "4px 10px",
        borderRadius: 6,
        fontSize: 11,
        fontWeight: 700,
        background: C?.pine || "#2F5D50",
        color: "#fff",
        border: "none",
        cursor: "pointer"
      }
    }, "➕ Forcer l'import"))))))), /*#__PURE__*/React.createElement("div", {
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
        padding: "7px 16px",
        borderRadius: 7,
        fontSize: 12.5,
        fontWeight: 600,
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        color: C?.inkSoft || "#6B7278",
        cursor: "pointer"
      }
    }, "Fermer")))), showDuplicateModal && importSummary?.duplicateCandidates && importSummary.duplicateCandidates.length > 0 && /*#__PURE__*/React.createElement("div", {
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
        zIndex: 1200,
        padding: 20
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 14,
        width: "100%",
        maxWidth: 880,
        maxHeight: "88vh",
        display: "flex",
        flexDirection: "column",
        boxShadow: "0 12px 36px rgba(0,0,0,0.3)",
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
    }, "🔀 Fusion des saisies manuelles (Doublons potentiels)"), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        marginTop: 2
      }
    }, "Des opérations manuelles existantes correspondent à ±1 jour et ±10 € aux lignes de votre relevé. Choisissez l'action pour chaque opération.")), /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => setShowDuplicateModal(false),
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
        padding: 16,
        display: "flex",
        flexDirection: "column",
        gap: 14
      }
    }, importSummary.duplicateCandidates.map((dc, idx) => {
      const incoming = dc.incomingOp;
      const manuals = dc.matchingManualOps || [];
      return /*#__PURE__*/React.createElement("div", {
        key: incoming.id || idx,
        style: {
          border: `1px solid ${C?.line || "#DED6C4"}`,
          borderRadius: 10,
          background: C?.panel || "#FFFFFF",
          padding: 14,
          display: "flex",
          flexDirection: "column",
          gap: 10
        }
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          background: C?.pineSoft || "#E3ECE8",
          padding: "8px 12px",
          borderRadius: 6
        }
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          display: "flex",
          gap: 12,
          alignItems: "center"
        }
      }, /*#__PURE__*/React.createElement("span", {
        style: {
          fontSize: 11,
          fontWeight: 700,
          background: C?.pine || "#2F5D50",
          color: "#fff",
          padding: "2px 6px",
          borderRadius: 4
        }
      }, "RELEVÉ BANCAIRE"), /*#__PURE__*/React.createElement("span", {
        style: {
          fontFamily: "'IBM Plex Mono', monospace",
          fontSize: 12,
          color: C?.inkSoft || "#6B7278"
        }
      }, incoming.date ? incoming.date.split("-").reverse().join("/") : "—"), /*#__PURE__*/React.createElement("span", {
        style: {
          fontWeight: 700,
          fontSize: 13,
          color: C?.ink || "#232A2E"
        }
      }, incoming.label)), /*#__PURE__*/React.createElement("div", {
        style: {
          fontFamily: "'IBM Plex Mono', monospace",
          fontWeight: 700,
          fontSize: 14,
          color: incoming.amount < 0 ? C?.brick || "#A8503C" : C?.pine || "#2F5D50"
        }
      }, eurExact(incoming.amount))), /*#__PURE__*/React.createElement("div", {
        style: {
          fontSize: 11.5,
          fontWeight: 600,
          color: C?.inkSoft || "#6B7278",
          marginLeft: 4
        }
      }, "Correspondance(s) manuelle(s) détectée(s) :"), /*#__PURE__*/React.createElement("div", {
        style: {
          display: "flex",
          flexDirection: "column",
          gap: 8
        }
      }, manuals.map(mOp => {
        const cat = categories.find(c => c.id === mOp.categoryId);
        return /*#__PURE__*/React.createElement("div", {
          key: mOp.id,
          style: {
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            background: C?.panelAlt || "#EFEAE0",
            padding: "8px 12px",
            borderRadius: 6,
            border: `1px solid ${C?.line || "#DED6C4"}`
          }
        }, /*#__PURE__*/React.createElement("div", {
          style: {
            display: "flex",
            gap: 12,
            alignItems: "center"
          }
        }, /*#__PURE__*/React.createElement("span", {
          style: {
            fontSize: 11,
            fontWeight: 700,
            background: C?.navy || "#28394A",
            color: "#fff",
            padding: "2px 6px",
            borderRadius: 4
          }
        }, "SAISIE MANUELLE"), /*#__PURE__*/React.createElement("span", {
          style: {
            fontFamily: "'IBM Plex Mono', monospace",
            fontSize: 12,
            color: C?.inkSoft || "#6B7278"
          }
        }, mOp.date ? mOp.date.split("-").reverse().join("/") : "—"), /*#__PURE__*/React.createElement("span", {
          style: {
            fontWeight: 600,
            fontSize: 12.5,
            color: C?.ink || "#232A2E"
          }
        }, mOp.label), cat && /*#__PURE__*/React.createElement("span", {
          style: {
            fontSize: 11,
            background: C?.panel || "#FFFFFF",
            padding: "2px 6px",
            borderRadius: 4,
            border: `1px solid ${C?.line || "#DED6C4"}`,
            color: C?.inkSoft || "#6B7278"
          }
        }, "📁 ", cat.label), /*#__PURE__*/React.createElement("span", {
          style: {
            fontFamily: "'IBM Plex Mono', monospace",
            fontWeight: 700,
            fontSize: 13,
            color: mOp.amount < 0 ? C?.brick || "#A8503C" : C?.pine || "#2F5D50"
          }
        }, eurExact(mOp.amount))), /*#__PURE__*/React.createElement("button", {
          type: "button",
          onClick: () => handleMergeCandidate(mOp.id, incoming),
          style: {
            padding: "5px 12px",
            borderRadius: 6,
            fontSize: 11.5,
            fontWeight: 700,
            cursor: "pointer",
            background: C?.pine || "#2F5D50",
            color: "#fff",
            border: "none",
            whiteSpace: "nowrap"
          }
        }, "🔀 Fusionner (Garder catégorie)"));
      })), /*#__PURE__*/React.createElement("div", {
        style: {
          display: "flex",
          justifyContent: "flex-end",
          gap: 8,
          marginTop: 2
        }
      }, /*#__PURE__*/React.createElement("button", {
        type: "button",
        onClick: () => handleImportCandidateSeparately(incoming),
        style: {
          padding: "5px 10px",
          borderRadius: 6,
          fontSize: 11.5,
          fontWeight: 600,
          background: C?.panelAlt || "#EFEAE0",
          border: `1px solid ${C?.line || "#DED6C4"}`,
          color: C?.ink || "#232A2E",
          cursor: "pointer"
        }
      }, "➕ Importer comme nouvelle"), /*#__PURE__*/React.createElement("button", {
        type: "button",
        onClick: () => handleIgnoreCandidate(incoming),
        style: {
          padding: "5px 10px",
          borderRadius: 6,
          fontSize: 11.5,
          fontWeight: 600,
          background: C?.brickSoft || "#F4E4DF",
          border: `1px solid ${C?.brick || "#A8503C"}`,
          color: C?.brick || "#A8503C",
          cursor: "pointer"
        }
      }, "✕ Ignorer / Retirer")));
    })), /*#__PURE__*/React.createElement("div", {
      style: {
        padding: "12px 20px",
        borderTop: `1px solid ${C?.line || "#DED6C4"}`,
        background: C?.panelAlt || "#EFEAE0",
        display: "flex",
        justifyContent: "flex-end"
      }
    }, /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => setShowDuplicateModal(false),
      style: {
        padding: "7px 16px",
        borderRadius: 7,
        fontSize: 12.5,
        fontWeight: 600,
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        color: C?.inkSoft || "#6B7278",
        cursor: "pointer"
      }
    }, "Fermer")))));
  }
  exports.PendingOperationsView = PendingOperationsView;
  exports.OP_TYPES = OP_TYPES;
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);