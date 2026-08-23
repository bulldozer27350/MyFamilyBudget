/**
 * Gestionnaire d'état centralisé, persistance localStorage et synchronisation inter-pages
 */
(function (exports) {
  'use strict';

  const STORAGE_KEY = exports.STORAGE_KEY || "budget_familial_data_v1";
  const CONSTANT_EUROS_KEY = "budget_familial_constant_euros";

  // Canal Broadcast pour la synchronisation multi-onglets / multi-fenêtres
  let broadcastChannel = null;
  try {
    if (typeof BroadcastChannel !== "undefined") {
      broadcastChannel = new BroadcastChannel("budget_familial_sync_channel");
    }
  } catch (e) {
    console.warn("BroadcastChannel non supporté", e);
  }

  // Événements d'écoute pour les changements d'état
  const listeners = new Set();
  let state = null;
  let saveTimeout = null;
  let statusMessage = "";
  function getNormalizeFn() {
    return exports.normalizeData || window.BudgetApp && window.BudgetApp.normalizeData || (d => d);
  }
  function getDefaultData() {
    return exports.DEFAULT_DATA || window.BudgetApp && window.BudgetApp.DEFAULT_DATA || {};
  }
  function loadInitialData() {
    if (state !== null) return state;
    const normalize = getNormalizeFn();
    const defaultData = getDefaultData();
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      state = normalize(raw ? JSON.parse(raw) : defaultData);
    } catch (err) {
      console.error("Erreur de chargement localStorage :", err);
      state = defaultData;
    }
    return state;
  }
  function notifyListeners() {
    listeners.forEach(listener => {
      try {
        listener(state);
      } catch (e) {
        console.error(e);
      }
    });
  }
  function scheduleSave() {
    if (saveTimeout) clearTimeout(saveTimeout);
    saveTimeout = setTimeout(() => {
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
        statusMessage = "Enregistré dans ce navigateur";
        if (broadcastChannel) {
          broadcastChannel.postMessage({
            type: "DATA_UPDATED",
            data: state
          });
        }
      } catch (err) {
        statusMessage = "Échec de l'enregistrement";
        console.error("Erreur de sauvegarde localStorage :", err);
      }
      notifyListeners();
    }, 300);
  }

  // Écoute des modifications externes (autres onglets via storage event)
  if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
    window.addEventListener("storage", e => {
      if (e.key === STORAGE_KEY && e.newValue) {
        try {
          const normalize = getNormalizeFn();
          state = normalize(JSON.parse(e.newValue));
          statusMessage = "Mis à jour depuis un autre onglet";
          notifyListeners();
        } catch (err) {
          console.error("Erreur lors de la synchro storage event :", err);
        }
      }
    });
    if (broadcastChannel) {
      broadcastChannel.onmessage = event => {
        if (event.data && event.data.type === "DATA_UPDATED" && event.data.data) {
          state = event.data.data;
          statusMessage = "Mis à jour (sync)";
          notifyListeners();
        }
      };
    }
  }

  // API Store
  const BudgetStore = {
    getData() {
      return loadInitialData();
    },
    getStatus() {
      return statusMessage;
    },
    setData(newData) {
      const normalize = getNormalizeFn();
      state = normalize(newData);
      scheduleSave();
      notifyListeners();
    },
    update(key, fn) {
      const cur = loadInitialData();
      state = {
        ...cur,
        [key]: fn(cur[key])
      };
      scheduleSave();
      notifyListeners();
    },
    updatePath(path, fn) {
      const cur = loadInitialData();
      const keys = path.split(".");
      const clone = {
        ...cur
      };
      let cursor = clone;
      for (let i = 0; i < keys.length - 1; i++) {
        cursor[keys[i]] = {
          ...cursor[keys[i]]
        };
        cursor = cursor[keys[i]];
      }
      cursor[keys[keys.length - 1]] = fn(cursor[keys[keys.length - 1]]);
      state = clone;
      scheduleSave();
      notifyListeners();
    },
    setCell(path) {
      return (id, field, value) => {
        this.updatePath(path, list => (list || []).map(r => r.id === id ? {
          ...r,
          [field]: value
        } : r));
      };
    },
    addRow(path, factory) {
      this.updatePath(path, list => [...(list || []), factory()]);
    },
    removeRow(path) {
      return id => {
        this.updatePath(path, list => (list || []).filter(r => r.id !== id));
      };
    },
    exportJSON() {
      const data = this.getData();
      const blob = new Blob([JSON.stringify(data, null, 2)], {
        type: "application/json"
      });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "budget-familial.json";
      a.click();
      URL.revokeObjectURL(url);
    },
    importJSON(file, callback) {
      if (!file) return;
      const reader = new FileReader();
      reader.onload = ev => {
        try {
          const parsed = JSON.parse(ev.target.result);
          this.setData(parsed);
          if (typeof fetch !== "undefined") {
            const apiBase = window.API_BASE_URL || 'http://localhost:8080/api/v1';
            fetch(apiBase + "/budget/import", {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify(parsed)
            }).catch(() => {});
          }
          if (callback) callback(true, "Données importées avec succès");
        } catch (err) {
          alert("Fichier JSON invalide.");
          if (callback) callback(false, "Erreur de format JSON");
        }
      };
      reader.readAsText(file);
    },
    resetData() {
      if (typeof window !== "undefined" && window.confirm("Réinitialiser toutes les données aux valeurs d'exemple ?")) {
        const defaultData = getDefaultData();
        this.setData(defaultData);
        if (typeof fetch !== "undefined") {
          const apiBase = window.API_BASE_URL || 'http://localhost:8080/api/v1';
          fetch(apiBase + "/budget/reset", { method: "POST" }).catch(() => {});
        }
      }
    },
    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    }
  };

  /**
   * Hook React principal pour se connecter au store.
   * Inclut également les projections financières calculées via useFinancialProjections.
   */
  function useBudgetStore() {
    const {
      useState,
      useEffect,
      useCallback
    } = React;
    const [data, setDataState] = useState(() => BudgetStore.getData());
    const [status, setStatus] = useState(() => BudgetStore.getStatus());
    const [useConstantEuros, setUseConstantEurosState] = useState(() => {
      try {
        return localStorage.getItem(CONSTANT_EUROS_KEY) === "true";
      } catch {
        return false;
      }
    });
    useEffect(() => {
      const unsubscribe = BudgetStore.subscribe(newData => {
        setDataState({
          ...newData
        });
        setStatus(BudgetStore.getStatus());
      });
      return unsubscribe;
    }, []);
    const setUseConstantEuros = useCallback(val => {
      setUseConstantEurosState(val);
      try {
        localStorage.setItem(CONSTANT_EUROS_KEY, String(val));
      } catch (e) {}
    }, []);

    // Récupère le hook de projections depuis BudgetApp (chargé après calculations.js)
    const useProjections = typeof window !== 'undefined' ? window.BudgetApp && window.BudgetApp.useFinancialProjections || (() => ({})) : () => ({});
    const projections = useProjections(data, useConstantEuros);
    const update = useCallback((key, fn) => BudgetStore.update(key, fn), []);
    const updatePath = useCallback((path, fn) => BudgetStore.updatePath(path, fn), []);
    const setCell = useCallback(path => BudgetStore.setCell(path), []);
    const addRow = useCallback((path, factory) => BudgetStore.addRow(path, factory), []);
    const removeRow = useCallback(path => BudgetStore.removeRow(path), []);
    const exportJSON = useCallback(() => BudgetStore.exportJSON(), []);
    const importJSON = useCallback((file, cb) => BudgetStore.importJSON(file, cb), []);
    const resetData = useCallback(() => BudgetStore.resetData(), []);
    return {
      data,
      loaded: true,
      status,
      useConstantEuros,
      setUseConstantEuros,
      projections,
      update,
      updatePath,
      setCell,
      addRow,
      removeRow,
      exportJSON,
      importJSON,
      resetData
    };
  }
  exports.BudgetStore = BudgetStore;
  exports.useBudgetStore = useBudgetStore;
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);