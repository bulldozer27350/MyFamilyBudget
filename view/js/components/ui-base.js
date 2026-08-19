/**
 * Composants React de base pour les formulaires, cartes et tableaux
 */
(function (exports) {
  'use strict';

  const {
    useState
  } = React;
  const C = exports.C || window.BudgetApp && window.BudgetApp.C || {};
  function Field({
    type = "text",
    value,
    onChange,
    options,
    align = "left",
    mono,
    placeholder
  }) {
    const base = {
      width: "100%",
      background: "transparent",
      border: "none",
      outline: "none",
      fontSize: 13,
      color: C.ink || "#232A2E",
      textAlign: align,
      fontFamily: mono ? "'IBM Plex Mono', monospace" : "inherit",
      padding: "6px 4px"
    };
    if (type === "select") {
      const opts = (options || []).map(o => typeof o === "object" && o !== null ? o : {
        value: o,
        label: o
      });
      return /*#__PURE__*/React.createElement("select", {
        style: {
          ...base,
          cursor: "pointer"
        },
        value: value,
        onChange: e => onChange(e.target.value)
      }, opts.map(o => /*#__PURE__*/React.createElement("option", {
        key: o.value,
        value: o.value
      }, o.label)));
    }
    if (type === "color") {
      return /*#__PURE__*/React.createElement("div", {
        style: {
          display: "flex",
          alignItems: "center",
          gap: 6
        }
      }, /*#__PURE__*/React.createElement("input", {
        type: "color",
        value: value || "#2F5D50",
        onChange: e => onChange(e.target.value),
        style: {
          border: "none",
          width: 28,
          height: 26,
          padding: 0,
          cursor: "pointer",
          background: "transparent",
          borderRadius: 4
        }
      }), /*#__PURE__*/React.createElement("span", {
        style: {
          fontSize: 11,
          fontFamily: "'IBM Plex Mono', monospace",
          color: C.inkSoft || "#6B7278"
        }
      }, value || "#2F5D50"));
    }
    return /*#__PURE__*/React.createElement("input", {
      type: type,
      style: base,
      value: value ?? "",
      placeholder: placeholder,
      onChange: e => onChange(e.target.value)
    });
  }
  function FieldHint({
    label,
    text,
    color
  }) {
    const [open, setOpen] = useState(false);
    return /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        alignItems: "center",
        gap: 5,
        marginBottom: 4
      }
    }, /*#__PURE__*/React.createElement("label", {
      style: {
        fontSize: 11,
        color: color || C.inkSoft || "#6B7278"
      }
    }, label), /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => setOpen(o => !o),
      "aria-label": "Explication",
      style: {
        border: `1px solid ${C.line || "#DED6C4"}`,
        background: open ? C.panelAlt || "#EFEAE0" : "transparent",
        color: C.inkSoft || "#6B7278",
        width: 15,
        height: 15,
        borderRadius: "50%",
        cursor: "pointer",
        fontSize: 10,
        lineHeight: "13px",
        padding: 0,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        flexShrink: 0
      }
    }, "ⓘ")), open && /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11.5,
        color: C.inkSoft || "#6B7278",
        background: C.panelAlt || "#EFEAE0",
        border: `1px solid ${C.line || "#DED6C4"}`,
        borderRadius: 6,
        padding: "8px 10px",
        marginBottom: 6,
        lineHeight: 1.55
      }
    }, text));
  }
  function IconBtn({
    onClick,
    title,
    children,
    danger
  }) {
    return /*#__PURE__*/React.createElement("button", {
      onClick: onClick,
      title: title,
      style: {
        border: "none",
        background: "transparent",
        cursor: "pointer",
        color: danger ? C.brick || "#A8503C" : C.inkSoft || "#6B7278",
        padding: 6,
        borderRadius: 6,
        display: "flex",
        alignItems: "center",
        fontSize: 15
      },
      onMouseEnter: e => e.currentTarget.style.background = danger ? C.brickSoft || "#F4E4DF" : C.panelAlt || "#EFEAE0",
      onMouseLeave: e => e.currentTarget.style.background = "transparent"
    }, children);
  }
  function SectionCard({
    title,
    subtitle,
    right,
    children
  }) {
    return /*#__PURE__*/React.createElement("div", {
      style: {
        background: C.panel || "#FFFFFF",
        border: `1px solid ${C.line || "#DED6C4"}`,
        borderRadius: 10,
        marginBottom: 20
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-start",
        padding: "16px 20px",
        borderBottom: `1px solid ${C.line || "#DED6C4"}`
      }
    }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontFamily: "'Newsreader', serif",
        fontSize: 18,
        color: C.ink || "#232A2E",
        fontWeight: 600
      }
    }, title), subtitle && /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12.5,
        color: C.inkSoft || "#6B7278",
        marginTop: 3
      }
    }, subtitle)), right), /*#__PURE__*/React.createElement("div", {
      style: {
        padding: 20
      }
    }, children));
  }
  function EditableTable({
    columns,
    rows,
    onCell,
    onAdd,
    onRemove
  }) {
    return /*#__PURE__*/React.createElement("div", {
      style: {
        overflowX: "auto"
      }
    }, /*#__PURE__*/React.createElement("table", {
      style: {
        width: "100%",
        borderCollapse: "collapse",
        fontSize: 13
      }
    }, /*#__PURE__*/React.createElement("thead", null, /*#__PURE__*/React.createElement("tr", null, columns.map(c => /*#__PURE__*/React.createElement("th", {
      key: c.key,
      style: {
        textAlign: c.align || "left",
        fontSize: 11,
        letterSpacing: 0.4,
        textTransform: "uppercase",
        color: C.inkSoft || "#6B7278",
        fontWeight: 600,
        padding: "0 6px 8px",
        borderBottom: `1px solid ${C.line || "#DED6C4"}`
      }
    }, c.label)), /*#__PURE__*/React.createElement("th", {
      style: {
        width: 32,
        borderBottom: `1px solid ${C.line || "#DED6C4"}`
      }
    }))), /*#__PURE__*/React.createElement("tbody", null, (rows || []).map(row => /*#__PURE__*/React.createElement("tr", {
      key: row.id,
      style: {
        borderBottom: `1px solid ${C.line || "#DED6C4"}`
      }
    }, columns.map(c => {
      let val = row[c.key];
      let onChange = v => onCell(row.id, c.key, v);
      if (c.type === "percent") {
        const isUnset = val === undefined || val === null || val === "";
        val = isUnset ? "" : ((Number(val) || 0) * 100).toFixed(1);
        onChange = v => onCell(row.id, c.key, v === "" || v === null ? null : (parseFloat(v) || 0) / 100);
      }
      return /*#__PURE__*/React.createElement("td", {
        key: c.key,
        style: {
          padding: "2px 6px"
        }
      }, /*#__PURE__*/React.createElement(Field, {
        type: c.type === "percent" ? "number" : c.type,
        value: val,
        onChange: onChange,
        options: c.options,
        align: c.align,
        mono: c.type === "number" || c.type === "percent",
        placeholder: c.placeholder
      }));
    }), /*#__PURE__*/React.createElement("td", null, /*#__PURE__*/React.createElement(IconBtn, {
      title: "Supprimer la ligne",
      danger: true,
      onClick: () => onRemove(row.id)
    }, "✕")))))), /*#__PURE__*/React.createElement("button", {
      onClick: onAdd,
      style: {
        marginTop: 12,
        display: "flex",
        alignItems: "center",
        gap: 6,
        fontSize: 12.5,
        color: C.pine || "#2F5D50",
        background: C.pineSoft || "#E3ECE8",
        border: "none",
        borderRadius: 7,
        padding: "7px 12px",
        cursor: "pointer",
        fontWeight: 600
      }
    }, "+ Ajouter une ligne"));
  }
  function KPI({
    label,
    value,
    accent,
    sub
  }) {
    return /*#__PURE__*/React.createElement("div", {
      style: {
        background: C.panel || "#FFFFFF",
        border: `1px solid ${C.line || "#DED6C4"}`,
        borderRadius: 10,
        padding: "16px 18px",
        flex: 1,
        minWidth: 180
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11.5,
        textTransform: "uppercase",
        letterSpacing: 0.5,
        color: C.inkSoft || "#6B7278",
        fontWeight: 600
      }
    }, label), /*#__PURE__*/React.createElement("div", {
      style: {
        fontFamily: "'IBM Plex Mono', monospace",
        fontSize: 24,
        color: accent || C.ink || "#232A2E",
        marginTop: 6,
        fontWeight: 600
      }
    }, value), sub && /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: C.inkSoft || "#6B7278",
        marginTop: 4
      }
    }, sub));
  }
  exports.Field = Field;
  exports.FieldHint = FieldHint;
  exports.IconBtn = IconBtn;
  exports.SectionCard = SectionCard;
  exports.EditableTable = EditableTable;
  exports.KPI = KPI;
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);