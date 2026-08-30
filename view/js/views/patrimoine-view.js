/**
 * Vue Patrimoine (PatrimoineView : Placements, Cartes & Tiroir Drawer, Crédits, Immobilier)
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
    eur
  } = exports.C ? exports : window.BudgetApp || {};
  const {
    SectionCard,
    EditableTable,
    Field,
    FieldHint
  } = exports.SectionCard ? exports : window.BudgetApp || {};
  const {
    LineChartJS
  } = exports.LineChartJS ? exports : window.BudgetApp || {};
  const {
    HelpBadge
  } = exports.HelpBadge ? exports : window.BudgetApp || {};
  const BudgetApi = exports.BudgetApi || window.BudgetApp && window.BudgetApp.BudgetApi;
  const DEFAULT_BUCKET_ICONS = {
    cash: "📖",
    fondsEuros: "💶",
    actions: "🌍",
    immobilier: "🏢",
    obligations: "📜",
    epargneSalariale: "💼"
  };
  const NEUTRAL_CATEGORY_COLOR = "#8A8778";
  function getCategoryTheme(categoryName, categories) {
    const catObj = (categories || []).find(c => c.name === categoryName);
    const bucket = catObj ? catObj.bucket : "cash";
    const icon = catObj && catObj.icon && catObj.icon.trim() ? catObj.icon.trim() : DEFAULT_BUCKET_ICONS[bucket] || DEFAULT_BUCKET_ICONS.cash;
    const color = catObj && catObj.color && catObj.color.trim() ? catObj.color.trim() : NEUTRAL_CATEGORY_COLOR;
    return {
      icon,
      color
    };
  }
  function PlacementCard({
    p,
    categories,
    onClick
  }) {
    const theme = getCategoryTheme(p.category, categories);
    const monthlyVal = Number(p.monthly) || 0;
    return /*#__PURE__*/React.createElement("div", {
      onClick: onClick,
      style: {
        background: C?.panel || "#FFFFFF",
        border: `1.5px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 10,
        padding: "12px 14px",
        cursor: "pointer",
        transition: "all 0.15s ease",
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-between",
        boxShadow: "0 2px 4px rgba(0,0,0,0.02)"
      },
      onMouseEnter: e => {
        e.currentTarget.style.transform = "translateY(-2px)";
        e.currentTarget.style.boxShadow = "0 6px 14px rgba(0,0,0,0.08)";
        e.currentTarget.style.borderColor = theme.color;
      },
      onMouseLeave: e => {
        e.currentTarget.style.transform = "translateY(0)";
        e.currentTarget.style.boxShadow = "0 2px 4px rgba(0,0,0,0.02)";
        e.currentTarget.style.borderColor = C?.line || "#DED6C4";
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        alignItems: "center",
        gap: 10,
        marginBottom: 10
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        width: 32,
        height: 32,
        borderRadius: 8,
        background: theme.color + "1F",
        border: `1px solid ${theme.color}44`,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        fontSize: 16,
        flexShrink: 0
      }
    }, theme.icon), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 14,
        fontWeight: 700,
        color: C?.ink || "#232A2E",
        whiteSpace: "nowrap",
        overflow: "hidden",
        textOverflow: "ellipsis",
        flex: 1
      },
      title: p.label
    }, p.label || "Nouveau placement")), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "baseline",
        marginTop: 2
      }
    }, /*#__PURE__*/React.createElement("span", {
      style: {
        fontFamily: "'IBM Plex Mono', monospace",
        fontSize: 17,
        fontWeight: 700,
        color: C?.pine || "#2F5D50"
      }
    }, eur(p.balance)), monthlyVal > 0 && /*#__PURE__*/React.createElement("span", {
      style: {
        fontFamily: "'IBM Plex Mono', monospace",
        fontSize: 11,
        fontWeight: 600,
        color: C?.navy || "#28394A",
        background: C?.pineSoft || "#E3ECE8",
        padding: "2px 7px",
        borderRadius: 4
      }
    }, "+", eur(monthlyVal), "/m")));
  }
  function PlacementDrawer({
    placement,
    categories,
    bankCategories,
    isOpen,
    onClose,
    onSaveNew,
    onCancelNew,
    onCell,
    onRemove,
    isNew
  }) {
    if (!isOpen || !placement) return null;
    const handleDelete = () => {
      if (window.confirm(`Êtes-vous sûr de vouloir supprimer le placement "${placement.label}" ?`)) {
        onRemove(placement.id);
        onClose();
      }
    };
    const handleCloseOrCancel = () => {
      if (isNew) {
        onCancelNew();
      } else {
        onClose();
      }
    };
    const inputStyle = {
      border: `1px solid ${C?.line || "#DED6C4"}`,
      borderRadius: 7,
      padding: "8px 10px",
      fontSize: 14,
      width: 140
    };
    return /*#__PURE__*/React.createElement("div", {
      style: {
        position: "fixed",
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        zIndex: 1000,
        display: "flex",
        justifyContent: "flex-end",
        background: "rgba(0, 0, 0, 0.4)",
        backdropFilter: "blur(2px)",
        animation: "fadeIn 0.2s ease-out"
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        flex: 1
      },
      onClick: handleCloseOrCancel
    }), /*#__PURE__*/React.createElement("div", {
      style: {
        width: 520,
        maxWidth: "90vw",
        height: "100%",
        background: C?.panel || "#FFFFFF",
        boxShadow: "-4px 0 24px rgba(0, 0, 0, 0.15)",
        display: "flex",
        flexDirection: "column",
        position: "relative",
        animation: "slideInRight 0.25s cubic-bezier(0.16, 1, 0.3, 1)"
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        padding: "20px 24px",
        borderBottom: `1px solid ${C?.line || "#DED6C4"}`,
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        background: C?.paper || "#F6F3EC"
      }
    }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("h3", {
      style: {
        margin: 0,
        fontFamily: "'Newsreader', serif",
        fontSize: 20,
        color: C?.ink || "#232A2E",
        fontWeight: 700
      }
    }, isNew ? "Nouveau placement / compte" : "Détails & Édition du placement"), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        marginTop: 2
      }
    }, placement.label || "Saisie des caractéristiques")), /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: handleCloseOrCancel,
      style: {
        border: "none",
        background: C?.panelAlt || "#EFEAE0",
        borderRadius: "50%",
        width: 34,
        height: 34,
        cursor: "pointer",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        fontSize: 16,
        color: C?.inkSoft || "#6B7278",
        fontWeight: 700
      }
    }, "✕")), /*#__PURE__*/React.createElement("div", {
      style: {
        flex: 1,
        overflowY: "auto",
        padding: "24px",
        display: "flex",
        flexDirection: "column",
        gap: 24
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.paper || "#F6F3EC",
        borderRadius: 10,
        padding: 16,
        border: `1px solid ${C?.line || "#DED6C4"}`
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        fontWeight: 700,
        textTransform: "uppercase",
        letterSpacing: 0.5,
        color: C?.pine || "#2F5D50",
        marginBottom: 12
      }
    }, "1. Informations Générales"), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        flexDirection: "column",
        gap: 12
      }
    }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("label", {
      style: {
        display: "block",
        fontSize: 12,
        fontWeight: 600,
        color: C?.ink || "#232A2E",
        marginBottom: 4
      }
    }, "Libellé du compte"), /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 6,
        padding: "4px 8px"
      }
    }, /*#__PURE__*/React.createElement(Field, {
      value: placement.label,
      onChange: v => onCell(placement.id, "label", v),
      placeholder: "Ex: Livret A, PEA..."
    }))), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("label", {
      style: {
        display: "block",
        fontSize: 12,
        fontWeight: 600,
        color: C?.ink || "#232A2E",
        marginBottom: 4
      }
    }, "Catégorie d'actif"), (() => {
      const catTheme = getCategoryTheme(placement.category, categories);
      return /*#__PURE__*/React.createElement("div", {
        style: {
          display: "flex",
          alignItems: "center",
          gap: 8,
          background: C?.panel || "#FFFFFF",
          border: `1px solid ${catTheme.color}55`,
          borderRadius: 6,
          padding: "4px 8px 4px 6px"
        }
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          width: 24,
          height: 24,
          borderRadius: 6,
          flexShrink: 0,
          background: catTheme.color + "1F",
          border: `1px solid ${catTheme.color}44`,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          fontSize: 13
        }
      }, catTheme.icon), /*#__PURE__*/React.createElement("div", {
        style: {
          flex: 1
        }
      }, /*#__PURE__*/React.createElement(Field, {
        type: "select",
        value: placement.category,
        options: (categories || []).map(c => c.name),
        onChange: v => onCell(placement.id, "category", v)
      })));
    })()), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("label", {
      style: {
        display: "flex",
        alignItems: "center",
        gap: 8,
        cursor: "pointer",
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 6,
        padding: "8px 10px"
      }
    }, /*#__PURE__*/React.createElement("input", {
      type: "checkbox",
      checked: !!placement.excludedFromRetirement,
      onChange: e => onCell(placement.id, "excludedFromRetirement", e.target.checked)
    }), /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 12.5,
        color: C?.ink || "#232A2E"
      }
    }, /*#__PURE__*/React.createElement("strong", null, "Hors calcul retraite"), " — appartient à un tiers (ex : assurance-vie d'un enfant)"))), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("label", {
      style: {
        display: "block",
        fontSize: 12,
        fontWeight: 600,
        color: C?.ink || "#232A2E",
        marginBottom: 4
      }
    }, "Notes & commentaires"), /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 6,
        padding: "4px 8px"
      }
    }, /*#__PURE__*/React.createElement(Field, {
      value: placement.notes,
      onChange: v => onCell(placement.id, "notes", v),
      placeholder: "Notes personnelles..."
    }))))), /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.paper || "#F6F3EC",
        borderRadius: 10,
        padding: 16,
        border: `1px solid ${C?.line || "#DED6C4"}`
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        fontWeight: 700,
        textTransform: "uppercase",
        letterSpacing: 0.5,
        color: C?.pine || "#2F5D50",
        marginBottom: 12
      }
    }, "2. Solde actuel & Versements"), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        flexDirection: "column",
        gap: 12
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        gap: 12
      }
    }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("label", {
      style: {
        display: "block",
        fontSize: 12,
        fontWeight: 600,
        color: C?.ink || "#232A2E",
        marginBottom: 4
      }
    }, "Solde actuel (€)"), /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 6,
        padding: "4px 8px"
      }
    }, /*#__PURE__*/React.createElement(Field, {
      type: "number",
      mono: true,
      value: placement.balance,
      onChange: v => onCell(placement.id, "balance", v)
    }))), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("label", {
      style: {
        display: "block",
        fontSize: 12,
        fontWeight: 600,
        color: C?.ink || "#232A2E",
        marginBottom: 4
      }
    }, "Date du solde"), /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 6,
        padding: "4px 8px"
      }
    }, /*#__PURE__*/React.createElement(Field, {
      type: "date",
      value: placement.balanceDate,
      onChange: v => onCell(placement.id, "balanceDate", v)
    })))), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("label", {
      style: {
        display: "block",
        fontSize: 12,
        fontWeight: 600,
        color: C?.ink || "#232A2E",
        marginBottom: 4
      }
    }, "Versement mensuel (€ / mois)"), /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 6,
        padding: "4px 8px"
      }
    }, /*#__PURE__*/React.createElement(Field, {
      type: "number",
      mono: true,
      value: placement.monthly,
      onChange: v => onCell(placement.id, "monthly", v)
    }))), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        gap: 12
      }
    }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("label", {
      style: {
        display: "block",
        fontSize: 11,
        color: C?.inkSoft || "#6B7278",
        marginBottom: 4
      }
    }, "Versements dès le"), /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 6,
        padding: "4px 8px"
      }
    }, /*#__PURE__*/React.createElement(Field, {
      type: "date",
      value: placement.monthlyFrom,
      onChange: v => onCell(placement.id, "monthlyFrom", v)
    }))), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("label", {
      style: {
        display: "block",
        fontSize: 11,
        color: C?.inkSoft || "#6B7278",
        marginBottom: 4
      }
    }, "Versements jusqu'au"), /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 6,
        padding: "4px 8px"
      }
    }, /*#__PURE__*/React.createElement(Field, {
      type: "date",
      value: placement.monthlyUntil,
      onChange: v => onCell(placement.id, "monthlyUntil", v)
    })))), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("label", {
      style: {
        display: "block",
        fontSize: 12,
        fontWeight: 600,
        color: C?.ink || "#232A2E",
        marginBottom: 4
      }
    }, "Catégorie bancaire (pour le pointage)"), /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 6,
        padding: "4px 8px"
      }
    }, /*#__PURE__*/React.createElement("select", {
      value: placement.categoryId || "",
      onChange: e => onCell(placement.id, "categoryId", e.target.value),
      style: {
        ...inputStyle,
        width: "100%",
        fontSize: 12
      }
    }, /*#__PURE__*/React.createElement("option", {
      value: ""
    }, "— Non liée —"), [...(bankCategories || [])].sort((a, b) => (a.label || "").localeCompare(b.label || "", "fr", {
      sensitivity: "base"
    })).map(c => /*#__PURE__*/React.createElement("option", {
      key: c.id,
      value: c.id
    }, c.label))))))), /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.paper || "#F6F3EC",
        borderRadius: 10,
        padding: 16,
        border: `1px solid ${C?.line || "#DED6C4"}`
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        fontWeight: 700,
        textTransform: "uppercase",
        letterSpacing: 0.5,
        color: C?.pine || "#2F5D50",
        marginBottom: 12
      }
    }, "3. Hypothèses de rendement annuel (%)"), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "grid",
        gridTemplateColumns: "1fr 1fr 1fr",
        gap: 10
      }
    }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("label", {
      style: {
        display: "block",
        fontSize: 11,
        fontWeight: 600,
        color: C?.brick || "#A8503C",
        marginBottom: 4
      }
    }, "Pessimiste"), /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 6,
        padding: "4px 8px"
      }
    }, /*#__PURE__*/React.createElement(Field, {
      type: "percent",
      mono: true,
      value: placement.ratePess,
      onChange: v => onCell(placement.id, "ratePess", v)
    }))), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("label", {
      style: {
        display: "block",
        fontSize: 11,
        fontWeight: 600,
        color: C?.pine || "#2F5D50",
        marginBottom: 4
      }
    }, "Correct"), /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 6,
        padding: "4px 8px"
      }
    }, /*#__PURE__*/React.createElement(Field, {
      type: "percent",
      mono: true,
      value: placement.rateCorr,
      onChange: v => onCell(placement.id, "rateCorr", v)
    }))), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("label", {
      style: {
        display: "block",
        fontSize: 11,
        fontWeight: 600,
        color: C?.gold || "#93802E",
        marginBottom: 4
      }
    }, "Optimiste"), /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 6,
        padding: "4px 8px"
      }
    }, /*#__PURE__*/React.createElement(Field, {
      type: "percent",
      mono: true,
      value: placement.rateOpti,
      onChange: v => onCell(placement.id, "rateOpti", v)
    }))))), /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.paper || "#F6F3EC",
        borderRadius: 10,
        padding: 16,
        border: `1px solid ${C?.line || "#DED6C4"}`
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        fontWeight: 700,
        textTransform: "uppercase",
        letterSpacing: 0.5,
        color: C?.pine || "#2F5D50",
        marginBottom: 6
      }
    }, "4. Automatisations & Épargne de précaution"), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11.5,
        color: C?.inkSoft || "#6B7278",
        marginBottom: 12,
        lineHeight: 1.5
      }
    }, "Ces réglages permettent le virement automatique (sweep) et la pause des versements d'épargne en cas de coup dur."), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        flexDirection: "column",
        gap: 12
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        gap: 12
      }
    }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement(FieldHint, {
      label: "Priorité virement auto",
      text: "Ordre d'utilisation du compte (1 = premier)."
    }), /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 6,
        padding: "4px 8px"
      }
    }, /*#__PURE__*/React.createElement(Field, {
      type: "number",
      align: "right",
      value: placement.sweepPriority,
      onChange: v => onCell(placement.id, "sweepPriority", v),
      placeholder: "Ex: 1, 2..."
    }))), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement(FieldHint, {
      label: "Plafond virement auto (€)",
      text: "Montant max recevable via le virement auto."
    }), /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 6,
        padding: "4px 8px"
      }
    }, /*#__PURE__*/React.createElement(Field, {
      type: "number",
      mono: true,
      align: "right",
      value: placement.sweepCap,
      onChange: v => onCell(placement.id, "sweepCap", v),
      placeholder: "Ex: 22950"
    })))), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        gap: 12
      }
    }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement(FieldHint, {
      label: "Seuil d'alerte tampon (€)",
      text: "Si le solde tombe sous ce seuil, une alerte se déclenche."
    }), /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 6,
        padding: "4px 8px"
      }
    }, /*#__PURE__*/React.createElement(Field, {
      type: "number",
      mono: true,
      align: "right",
      value: placement.pauseTriggerBalance,
      onChange: v => onCell(placement.id, "pauseTriggerBalance", v),
      placeholder: "Ex: 3000"
    }))), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement(FieldHint, {
      label: "Priorité pause épargne",
      text: "Ordre de suspension des versements en cas d'alerte."
    }), /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 6,
        padding: "4px 8px"
      }
    }, /*#__PURE__*/React.createElement(Field, {
      type: "number",
      align: "right",
      value: placement.pausePriority,
      onChange: v => onCell(placement.id, "pausePriority", v),
      placeholder: "Ex: 1, 2..."
    }))))))), /*#__PURE__*/React.createElement("div", {
      style: {
        padding: "16px 24px",
        borderTop: `1px solid ${C?.line || "#DED6C4"}`,
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        background: C?.paper || "#F6F3EC"
      }
    }, isNew ? /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: onCancelNew,
      style: {
        border: `1px solid ${C?.line || "#DED6C4"}`,
        background: C?.panelAlt || "#EFEAE0",
        color: C?.inkSoft || "#6B7278",
        borderRadius: 8,
        padding: "10px 16px",
        cursor: "pointer",
        fontSize: 13,
        fontWeight: 600
      }
    }, "Annuler") : /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: handleDelete,
      style: {
        border: "none",
        background: C?.brickSoft || "#F4E4DF",
        color: C?.brick || "#A8503C",
        borderRadius: 8,
        padding: "10px 16px",
        cursor: "pointer",
        fontSize: 13,
        fontWeight: 600,
        display: "flex",
        alignItems: "center",
        gap: 6
      }
    }, "🗑️ Supprimer ce placement"), /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: isNew ? onSaveNew : onClose,
      style: {
        border: "none",
        background: C?.pine || "#2F5D50",
        color: "#FFF",
        borderRadius: 8,
        padding: "10px 24px",
        cursor: "pointer",
        fontSize: 13,
        fontWeight: 600,
        boxShadow: "0 2px 6px rgba(0,0,0,0.1)"
      }
    }, isNew ? "✓ Créer le placement" : "✓ Enregistrer & Fermer"))));
  }
  function PatrimoineView({
    useConstantEuros = false,
    openHelp
  }) {
    const [selectedPlacementId, setSelectedPlacementId] = useState(null);
    const [draftPlacement, setDraftPlacement] = useState(null);
    const [isDrawerOpen, setIsDrawerOpen] = useState(false);
    const [isAddingNew, setIsAddingNew] = useState(false);
    const [model, setModel] = useState(null);
    const [loaded, setLoaded] = useState(false);
    useEffect(() => {
      let cancelled = false;
      const fetchPatrimoine = () => {
        BudgetApi.getPatrimoine({
          useConstantEuros
        }).then(result => {
          if (cancelled) return;
          setModel(result);
          setLoaded(true);
        }).catch(err => {
          console.error("Erreur de chargement du Patrimoine :", err);
          if (!cancelled) setLoaded(true);
        });
      };
      fetchPatrimoine();
      const unsubscribe = BudgetApi.onPatrimoineChanged(fetchPatrimoine);
      return () => {
        cancelled = true;
        unsubscribe();
      };
    }, [useConstantEuros]);
    const placements = model?.placements || [];
    const activePlacement = useMemo(() => {
      if (isAddingNew) return draftPlacement;
      return placements.find(p => p.id === selectedPlacementId) || null;
    }, [placements, selectedPlacementId, isAddingNew, draftPlacement]);
    if (!loaded || !model) {
      return /*#__PURE__*/React.createElement("div", {
        style: {
          color: C?.inkSoft || "#6B7278",
          fontFamily: "sans-serif",
          padding: 24
        }
      }, "Chargement…");
    }
    const {
      transfers,
      loans,
      realEstate,
      assetCategories,
      bankCategories,
      patrimoine
    } = model;
    const handleCardClick = id => {
      setSelectedPlacementId(id);
      setDraftPlacement(null);
      setIsAddingNew(false);
      setIsDrawerOpen(true);
    };
    const handleAddNew = () => {
      BudgetApi.createPatrimoineLigne("placements").then(newPlacement => {
        setDraftPlacement(newPlacement);
        setIsAddingNew(true);
        setIsDrawerOpen(true);
      });
    };
    const handleSaveNew = () => {
      if (draftPlacement) {
        BudgetApi.addPatrimoineLigne("placements", draftPlacement);
      }
      setDraftPlacement(null);
      setIsAddingNew(false);
      setIsDrawerOpen(false);
    };
    const handleCancelNew = () => {
      setDraftPlacement(null);
      setIsAddingNew(false);
      setIsDrawerOpen(false);
    };
    const handleCellChange = (id, field, value) => {
      if (isAddingNew && draftPlacement) {
        setDraftPlacement(prev => ({
          ...prev,
          [field]: value
        }));
      } else {
        BudgetApi.updatePatrimoineLigne("placements", id, field, value);
      }
    };
    return /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement(SectionCard, {
      title: "Évolution du patrimoine — 3 scénarios",
      subtitle: "Somme de tous les placements ci-dessous"
    }, /*#__PURE__*/React.createElement(LineChartJS, {
      data: patrimoine?.totals || [],
      xKey: "year",
      height: 300,
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
    })), /*#__PURE__*/React.createElement(SectionCard, {
      title: /*#__PURE__*/React.createElement("span", null, "Placements & comptes", openHelp && /*#__PURE__*/React.createElement(HelpBadge, {
        sectionKey: "patrimoine",
        badgeId: "placement_drawer",
        onClick: openHelp,
        inline: true
      })),
      subtitle: "Solde actuel, catégorie et versements mensuels — cliquez sur une carte pour voir et modifier les détails"
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "grid",
        gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))",
        gap: 12
      }
    }, placements.map(p => /*#__PURE__*/React.createElement(PlacementCard, {
      key: p.id,
      p: p,
      categories: assetCategories,
      onClick: () => handleCardClick(p.id)
    }))), /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: handleAddNew,
      style: {
        marginTop: 20,
        display: "flex",
        alignItems: "center",
        gap: 6,
        fontSize: 13,
        color: C?.pine || "#2F5D50",
        background: C?.pineSoft || "#E3ECE8",
        border: "none",
        borderRadius: 8,
        padding: "10px 16px",
        cursor: "pointer",
        fontWeight: 600
      }
    }, "+ Ajouter un nouveau placement"), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11.5,
        color: C?.inkSoft || "#6B7278",
        marginTop: 16,
        borderTop: `1px solid ${C?.line || "#DED6C4"}`,
        paddingTop: 12
      }
    }, "💡 ", /*#__PURE__*/React.createElement("em", null, "La catégorie détermine dans quelle classe d'actif ce placement compte sur la page Vue d'ensemble. Cliquez sur n'importe quel placement ci-dessus pour configurer les virements automatiques, plafonds et seuils d'alerte."))), /*#__PURE__*/React.createElement(PlacementDrawer, {
      placement: activePlacement,
      categories: assetCategories,
      bankCategories: bankCategories,
      isOpen: isDrawerOpen,
      onClose: () => setIsDrawerOpen(false),
      onSaveNew: handleSaveNew,
      onCancelNew: handleCancelNew,
      onCell: handleCellChange,
      onRemove: id => BudgetApi.removePatrimoineLigne("placements", id),
      isNew: isAddingNew
    }), /*#__PURE__*/React.createElement(SectionCard, {
      title: "Transferts depuis un placement vers le compte courant",
      subtitle: "Simule un retrait pour financer une grosse dépense"
    }, /*#__PURE__*/React.createElement(EditableTable, {
      columns: [{
        key: "placement",
        label: "Placement",
        type: "select",
        options: placements.map(p => p.label)
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
      rows: transfers,
      onCell: (id, field, value) => BudgetApi.updatePatrimoineLigne("transfers", id, field, value),
      onRemove: id => BudgetApi.removePatrimoineLigne("transfers", id),
      onAdd: () => BudgetApi.addPatrimoineLigne("transfers")
    })), /*#__PURE__*/React.createElement(SectionCard, {
      title: "Crédits & Passif Immobilier",
      subtitle: "Capital Restant Dû, taux et mensualités pour l'amortissement"
    }, /*#__PURE__*/React.createElement(EditableTable, {
      columns: [{
        key: "label",
        label: "Libellé",
        type: "text"
      }, {
        key: "crd",
        label: "CRD actuel (€)",
        type: "number",
        align: "right"
      }, {
        key: "rate",
        label: "Taux hors ass. %",
        type: "percent",
        align: "right"
      }, {
        key: "monthly",
        label: "Mensualité (€)",
        type: "number",
        align: "right"
      }, {
        key: "insurance",
        label: "Assurance (€)",
        type: "number",
        align: "right"
      }, {
        key: "startDate",
        label: "Date CRD",
        type: "date"
      }, {
        key: "endDate",
        label: "Date fin prévue",
        type: "date"
      }],
      rows: loans,
      onCell: (id, field, value) => BudgetApi.updatePatrimoineLigne("loans", id, field, value),
      onRemove: id => BudgetApi.removePatrimoineLigne("loans", id),
      onAdd: () => BudgetApi.addPatrimoineLigne("loans")
    }), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11.5,
        color: C?.inkSoft || "#6B7278",
        marginTop: 16,
        borderTop: `1px solid ${C?.line || "#DED6C4"}`,
        paddingTop: 12
      }
    }, "💡 ", /*#__PURE__*/React.createElement("em", null, "Le CRD et la \"Date CRD\" saisis ici sont votre dernier relevé bancaire de référence : ils ne bougent jamais tout seuls, y compris pour les tranches d'un prêt lissé qui démarrent dans le futur. Le CRD affiché à la date du jour (Vue d'ensemble, export PDF) est calculé automatiquement par amortissement depuis cette référence, sans jamais modifier votre saisie."))), /*#__PURE__*/React.createElement(SectionCard, {
      title: "Actif Immobilier Physique",
      subtitle: "Résidence principale, terrains, nu-propriété — la valorisation annuelle est intégrée au patrimoine net et à la jauge FIRE"
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
        key: "currentValue",
        label: "Valeur estimée (€)",
        type: "number",
        align: "right"
      }, {
        key: "valuationYear",
        label: "Année d'estimation",
        type: "number"
      }, {
        key: "annualGrowthRate",
        label: "Revalorisation % / an",
        type: "percent",
        align: "right"
      }, {
        key: "notes",
        label: "Notes",
        type: "text"
      }],
      rows: realEstate,
      onCell: (id, field, value) => BudgetApi.updatePatrimoineLigne("realEstate", id, field, value),
      onRemove: id => BudgetApi.removePatrimoineLigne("realEstate", id),
      onAdd: () => BudgetApi.addPatrimoineLigne("realEstate")
    })));
  }
  exports.PatrimoineView = PatrimoineView;
  exports.PlacementCard = PlacementCard;
  exports.PlacementDrawer = PlacementDrawer;
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);