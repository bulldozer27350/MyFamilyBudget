/**
 * Vue Paramètres (SettingsView : Paramètres généraux, Date Pivot, Virement Auto & Catégories d'Actifs)
 */
(function (exports) {
  'use strict';

  const {
    C,
    uid
  } = exports.C ? exports : window.BudgetApp || {};
  const {
    computePivotBalance,
    latestTransactionDate
  } = exports.computePivotBalance ? exports : window.BudgetApp || {};
  const {
    SectionCard,
    EditableTable
  } = exports.SectionCard ? exports : window.BudgetApp || {};
  const {
    HelpBadge
  } = exports.HelpBadge ? exports : window.BudgetApp || {};
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

  // Délai (ms) avant qu'une saisie clavier ne soit répercutée vers l'API des paramètres
  // (sauvegarde + rechargement complet des settings). Même valeur et même principe que le
  // debounce du composant Field générique (view/js/components/ui-base.js) : évite un appel
  // réseau à chaque frappe sur les champs texte/nombre/date de la vue Paramètres.
  const SETTINGS_FIELD_DEBOUNCE_MS = 500;

  // Champ texte/nombre/date debouncé : affiche la saisie localement en temps réel, et ne
  // répercute la valeur au parent (onChange) qu'après une pause de frappe, ou immédiatement
  // au blur. Reprend le même mécanisme que Field (ui-base.js) pour rester cohérent avec le
  // reste de l'application.
  function DebouncedInput({
    value,
    onChange,
    ...rest
  }) {
    const { useState, useEffect, useRef } = React;
    const [localValue, setLocalValue] = useState(value ?? "");
    const debounceTimerRef = useRef(null);
    const isTypingRef = useRef(false);

    // Garde le champ synchronisé si la valeur change depuis l'extérieur (rechargement des
    // settings, synchronisation...), sauf pendant que l'utilisateur est en train de taper.
    useEffect(() => {
      if (!isTypingRef.current) {
        setLocalValue(value ?? "");
      }
    }, [value]);

    useEffect(() => {
      return () => {
        if (debounceTimerRef.current) clearTimeout(debounceTimerRef.current);
      };
    }, []);

    function handleChange(e) {
      const v = e.target.value;
      setLocalValue(v);
      isTypingRef.current = true;
      if (debounceTimerRef.current) clearTimeout(debounceTimerRef.current);
      debounceTimerRef.current = setTimeout(() => {
        debounceTimerRef.current = null;
        isTypingRef.current = false;
        onChange(v);
      }, SETTINGS_FIELD_DEBOUNCE_MS);
    }

    function flush() {
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current);
        debounceTimerRef.current = null;
        isTypingRef.current = false;
        onChange(localValue);
      }
    }

    return /*#__PURE__*/React.createElement("input", {
      ...rest,
      value: localValue ?? "",
      onChange: handleChange,
      onBlur: flush
    });
  }

  function SettingsView({
    openHelp
  }) {
    const { useState, useEffect, useCallback } = React;
    const [settingsData, setSettingsData] = useState(null);
    const [loading, setLoading] = useState(true);

    // Charger les données via l'API asynchrone
    useEffect(() => {
      async function loadSettings() {
        try {
          const api = exports.BudgetApi || window.BudgetApp?.BudgetApi;
          if (api) {
            const data = await api.getSettings();
            setSettingsData(data);
          }
        } catch (error) {
          console.error("Erreur lors du chargement des paramètres:", error);
        } finally {
          setLoading(false);
        }
      }
      loadSettings();
    }, []);

    // S'abonner aux changements
    useEffect(() => {
      const api = exports.BudgetApi || window.BudgetApp?.BudgetApi;
      if (api) {
        const unsubscribe = api.onSettingsChanged(async () => {
          const data = await api.getSettings();
          setSettingsData(data);
        });
        return unsubscribe;
      }
    }, []);

    // Fonction pour mettre à jour un champ des settings
    const updateSettingsField = useCallback(async (field, value) => {
      const api = exports.BudgetApi || window.BudgetApp?.BudgetApi;
      if (api) {
        await api.updateSettingsField(field, value);
        // Recharger les données après la mise à jour
        const data = await api.getSettings();
        setSettingsData(data);
      }
    }, []);

    // Fonction pour mettre à jour une catégorie d'actif
    const updateAssetCategory = useCallback(async (id, field, value) => {
      const api = exports.BudgetApi || window.BudgetApp?.BudgetApi;
      if (api) {
        await api.updateAssetCategory(id, field, value);
        // Recharger les données après la mise à jour
        const data = await api.getSettings();
        setSettingsData(data);
      }
    }, []);

    // Fonction pour ajouter une catégorie d'actif
    const addAssetCategory = useCallback(async (row) => {
      const api = exports.BudgetApi || window.BudgetApp?.BudgetApi;
      if (api) {
        await api.addAssetCategory(row);
        // Recharger les données après l'ajout
        const data = await api.getSettings();
        setSettingsData(data);
      }
    }, []);

    // Fonction pour supprimer une catégorie d'actif
    const removeAssetCategory = useCallback(async (id) => {
      const api = exports.BudgetApi || window.BudgetApp?.BudgetApi;
      if (api) {
        await api.removeAssetCategory(id);
        // Recharger les données après la suppression
        const data = await api.getSettings();
        setSettingsData(data);
      }
    }, []);

    if (loading) {
      return /*#__PURE__*/React.createElement("div", {
        style: {
          padding: 20,
          textAlign: "center",
          color: C?.inkSoft || "#6B7278"
        }
      }, "Chargement des paramètres...");
    }

    const data = settingsData;
    const retireYear = data?.retireYear || 1985 + 64;
    const years = data?.years || [retireYear];
    return /*#__PURE__*/React.createElement(SectionCard, {
      title: "Paramètres généraux"
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 30,
        flexWrap: "wrap"
      }
    }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        marginBottom: 6
      }
    }, "Année de naissance (parent référent)"), /*#__PURE__*/React.createElement(DebouncedInput, {
      type: "number",
      value: data?.settings?.birthYear ?? 1985,
      onChange: v => updateSettingsField("birthYear", v),
      style: inputStyle
    })), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        marginBottom: 6
      }
    }, "Âge de départ à la retraite visé"), /*#__PURE__*/React.createElement(DebouncedInput, {
      type: "number",
      value: data?.settings?.retireAge ?? 64,
      onChange: v => updateSettingsField("retireAge", v),
      style: inputStyle
    })), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        marginBottom: 6
      }
    }, "Simuler la retraite jusqu'à l'âge de"), /*#__PURE__*/React.createElement(DebouncedInput, {
      type: "number",
      value: data?.settings?.simulateUntilAge ?? 85,
      onChange: v => updateSettingsField("simulateUntilAge", v),
      style: inputStyle
    }), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11,
        color: C?.inkSoft || "#6B7278",
        marginTop: 4,
        maxWidth: 220
      }
    }, "Étend la projection au-delà du départ pour voir si le patrimoine tient dans la durée (pensions injectées automatiquement).")), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        marginBottom: 6
      }
    }, "Trésorerie disponible de départ (€)"), /*#__PURE__*/React.createElement(DebouncedInput, {
      type: "number",
      value: data?.settings?.startBalance ?? 0,
      onChange: v => updateSettingsField("startBalance", v),
      style: inputStyle
    }), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11,
        color: C?.inkSoft || "#6B7278",
        marginTop: 4,
        maxWidth: 250
      }
    }, "Solde initial de référence.")), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        marginBottom: 6
      }
    }, "Taux d'inflation estimé (%)"), /*#__PURE__*/React.createElement(DebouncedInput, {
      type: "number",
      step: "0.1",
      value: (Number(data?.settings?.inflationRate) || 0.02) * 100,
      onChange: v => updateSettingsField("inflationRate", (parseFloat(v || 0) || 0) / 100),
      style: inputStyle
    }))), /*#__PURE__*/React.createElement("div", {
      style: {
        marginTop: 18,
        fontSize: 12.5,
        color: C?.inkSoft || "#6B7278"
      }
    }, "Année de retraite calculée : ", /*#__PURE__*/React.createElement("strong", null, retireYear), " — projection affichée jusqu'en ", /*#__PURE__*/React.createElement("strong", null, years[years.length - 1]), "."), (() => {
      const calcLatest = exports.latestTransactionDate || window.BudgetApp && window.BudgetApp.latestTransactionDate || latestTransactionDate;
      const calcPivot = exports.computePivotBalance || window.BudgetApp && window.BudgetApp.computePivotBalance || computePivotBalance;
      const latestTx = calcLatest(data);
      const pivotBalance = calcPivot(data);
      const txCount = (data?.bankImport?.transactions || []).filter(t => data?.settings?.pivotDate ? t.date <= data.settings.pivotDate : true).length;
      const isPivotActive = !!data?.settings?.pivotDate;
      const isAutoMode = data?.settings?.pivotMode === "auto";
      return /*#__PURE__*/React.createElement("div", {
        style: {
          marginTop: 20,
          paddingTop: 18,
          borderTop: `1px solid ${C?.line || "#DED6C4"}`
        }
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          fontFamily: "'Newsreader', serif",
          fontSize: 16,
          color: C?.ink || "#232A2E",
          fontWeight: 600,
          marginBottom: 4,
          display: "flex",
          alignItems: "center",
          gap: 8
        }
      }, "📌 Date Pivot — Point de bascule Réel → Prévisionnel"), /*#__PURE__*/React.createElement("div", {
        style: {
          fontSize: 12.5,
          color: C?.inkSoft || "#6B7278",
          marginBottom: 14
        }
      }, "Définit la date à laquelle le moteur bascule du réel constaté (transactions bancaires importées) vers la projection prévisionnelle."), /*#__PURE__*/React.createElement("div", {
        style: {
          display: "flex",
          gap: 16,
          marginBottom: 16,
          flexWrap: "wrap",
          alignItems: "center"
        }
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          fontSize: 12,
          color: C?.inkSoft || "#6B7278",
          fontWeight: 600
        }
      }, "Mode :"), /*#__PURE__*/React.createElement("label", {
        style: {
          display: "flex",
          alignItems: "center",
          gap: 6,
          cursor: "pointer",
          fontSize: 12.5
        }
      }, /*#__PURE__*/React.createElement("input", {
        type: "radio",
        name: "pivotMode",
        value: "auto",
        checked: isAutoMode,
        onChange: () => updateSettingsField("pivotMode", "auto")
      }), /*#__PURE__*/React.createElement("span", null, "Automatique — solde calculé à partir des transactions bancaires importées")), /*#__PURE__*/React.createElement("label", {
        style: {
          display: "flex",
          alignItems: "center",
          gap: 6,
          cursor: "pointer",
          fontSize: 12.5
        }
      }, /*#__PURE__*/React.createElement("input", {
        type: "radio",
        name: "pivotMode",
        value: "manual",
        checked: !isAutoMode,
        onChange: () => updateSettingsField("pivotMode", "manual")
      }), /*#__PURE__*/React.createElement("span", null, "Manuel — utiliser la trésorerie de départ saisie ci-dessus"))), /*#__PURE__*/React.createElement("div", {
        style: {
          display: "flex",
          gap: 16,
          flexWrap: "wrap",
          alignItems: "flex-end",
          marginBottom: 14
        }
      }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
        style: {
          fontSize: 12,
          color: C?.inkSoft || "#6B7278",
          marginBottom: 6
        }
      }, "Date Pivot"), /*#__PURE__*/React.createElement(DebouncedInput, {
        type: "date",
        value: data?.settings?.pivotDate || "",
        onChange: v => updateSettingsField("pivotDate", v),
        style: {
          ...inputStyle,
          minWidth: 160
        }
      })), latestTx && /*#__PURE__*/React.createElement("button", {
        type: "button",
        onClick: () => updateSettingsField("pivotDate", latestTx),
        style: {
          ...btnSmStyle,
          background: C?.pineSoft || "#E3ECE8",
          color: C?.pine || "#2F5D50",
          fontWeight: 600,
          border: `1px solid ${C?.pine || "#2F5D50"}`,
          padding: "7px 14px",
          display: "flex",
          alignItems: "center",
          gap: 6
        },
        title: `Dernier import : ${latestTx}`
      }, "⚡ Caler sur le dernier import (", new Date(latestTx + 'T12:00:00').toLocaleDateString('fr-FR', {
        day: 'numeric',
        month: 'short',
        year: 'numeric'
      }), ")"), isPivotActive && /*#__PURE__*/React.createElement("button", {
        type: "button",
        onClick: () => updateSettingsField("pivotDate", ""),
        style: {
          ...btnSmStyle,
          color: C?.brick || "#A8503C",
          border: `1px solid ${C?.line || "#DED6C4"}`,
          padding: "7px 12px"
        }
      }, "✕ Désactiver la Date Pivot")), isPivotActive && isAutoMode && /*#__PURE__*/React.createElement("div", {
        style: {
          display: "inline-flex",
          alignItems: "center",
          gap: 10,
          padding: "10px 16px",
          borderRadius: 10,
          marginBottom: 4,
          background: C?.pineSoft || "#E3ECE8",
          border: `1px solid ${C?.pine || "#2F5D50"}`,
          fontSize: 12.5,
          color: C?.pine || "#2F5D50",
          fontWeight: 600
        }
      }, /*#__PURE__*/React.createElement("span", null, "✅ Solde réel constaté au ", new Date(data.settings.pivotDate + 'T12:00:00').toLocaleDateString('fr-FR', {
        day: 'numeric',
        month: 'long',
        year: 'numeric'
      }), " :"), /*#__PURE__*/React.createElement("span", {
        style: {
          fontFamily: "'IBM Plex Mono', monospace",
          fontSize: 15,
          color: C?.navy || "#28394A"
        }
      }, new Intl.NumberFormat('fr-FR', {
        style: 'currency',
        currency: 'EUR',
        minimumFractionDigits: 2
      }).format(pivotBalance || 0)), /*#__PURE__*/React.createElement("span", {
        style: {
          fontWeight: 400,
          color: C?.inkSoft || "#6B7278"
        }
      }, "(", txCount, " transaction(s) prise(s) en compte)")), isPivotActive && !isAutoMode && /*#__PURE__*/React.createElement("div", {
        style: {
          display: "inline-flex",
          alignItems: "center",
          gap: 10,
          padding: "10px 16px",
          borderRadius: 10,
          marginBottom: 4,
          background: C?.goldSoft || "#F0EAD3",
          border: `1px solid ${C?.gold || "#93802E"}`,
          fontSize: 12.5,
          color: C?.gold || "#93802E",
          fontWeight: 600
        }
      }, /*#__PURE__*/React.createElement("span", null, "📌 Date Pivot active (mode manuel) — solde de départ utilisé :"), /*#__PURE__*/React.createElement("span", {
        style: {
          fontFamily: "'IBM Plex Mono', monospace",
          fontSize: 15,
          color: C?.navy || "#28394A"
        }
      }, new Intl.NumberFormat('fr-FR', {
        style: 'currency',
        currency: 'EUR',
        minimumFractionDigits: 2
      }).format(Number(data?.settings?.startBalance) || 0))), !isPivotActive && /*#__PURE__*/React.createElement("div", {
        style: {
          fontSize: 11.5,
          color: C?.inkSoft || "#6B7278",
          fontStyle: 'italic'
        }
      }, "Aucune Date Pivot configurée — le moteur utilise la trésorerie de départ saisie manuellement (", new Intl.NumberFormat('fr-FR', {
        style: 'currency',
        currency: 'EUR',
        minimumFractionDigits: 2
      }).format(Number(data?.settings?.startBalance) || 0), ")."));
    })(), /*#__PURE__*/React.createElement("div", {
      style: {
        marginTop: 20,
        paddingTop: 18,
        borderTop: `1px solid ${C?.line || "#DED6C4"}`
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontFamily: "'Newsreader', serif",
        fontSize: 16,
        color: C?.ink || "#232A2E",
        fontWeight: 600,
        marginBottom: 4,
        display: "flex",
        alignItems: "center",
        gap: 8
      }
    }, /*#__PURE__*/React.createElement("span", null, "Virement automatique compte courant ↔ épargne"), openHelp && /*#__PURE__*/React.createElement(HelpBadge, {
      sectionKey: "settings",
      badgeId: "sweep_settings",
      onClick: openHelp,
      inline: true
    })), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12.5,
        color: C?.inkSoft || "#6B7278",
        marginBottom: 14
      }
    }, "En fin de mois, l'excédent au-dessus du seuil haut est versé vers les comptes marqués d'une priorité (page Placements). À tout moment, si la trésorerie passe sous le seuil bas, le manque est repris sur ces mêmes comptes."), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 30,
        flexWrap: "wrap",
        alignItems: "center"
      }
    }, /*#__PURE__*/React.createElement("label", {
      style: {
        display: "flex",
        alignItems: "center",
        gap: 8,
        cursor: "pointer"
      }
    }, /*#__PURE__*/React.createElement("input", {
      type: "checkbox",
      checked: !!data?.settings?.sweepEnabled,
      onChange: e => updateSettingsField("sweepEnabled", e.target.checked)
    }), /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 12.5,
        color: C?.ink || "#232A2E",
        fontWeight: 600
      }
    }, "Activer le virement automatique")), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        marginBottom: 6
      }
    }, "Seuil haut — versement vers l'épargne (€)"), /*#__PURE__*/React.createElement(DebouncedInput, {
      type: "number",
      value: data?.settings?.cashCeiling ?? "",
      placeholder: "ex. 15000",
      onChange: v => updateSettingsField("cashCeiling", v),
      style: inputStyle
    })), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        marginBottom: 6
      }
    }, "Seuil bas — retrait depuis l'épargne (€)"), /*#__PURE__*/React.createElement(DebouncedInput, {
      type: "number",
      value: data?.settings?.cashFloor ?? "",
      placeholder: "ex. 3000",
      onChange: v => updateSettingsField("cashFloor", v),
      style: inputStyle
    })))), /*#__PURE__*/React.createElement("div", {
      style: {
        marginTop: 20,
        paddingTop: 18,
        borderTop: `1px solid ${C?.line || "#DED6C4"}`
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontFamily: "'Newsreader', serif",
        fontSize: 16,
        color: C?.ink || "#232A2E",
        fontWeight: 600,
        marginBottom: 4
      }
    }, "Catégories d'actifs (Répartition d'actifs)"), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12.5,
        color: C?.inkSoft || "#6B7278",
        marginBottom: 14
      }
    }, "Chaque catégorie utilisable sur la page Placements est associée ici à l'une des 6 classes d'actif affichées dans \"Répartition d'actifs\" (Vue d'ensemble)."), /*#__PURE__*/React.createElement(EditableTable, {
      columns: [{
        key: "icon",
        label: "Icône / Émoji",
        type: "text",
        align: "center",
        placeholder: "ex. 📖"
      }, {
        key: "color",
        label: "Couleur",
        type: "color",
        align: "center"
      }, {
        key: "name",
        label: "Catégorie (telle que saisie page Placements)",
        type: "text"
      }, {
        key: "bucket",
        label: "Classe d'actif",
        type: "select",
        options: [{
          value: "cash",
          label: "Cash & Livrets réglementés"
        }, {
          value: "fondsEuros",
          label: "Fonds en Euros (Sécurité)"
        }, {
          value: "immobilier",
          label: "Immobilier (SCPI / SC)"
        }, {
          value: "actions",
          label: "Actions Monde & Thématiques"
        }, {
          value: "obligations",
          label: "Obligations (Taux)"
        }, {
          value: "epargneSalariale",
          label: "Épargne Salariale & PER"
        }]
      }],
      rows: data?.assetCategories || [],
      onCell: (id, field, value) => updateAssetCategory(id, field, value),
      onRemove: id => removeAssetCategory(id),
      onAdd: () => addAssetCategory({
        id: uid(),
        icon: "📁",
        name: "Nouvelle catégorie",
        bucket: "cash"
      })
    })));
  }
  exports.SettingsView = SettingsView;
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);