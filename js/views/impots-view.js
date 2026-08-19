/**
 * Vue Impôts (ImpotsView : Foyer fiscal, Barème progressif, Simulateur PAS & Ajustements réels)
 */
(function (exports) {
  'use strict';

  const {
    C,
    eur,
    uid
  } = exports.C ? exports : window.BudgetApp || {};
  const {
    SectionCard,
    EditableTable
  } = exports.SectionCard ? exports : window.BudgetApp || {};
  function ImpotsView({
    data,
    setCell,
    addRow,
    removeRow,
    update,
    taxPreview
  }) {
    const inputStyle = {
      border: `1px solid ${C?.line || "#DED6C4"}`,
      borderRadius: 7,
      padding: "8px 10px",
      fontSize: 14,
      width: 140
    };
    return /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement(SectionCard, {
      title: "Foyer fiscal",
      subtitle: "Déclaration commune (mariés/pacsés) — 2 parts de base, puis 0,5 part pour chacun des deux premiers enfants à charge, 1 part à partir du 3ᵉ"
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 30,
        flexWrap: "wrap",
        marginBottom: 18
      }
    }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        marginBottom: 6
      }
    }, "Âge de sortie du foyer fiscal des enfants"), /*#__PURE__*/React.createElement("input", {
      type: "number",
      value: data?.settings?.childExitAge ?? 21,
      onChange: e => update("settings", s => ({
        ...s,
        childExitAge: e.target.value
      })),
      style: inputStyle
    }), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11.5,
        color: C?.inkSoft || "#6B7278",
        marginTop: 4
      }
    }, "21 ans par défaut — jusqu'à 25 ans si rattachement étudiant")), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        marginBottom: 6
      }
    }, "Abattement forfaitaire (frais professionnels)"), /*#__PURE__*/React.createElement("input", {
      type: "number",
      step: "0.1",
      value: (Number(data?.settings?.taxAbattement || 0.1) * 100).toFixed(1),
      onChange: e => update("settings", s => ({
        ...s,
        taxAbattement: (parseFloat(e.target.value || 0) || 0) / 100
      })),
      style: inputStyle
    }), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11.5,
        color: C?.inkSoft || "#6B7278",
        marginTop: 4
      }
    }, "10 % par défaut, sans plafond modélisé ici"))), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        marginBottom: 8,
        fontWeight: 600
      }
    }, "Enfants à charge (fiscalement)"), /*#__PURE__*/React.createElement(EditableTable, {
      columns: [{
        key: "birthYear",
        label: "Année de naissance",
        type: "number"
      }],
      rows: data?.taxChildren || [],
      onCell: setCell("taxChildren"),
      onRemove: removeRow("taxChildren"),
      onAdd: () => addRow("taxChildren", () => ({
        id: uid(),
        birthYear: new Date().getFullYear()
      }))
    })), /*#__PURE__*/React.createElement(SectionCard, {
      title: "Barème progressif de l'impôt (par part)",
      subtitle: "Barème 2026 sur les revenus 2025 par défaut — revalorisé chaque année selon l'inflation, appliqué ici tel quel à toutes les années projetées par simplification"
    }, /*#__PURE__*/React.createElement(EditableTable, {
      columns: [{
        key: "upTo",
        label: "Jusqu'à (€) — laisser vide pour la dernière tranche",
        type: "number",
        align: "right"
      }, {
        key: "rate",
        label: "Taux (%)",
        type: "percent",
        align: "right"
      }],
      rows: data?.taxBrackets || [],
      onCell: setCell("taxBrackets"),
      onRemove: removeRow("taxBrackets"),
      onAdd: () => addRow("taxBrackets", () => ({
        id: uid(),
        upTo: "",
        rate: 0
      }))
    })), /*#__PURE__*/React.createElement(SectionCard, {
      title: "Aperçu — parts, revenu imposable et taux de PAS prévisionnels",
      subtitle: "Simplification : pas de distinction fine entre types de revenus, pas de décote, pas de crédits d'impôt. Un ordre de grandeur estimatif."
    }, /*#__PURE__*/React.createElement("table", {
      style: {
        width: "100%",
        borderCollapse: "collapse",
        fontSize: 13
      }
    }, /*#__PURE__*/React.createElement("thead", null, /*#__PURE__*/React.createElement("tr", null, /*#__PURE__*/React.createElement("th", {
      style: {
        textAlign: "left",
        fontSize: 11,
        color: C?.inkSoft || "#6B7278",
        padding: "0 6px 6px"
      }
    }, "Année"), /*#__PURE__*/React.createElement("th", {
      style: {
        textAlign: "right",
        fontSize: 11,
        color: C?.inkSoft || "#6B7278",
        padding: "0 6px 6px"
      }
    }, "Parts"), /*#__PURE__*/React.createElement("th", {
      style: {
        textAlign: "right",
        fontSize: 11,
        color: C?.inkSoft || "#6B7278",
        padding: "0 6px 6px"
      }
    }, "Revenu imposable"), /*#__PURE__*/React.createElement("th", {
      style: {
        textAlign: "right",
        fontSize: 11,
        color: C?.inkSoft || "#6B7278",
        padding: "0 6px 6px"
      }
    }, "Impôt (prévision/réel)"), /*#__PURE__*/React.createElement("th", {
      style: {
        textAlign: "right",
        fontSize: 11,
        color: C?.inkSoft || "#6B7278",
        padding: "0 6px 6px"
      }
    }, "Taux PAS"))), /*#__PURE__*/React.createElement("tbody", null, (taxPreview || []).map(row => /*#__PURE__*/React.createElement("tr", {
      key: row.year,
      style: {
        borderTop: `1px solid ${C?.line || "#DED6C4"}`
      }
    }, /*#__PURE__*/React.createElement("td", {
      style: {
        padding: "6px"
      }
    }, row.year), /*#__PURE__*/React.createElement("td", {
      style: {
        textAlign: "right",
        padding: "6px",
        fontFamily: "'IBM Plex Mono', monospace"
      }
    }, row.parts), /*#__PURE__*/React.createElement("td", {
      style: {
        textAlign: "right",
        padding: "6px",
        fontFamily: "'IBM Plex Mono', monospace"
      }
    }, eur(row.taxableIncome)), /*#__PURE__*/React.createElement("td", {
      style: {
        textAlign: "right",
        padding: "6px",
        fontFamily: "'IBM Plex Mono', monospace"
      }
    }, eur(row.taxActual)), /*#__PURE__*/React.createElement("td", {
      style: {
        textAlign: "right",
        padding: "6px",
        fontFamily: "'IBM Plex Mono', monospace"
      }
    }, (row.ratePAS * 100).toFixed(1), " %")))))), /*#__PURE__*/React.createElement(SectionCard, {
      title: "Taux de prélèvement à la source — valeurs réelles",
      subtitle: "Renseignez ici le taux exact affiché sur votre fiche de paie (il change chaque septembre) : il remplace le taux prévisionnel pour cette année précise"
    }, /*#__PURE__*/React.createElement(EditableTable, {
      columns: [{
        key: "year",
        label: "Année",
        type: "number"
      }, {
        key: "rate",
        label: "Taux (%)",
        type: "percent",
        align: "right"
      }, {
        key: "notes",
        label: "Notes",
        type: "text"
      }],
      rows: data?.taxRateOverrides || [],
      onCell: setCell("taxRateOverrides"),
      onRemove: removeRow("taxRateOverrides"),
      onAdd: () => addRow("taxRateOverrides", () => ({
        id: uid(),
        year: new Date().getFullYear(),
        rate: 0.1,
        notes: ""
      }))
    })), /*#__PURE__*/React.createElement(SectionCard, {
      title: "Impôt réel constaté (avis d'imposition)",
      subtitle: "Dès réception de votre avis d'imposition définitif, ajoutez le montant réellement dû pour cette année-là — cela affine automatiquement la régularisation de l'année suivante"
    }, /*#__PURE__*/React.createElement(EditableTable, {
      columns: [{
        key: "year",
        label: "Année des revenus",
        type: "number"
      }, {
        key: "amount",
        label: "Impôt réel dû (€)",
        type: "number",
        align: "right"
      }, {
        key: "notes",
        label: "Notes",
        type: "text"
      }],
      rows: data?.taxActualOverrides || [],
      onCell: setCell("taxActualOverrides"),
      onRemove: removeRow("taxActualOverrides"),
      onAdd: () => addRow("taxActualOverrides", () => ({
        id: uid(),
        year: new Date().getFullYear(),
        amount: 0,
        notes: ""
      }))
    })));
  }
  exports.ImpotsView = ImpotsView;
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);