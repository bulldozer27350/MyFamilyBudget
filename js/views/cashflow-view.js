/**
 * Vue Trésorerie (CashflowView : Revenus, Charges récurrentes, Primes & Dépenses ponctuelles)
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
    uid
  } = exports.C ? exports : window.BudgetApp || {};
  const {
    chargeEffectiveGrowth,
    chargeMonthlyForYear,
    incomeMonthlyForYear,
    computeRealAverages
  } = exports.chargeMonthlyForYear ? exports : window.BudgetApp || {};
  const {
    SectionCard,
    EditableTable
  } = exports.SectionCard ? exports : window.BudgetApp || {};
  const {
    LineChartJS
  } = exports.LineChartJS ? exports : window.BudgetApp || {};
  const {
    HelpBadge
  } = exports.HelpBadge ? exports : window.BudgetApp || {};
  function CashflowView({
    data,
    setCell,
    addRow,
    removeRow,
    retireYear,
    years,
    cashflow,
    variablePreview,
    previewYears,
    openHelp
  }) {
    const [showAdjust, setShowAdjust] = useState(false);

    // Calcul des suggestions d'ajustement prévisionnel vs réel
    const realAverages = useMemo(() => {
      const fn = exports.computeRealAverages || window.BudgetApp && window.BudgetApp.computeRealAverages || computeRealAverages;
      return fn(data);
    }, [data?.bankImport]);
    const applyAdjustment = (lineId, kind, newMonthly) => {
      const listKey = kind === "charge" ? "charges" : kind === "revenu" ? "incomes" : "placements";
      setCell(listKey)(lineId, "monthly", String(newMonthly));
    };
    const suggestions = useMemo(() => {
      const inflationRate = Number(data?.settings.inflationRate) || 0.02;
      const currentYear = new Date().getFullYear();
      const lines = [];
      const addLine = (row, kind) => {
        const avg = realAverages[row.id];
        if (!avg || avg.avg3m === null) return;
        const budgeted = kind === "charge" ? chargeMonthlyForYear(row, currentYear, inflationRate) : kind === "revenu" ? incomeMonthlyForYear(row, currentYear) : Number(row.monthly) || 0;
        if (budgeted <= 0) return;
        const ecart = avg.avg3m - budgeted;
        const ecartPct = ecart / budgeted * 100;
        if (Math.abs(ecart) >= 10 || Math.abs(ecartPct) >= 5) {
          lines.push({
            id: row.id,
            label: kind === "placement" ? `Épargne : ${row.label}` : row.label,
            kind,
            budgeted,
            avg3m: avg.avg3m,
            avg12m: avg.avg12m,
            ecart,
            ecartPct,
            months: avg.months,
            suggested: Math.round(avg.avg3m * 100) / 100
          });
        }
      };
      (data?.charges || []).forEach(c => addLine(c, "charge"));
      (data?.incomes || []).forEach(i => addLine(i, "revenu"));
      (data?.placements || []).forEach(p => addLine(p, "placement"));
      return lines.sort((a, b) => Math.abs(b.ecart) - Math.abs(a.ecart));
    }, [realAverages, data?.charges, data?.incomes, data?.placements, data?.settings.inflationRate]);
    const sortedCategories = useMemo(() => {
      return [...(data?.bankImport?.categories || [])].sort((a, b) => (a.label || "").localeCompare(b.label || "", "fr", {
        sensitivity: "base"
      }));
    }, [data?.bankImport?.categories]);
    const categoryOptions = useMemo(() => [{
      value: "",
      label: "— Non liée —"
    }, ...sortedCategories.map(c => ({
      value: c.id,
      label: c.label
    }))], [sortedCategories]);
    return /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement(SectionCard, {
      title: /*#__PURE__*/React.createElement("span", null, "Évolution de la trésorerie (estimation simplifiée, sans virements automatiques)", openHelp && /*#__PURE__*/React.createElement(HelpBadge, {
        sectionKey: "cashflow",
        badgeId: "moteur_simplifie",
        onClick: openHelp,
        inline: true
      })),
      subtitle: "Année par année, à partir de la trésorerie de départ — ⚠️ ce calcul ignore le virement automatique compte courant ↔ épargne : il montre ce que deviendrait votre trésorerie si elle n'était jamais ni écrêtée vers l'épargne, ni renflouée depuis elle. Pour le solde réel du compte courant, voir la courbe dans Vue d'ensemble."
    }, /*#__PURE__*/React.createElement(LineChartJS, {
      data: cashflow || [],
      xKey: "year",
      zeroLine: true,
      series: [{
        key: "balance",
        label: "Trésorerie cumulée (hors virements auto)",
        color: C?.pine || "#2F5D50",
        fill: true
      }]
    })), suggestions.length > 0 && /*#__PURE__*/React.createElement("div", {
      style: {
        marginBottom: 20,
        borderRadius: 12,
        overflow: 'hidden',
        border: `1px solid ${C?.gold || "#93802E"}`,
        boxShadow: '0 2px 12px rgba(147,128,46,0.10)'
      }
    }, /*#__PURE__*/React.createElement("div", {
      onClick: () => setShowAdjust(!showAdjust),
      style: {
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '13px 18px',
        background: C?.goldSoft || "#F0EAD3",
        cursor: 'pointer',
        gap: 12
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        display: 'flex',
        alignItems: 'center',
        gap: 10
      }
    }, /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 18
      }
    }, "💡"), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontWeight: 700,
        fontSize: 13.5,
        color: C?.gold || "#93802E"
      }
    }, suggestions.length, " ligne", suggestions.length > 1 ? 's' : '', " budgétaire", suggestions.length > 1 ? 's' : '', " présente", suggestions.length > 1 ? 'nt' : '', " une dérive par rapport au réel constaté"), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        marginTop: 2
      }
    }, "Basé sur les pointages de la page Pointage. Cliquez pour examiner les suggestions d'ajustement."))), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 18,
        color: C?.gold || "#93802E",
        flexShrink: 0
      }
    }, showAdjust ? '▲' : '▼')), showAdjust && /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.panel || "#FFFFFF"
      }
    }, /*#__PURE__*/React.createElement("table", {
      style: {
        width: '100%',
        borderCollapse: 'collapse',
        fontSize: 12.5
      }
    }, /*#__PURE__*/React.createElement("thead", null, /*#__PURE__*/React.createElement("tr", {
      style: {
        borderBottom: `2px solid ${C?.line || "#DED6C4"}`
      }
    }, /*#__PURE__*/React.createElement("th", {
      style: {
        padding: '9px 14px',
        textAlign: 'left',
        fontSize: 11,
        fontWeight: 700,
        color: C?.inkSoft || "#6B7278",
        background: C?.panelAlt || "#EFEAE0"
      }
    }, "Type"), /*#__PURE__*/React.createElement("th", {
      style: {
        padding: '9px 14px',
        textAlign: 'left',
        fontSize: 11,
        fontWeight: 700,
        color: C?.inkSoft || "#6B7278",
        background: C?.panelAlt || "#EFEAE0"
      }
    }, "Libellé"), /*#__PURE__*/React.createElement("th", {
      style: {
        padding: '9px 14px',
        textAlign: 'right',
        fontSize: 11,
        fontWeight: 700,
        color: C?.inkSoft || "#6B7278",
        background: C?.panelAlt || "#EFEAE0"
      }
    }, "Budget Actuel"), /*#__PURE__*/React.createElement("th", {
      style: {
        padding: '9px 14px',
        textAlign: 'right',
        fontSize: 11,
        fontWeight: 700,
        color: '#2563EB',
        background: C?.panelAlt || "#EFEAE0"
      }
    }, "Moy. Réelle 3M"), /*#__PURE__*/React.createElement("th", {
      style: {
        padding: '9px 14px',
        textAlign: 'right',
        fontSize: 11,
        fontWeight: 700,
        color: C?.inkSoft || "#6B7278",
        background: C?.panelAlt || "#EFEAE0"
      }
    }, "Moy. Réelle 12M"), /*#__PURE__*/React.createElement("th", {
      style: {
        padding: '9px 14px',
        textAlign: 'right',
        fontSize: 11,
        fontWeight: 700,
        color: C?.inkSoft || "#6B7278",
        background: C?.panelAlt || "#EFEAE0"
      }
    }, "Écart Moyen"), /*#__PURE__*/React.createElement("th", {
      style: {
        padding: '9px 14px',
        textAlign: 'center',
        fontSize: 11,
        fontWeight: 700,
        color: C?.inkSoft || "#6B7278",
        background: C?.panelAlt || "#EFEAE0"
      }
    }, "Suggestion"))), /*#__PURE__*/React.createElement("tbody", null, suggestions.map(s => {
      const ecartColor = s.ecart > 0 ? s.kind === 'charge' ? '#DC2626' : '#16A34A' : s.kind === 'charge' ? '#16A34A' : '#DC2626';
      return /*#__PURE__*/React.createElement("tr", {
        key: s.id,
        style: {
          borderBottom: `1px solid ${C?.line || "#DED6C4"}`
        }
      }, /*#__PURE__*/React.createElement("td", {
        style: {
          padding: '9px 14px'
        }
      }, /*#__PURE__*/React.createElement("span", {
        style: {
          padding: '2px 8px',
          borderRadius: 8,
          fontSize: 11,
          fontWeight: 600,
          background: s.kind === 'charge' ? C?.brickSoft || "#F4E4DF" : C?.pineSoft || "#E3ECE8",
          color: s.kind === 'charge' ? C?.brick || "#A8503C" : C?.pine || "#2F5D50"
        }
      }, s.kind === 'charge' ? 'Charge' : 'Revenu')), /*#__PURE__*/React.createElement("td", {
        style: {
          padding: '9px 14px',
          fontWeight: 600,
          maxWidth: 200,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap'
        },
        title: s.label
      }, s.label), /*#__PURE__*/React.createElement("td", {
        style: {
          padding: '9px 14px',
          textAlign: 'right',
          fontFamily: "'IBM Plex Mono', monospace"
        }
      }, eur(s.budgeted)), /*#__PURE__*/React.createElement("td", {
        style: {
          padding: '9px 14px',
          textAlign: 'right',
          fontFamily: "'IBM Plex Mono', monospace",
          fontWeight: 700,
          color: '#2563EB'
        }
      }, eur(s.avg3m), /*#__PURE__*/React.createElement("div", {
        style: {
          fontSize: 10,
          color: C?.inkSoft || "#6B7278",
          fontWeight: 400
        }
      }, s.months, " mois pointés")), /*#__PURE__*/React.createElement("td", {
        style: {
          padding: '9px 14px',
          textAlign: 'right',
          fontFamily: "'IBM Plex Mono', monospace",
          color: C?.inkSoft || "#6B7278"
        }
      }, s.avg12m !== null ? eur(s.avg12m) : '—'), /*#__PURE__*/React.createElement("td", {
        style: {
          padding: '9px 14px',
          textAlign: 'right',
          fontFamily: "'IBM Plex Mono', monospace",
          fontWeight: 700,
          color: ecartColor
        }
      }, s.ecart >= 0 ? '+' : '', eur(s.ecart), /*#__PURE__*/React.createElement("div", {
        style: {
          fontSize: 10,
          fontWeight: 400
        }
      }, s.ecartPct >= 0 ? '+' : '', s.ecartPct.toFixed(1), "%")), /*#__PURE__*/React.createElement("td", {
        style: {
          padding: '9px 14px',
          textAlign: 'center'
        }
      }, /*#__PURE__*/React.createElement("button", {
        type: "button",
        onClick: () => applyAdjustment(s.id, s.kind, s.suggested),
        style: {
          padding: '5px 12px',
          borderRadius: 7,
          fontSize: 11.5,
          fontWeight: 700,
          cursor: 'pointer',
          background: C?.pine || "#2F5D50",
          color: '#fff',
          border: 'none',
          display: 'inline-flex',
          alignItems: 'center',
          gap: 4
        },
        title: `Remplacer ${eur(s.budgeted)} par ${eur(s.suggested)}`
      }, "⚡ Appliquer ", eur(s.suggested))));
    })), /*#__PURE__*/React.createElement("tfoot", null, /*#__PURE__*/React.createElement("tr", {
      style: {
        background: C?.panelAlt || "#EFEAE0",
        borderTop: `2px solid ${C?.line || "#DED6C4"}`
      }
    }, /*#__PURE__*/React.createElement("td", {
      colSpan: 7,
      style: {
        padding: '8px 14px',
        fontSize: 11.5,
        color: C?.inkSoft || "#6B7278",
        fontStyle: 'italic'
      }
    }, "💡 Ces suggestions sont basées sur les pointages effectués dans l'onglet ", /*#__PURE__*/React.createElement("strong", null, "Pointage"), ". Appliquer une suggestion met à jour le montant mensuel de la ligne correspondante.")))))), /*#__PURE__*/React.createElement(SectionCard, {
      title: "Revenus récurrents",
      subtitle: "Montant mensuel, actif entre les deux dates, avec augmentation annuelle optionnelle"
    }, /*#__PURE__*/React.createElement(EditableTable, {
      columns: [{
        key: "label",
        label: "Libellé",
        type: "text"
      }, {
        key: "monthly",
        label: "€ / mois",
        type: "number",
        align: "right"
      }, {
        key: "growthRate",
        label: "Augmentation annuelle %",
        type: "percent",
        align: "right"
      }, {
        key: "start",
        label: "Début",
        type: "date"
      }, {
        key: "end",
        label: "Fin",
        type: "date"
      }, {
        key: "categoryId",
        label: "Cat. Bancaire",
        type: "select",
        options: categoryOptions
      }, {
        key: "notes",
        label: "Notes",
        type: "text"
      }],
      rows: data?.incomes || [],
      onCell: setCell("incomes"),
      onRemove: removeRow("incomes"),
      onAdd: () => addRow("incomes", () => ({
        id: uid(),
        label: "Nouveau revenu",
        monthly: 0,
        start: "2026-01-01",
        end: String(retireYear) + "-12-31",
        growthRate: 0,
        categoryId: "",
        notes: ""
      }))
    })), /*#__PURE__*/React.createElement(SectionCard, {
      title: "Charges récurrentes",
      subtitle: "Prêts, logement étudiant, frais courants, entretien — une charge sans taux saisi suit automatiquement l'inflation ; mettez 0 % pour la geler explicitement"
    }, /*#__PURE__*/React.createElement(EditableTable, {
      columns: [{
        key: "label",
        label: "Libellé",
        type: "text"
      }, {
        key: "monthly",
        label: "€ / mois",
        type: "number",
        align: "right"
      }, {
        key: "growthRate",
        label: "Croissance annuelle %",
        type: "percent",
        align: "right",
        placeholder: "infl."
      }, {
        key: "start",
        label: "Début",
        type: "date"
      }, {
        key: "end",
        label: "Fin",
        type: "date"
      }, {
        key: "categoryId",
        label: "Cat. Bancaire",
        type: "select",
        options: categoryOptions
      }, {
        key: "notes",
        label: "Notes",
        type: "text"
      }],
      rows: data?.charges || [],
      onCell: setCell("charges"),
      onRemove: removeRow("charges"),
      onAdd: () => addRow("charges", () => ({
        id: uid(),
        label: "Nouvelle charge",
        monthly: 0,
        start: "2026-01-01",
        end: String(retireYear) + "-12-31",
        categoryId: "",
        notes: ""
      }))
    })), /*#__PURE__*/React.createElement(SectionCard, {
      title: "Dépenses ponctuelles",
      subtitle: "Événements, achats, travaux à une date précise"
    }, /*#__PURE__*/React.createElement(EditableTable, {
      columns: [{
        key: "label",
        label: "Libellé",
        type: "text"
      }, {
        key: "date",
        label: "Date",
        type: "date"
      }, {
        key: "amount",
        label: "Montant (€)",
        type: "number",
        align: "right"
      }, {
        key: "notes",
        label: "Notes",
        type: "text"
      }],
      rows: data?.oneoff || [],
      onCell: setCell("oneoff"),
      onRemove: removeRow("oneoff"),
      onAdd: () => addRow("oneoff", () => ({
        id: uid(),
        label: "Nouvelle dépense",
        date: "2026-01-01",
        amount: 0,
        notes: ""
      }))
    })), /*#__PURE__*/React.createElement(SectionCard, {
      title: "Primes, participation & intéressement",
      subtitle: "Chaque ligne = un taux appliqué à un revenu de référence. La prévision se recalcule automatiquement si ce revenu change."
    }, /*#__PURE__*/React.createElement(EditableTable, {
      columns: [{
        key: "label",
        label: "Libellé",
        type: "text"
      }, {
        key: "type",
        label: "Type",
        type: "text"
      }, {
        key: "refIncomeLabel",
        label: "Revenu de référence",
        type: "select",
        options: (data?.incomes || []).map(i => i.label)
      }, {
        key: "rate",
        label: "Taux (%)",
        type: "percent",
        align: "right"
      }, {
        key: "startYear",
        label: "Année début",
        type: "number"
      }, {
        key: "endYear",
        label: "Année fin",
        type: "number"
      }, {
        key: "taxable",
        label: "Imposable par défaut",
        type: "select",
        options: ["Oui", "Non"]
      }, {
        key: "notes",
        label: "Notes",
        type: "text"
      }],
      rows: data?.variableIncomes || [],
      onCell: setCell("variableIncomes"),
      onRemove: removeRow("variableIncomes"),
      onAdd: () => addRow("variableIncomes", () => ({
        id: uid(),
        label: "Nouvelle prime",
        type: "Prime",
        refIncomeLabel: data?.incomes?.[0]?.label || "",
        rate: 0.05,
        startYear: years?.[0] || 2026,
        endYear: retireYear,
        taxable: "Oui",
        notes: ""
      }))
    }), (variablePreview || []).length > 0 && /*#__PURE__*/React.createElement("div", {
      style: {
        marginTop: 18
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11,
        textTransform: "uppercase",
        letterSpacing: 0.4,
        color: C?.inkSoft || "#6B7278",
        fontWeight: 600,
        marginBottom: 8
      }
    }, "Aperçu — prévision (ou réel si connu) sur les prochaines années"), /*#__PURE__*/React.createElement("table", {
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
    }, "Libellé"), (previewYears || []).map(y => /*#__PURE__*/React.createElement("th", {
      key: y,
      style: {
        textAlign: "right",
        fontSize: 11,
        color: C?.inkSoft || "#6B7278",
        padding: "0 6px 6px"
      }
    }, y)))), /*#__PURE__*/React.createElement("tbody", null, (variablePreview || []).map(row => /*#__PURE__*/React.createElement("tr", {
      key: row.label,
      style: {
        borderTop: `1px solid ${C?.line || "#DED6C4"}`
      }
    }, /*#__PURE__*/React.createElement("td", {
      style: {
        padding: "6px"
      }
    }, row.label), row.cells.map(c => /*#__PURE__*/React.createElement("td", {
      key: c.year,
      style: {
        textAlign: "right",
        padding: "6px",
        fontFamily: "'IBM Plex Mono', monospace",
        color: c.amount == null ? C?.inkSoft || "#6B7278" : c.isReal ? C?.pine || "#2F5D50" : C?.ink || "#232A2E"
      }
    }, c.amount == null ? "—" : eur(c.amount) + (c.isReal ? " ✓" : ""))))))), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11.5,
        color: C?.inkSoft || "#6B7278",
        marginTop: 8
      }
    }, "✓ = montant réel saisi ci-dessous — sinon, prévision calculée à partir du taux."))), /*#__PURE__*/React.createElement(SectionCard, {
      title: "Valeurs réelles constatées",
      subtitle: "Dès que vous connaissez un montant réel, ajoutez-le ici : il remplace la prévision pour cette année précise. Le champ « imposable » ne s'applique que si le traitement fiscal de cette année-là diffère de la ligne ci-dessus"
    }, /*#__PURE__*/React.createElement(EditableTable, {
      columns: [{
        key: "label",
        label: "Élément",
        type: "select",
        options: (data?.variableIncomes || []).map(v => v.label)
      }, {
        key: "year",
        label: "Année",
        type: "number"
      }, {
        key: "amount",
        label: "Montant réel (€)",
        type: "number",
        align: "right"
      }, {
        key: "taxable",
        label: "Imposable cette année-là",
        type: "select",
        options: ["", "Oui", "Non"]
      }, {
        key: "notes",
        label: "Notes",
        type: "text"
      }],
      rows: data?.variableOverrides || [],
      onCell: setCell("variableOverrides"),
      onRemove: removeRow("variableOverrides"),
      onAdd: () => addRow("variableOverrides", () => ({
        id: uid(),
        label: data?.variableIncomes?.[0]?.label || "",
        year: new Date().getFullYear(),
        amount: 0,
        taxable: "",
        notes: ""
      }))
    })));
  }
  exports.CashflowView = CashflowView;
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);