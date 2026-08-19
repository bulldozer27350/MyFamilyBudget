/**
 * Composants d'Aide Contextuelle (HelpBadge & HelpModal)
 */
(function (exports) {
  'use strict';

  const {
    useState,
    useEffect,
    useRef
  } = React;
  const {
    C
  } = exports.C ? exports : window.BudgetApp || {};
  const HELP_CONTENT = exports.HELP_CONTENT || window.BudgetApp && window.BudgetApp.HELP_CONTENT || {};

  /**
   * Badge / Bouton d'Aide Contextuelle (❓)
   */
  function HelpBadge({
    sectionKey,
    badgeId,
    onClick,
    inline = false
  }) {
    return /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: e => {
        e.stopPropagation();
        if (onClick) onClick(sectionKey, badgeId);
      },
      title: "Cliquez pour ouvrir l'aide détaillée sur cette section",
      style: {
        display: "inline-flex",
        alignItems: "center",
        justifyContent: "center",
        width: 18,
        height: 18,
        borderRadius: "50%",
        background: C?.pineSoft || "#E3ECE8",
        color: C?.pine || "#2F5D50",
        border: `1px solid ${C?.pine || "#2F5D50"}`,
        fontSize: 11,
        fontWeight: 700,
        cursor: "pointer",
        marginLeft: inline ? 6 : 0,
        transition: "transform 0.15s ease, background 0.15s ease",
        verticalAlign: "middle",
        padding: 0,
        lineHeight: 1
      },
      onMouseEnter: e => {
        e.currentTarget.style.transform = "scale(1.15)";
        e.currentTarget.style.background = C?.pine || "#2F5D50";
        e.currentTarget.style.color = "#fff";
      },
      onMouseLeave: e => {
        e.currentTarget.style.transform = "scale(1)";
        e.currentTarget.style.background = C?.pineSoft || "#E3ECE8";
        e.currentTarget.style.color = C?.pine || "#2F5D50";
      }
    }, "?");
  }

  /**
   * Modale d'Aide Complète et Interactive
   */
  function HelpModal({
    isOpen,
    onClose,
    initialSection = "overview",
    initialBadgeId = null
  }) {
    const [activeTab, setActiveTab] = useState(initialSection);
    const [searchQuery, setSearchQuery] = useState("");
    useEffect(() => {
      if (isOpen) {
        setActiveTab(initialSection);
        if (initialBadgeId) {
          setTimeout(() => {
            const el = document.getElementById(`help_section_${initialBadgeId}`);
            if (el) el.scrollIntoView({
              behavior: "smooth",
              block: "start"
            });
          }, 150);
        }
      }
    }, [isOpen, initialSection, initialBadgeId]);
    if (!isOpen) return null;
    const currentTabContent = HELP_CONTENT[activeTab] || HELP_CONTENT.overview || {
      title: "Aide",
      summary: "",
      sections: []
    };
    const filteredSections = searchQuery.trim() === "" ? currentTabContent.sections || [] : (currentTabContent.sections || []).filter(s => (s.title || "").toLowerCase().includes(searchQuery.toLowerCase()) || (s.content || "").toLowerCase().includes(searchQuery.toLowerCase()));
    const TABS = [{
      key: "overview",
      label: "Vue d'ensemble"
    }, {
      key: "cashflow",
      label: "Trésorerie"
    }, {
      key: "patrimoine",
      label: "Placements"
    }, {
      key: "retraite",
      label: "Retraite"
    }, {
      key: "impots",
      label: "Impôts"
    }, {
      key: "settings",
      label: "Paramètres"
    }, {
      key: "import",
      label: "Import"
    }, {
      key: "pending",
      label: "Opérations"
    }, {
      key: "pointage",
      label: "Pointage"
    }, {
      key: "analyse",
      label: "Analyse"
    }];
    return /*#__PURE__*/React.createElement("div", {
      style: {
        position: "fixed",
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        background: "rgba(35, 42, 46, 0.65)",
        backdropFilter: "blur(3px)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        zIndex: 9999,
        padding: 20
      },
      onClick: onClose
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.paper || "#F6F3EC",
        borderRadius: 12,
        width: "100%",
        maxWidth: 880,
        maxHeight: "88vh",
        display: "flex",
        flexDirection: "column",
        boxShadow: "0 20px 40px rgba(0,0,0,0.25)",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        overflow: "hidden"
      },
      onClick: e => e.stopPropagation()
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.navy || "#28394A",
        color: "#fff",
        padding: "16px 24px",
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center"
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        alignItems: "center",
        gap: 10
      }
    }, /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 22
      }
    }, "📖"), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontFamily: "'Newsreader', serif",
        fontSize: 20,
        fontWeight: 600
      }
    }, "Guide & Aide en Ligne Contextuelle"), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: "#9FB0BE"
      }
    }, "Manuel utilisateur interactif de l'outil budgétaire"))), /*#__PURE__*/React.createElement("button", {
      onClick: onClose,
      style: {
        background: "none",
        border: "none",
        color: "#fff",
        fontSize: 22,
        cursor: "pointer",
        padding: "0 8px",
        opacity: 0.8
      }
    }, "✕")), /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.panelAlt || "#EFEAE0",
        borderBottom: `1px solid ${C?.line || "#DED6C4"}`,
        padding: "12px 24px"
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 12,
        alignItems: "center",
        marginBottom: 10
      }
    }, /*#__PURE__*/React.createElement("input", {
      type: "text",
      placeholder: "🔍 Rechercher une notion (ex: sweep, FIRE, inflation, pause, pointage)...",
      value: searchQuery,
      onChange: e => setSearchQuery(e.target.value),
      style: {
        flex: 1,
        padding: "8px 14px",
        borderRadius: 8,
        border: `1px solid ${C?.line || "#DED6C4"}`,
        fontSize: 13,
        background: C?.panel || "#FFFFFF"
      }
    }), searchQuery && /*#__PURE__*/React.createElement("button", {
      onClick: () => setSearchQuery(""),
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        background: "none",
        border: "none",
        cursor: "pointer"
      }
    }, "Effacer")), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 6,
        overflowX: "auto",
        paddingBottom: 4
      }
    }, TABS.map(tab => /*#__PURE__*/React.createElement("button", {
      key: tab.key,
      onClick: () => {
        setActiveTab(tab.key);
        setSearchQuery("");
      },
      style: {
        padding: "6px 14px",
        borderRadius: 20,
        fontSize: 12,
        fontWeight: 600,
        border: `1px solid ${activeTab === tab.key ? C?.pine || "#2F5D50" : C?.line || "#DED6C4"}`,
        background: activeTab === tab.key ? C?.pine || "#2F5D50" : C?.panel || "#FFFFFF",
        color: activeTab === tab.key ? "#fff" : C?.ink || "#232A2E",
        cursor: "pointer",
        whiteSpace: "nowrap",
        transition: "all 0.15s ease"
      }
    }, tab.label)))), /*#__PURE__*/React.createElement("div", {
      style: {
        flex: 1,
        overflowY: "auto",
        padding: "20px 28px"
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        marginBottom: 20
      }
    }, /*#__PURE__*/React.createElement("h2", {
      style: {
        fontFamily: "'Newsreader', serif",
        fontSize: 22,
        color: C?.pine || "#2F5D50",
        margin: "0 0 6px 0"
      }
    }, currentTabContent.title), /*#__PURE__*/React.createElement("p", {
      style: {
        fontSize: 13.5,
        color: C?.inkSoft || "#6B7278",
        margin: 0,
        fontStyle: "italic"
      }
    }, currentTabContent.summary)), filteredSections.length === 0 ? /*#__PURE__*/React.createElement("div", {
      style: {
        textAlign: "center",
        padding: "30px 0",
        color: C?.inkSoft || "#6B7278",
        fontSize: 14
      }
    }, "Aucun résultat trouvé pour « ", searchQuery, " » dans ce chapitre. Essayez un autre onglet ci-dessus.") : filteredSections.map(sec => /*#__PURE__*/React.createElement("div", {
      key: sec.id,
      id: sec.badgeId ? `help_section_${sec.badgeId}` : undefined,
      style: {
        background: C?.panel || "#FFFFFF",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 10,
        padding: "16px 20px",
        marginBottom: 16,
        boxShadow: "0 2px 4px rgba(0,0,0,0.02)"
      }
    }, /*#__PURE__*/React.createElement("h3", {
      style: {
        fontFamily: "'Newsreader', serif",
        fontSize: 17,
        color: C?.navy || "#28394A",
        marginTop: 0,
        marginBottom: 10,
        display: "flex",
        alignItems: "center",
        gap: 8
      }
    }, /*#__PURE__*/React.createElement("span", null, "📌"), " ", sec.title), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 13,
        color: C?.ink || "#232A2E",
        lineHeight: 1.55,
        whiteSpace: "pre-line"
      }
    }, (sec.content || "").split('\n').map((line, i) => {
      const formattedLine = line.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>').replace(/\*(.*?)\*/g, '<em>$1</em>').replace(/\`(.*?)\`/g, '<code style="background:#EFEAE0;padding:2px 5px;border-radius:4px;font-family:monospace;font-size:12px;">$1</code>');
      return /*#__PURE__*/React.createElement("div", {
        key: i,
        dangerouslySetInnerHTML: {
          __html: formattedLine
        }
      });
    }))))), /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.panelAlt || "#EFEAE0",
        borderTop: `1px solid ${C?.line || "#DED6C4"}`,
        padding: "12px 24px",
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center"
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278"
      }
    }, "💡 ", /*#__PURE__*/React.createElement("em", null, "Le manuel utilisateur complet est également disponible dans ", /*#__PURE__*/React.createElement("code", {
      style: {
        fontSize: 11
      }
    }, "Manuel utilisateur.md"), ".")), /*#__PURE__*/React.createElement("button", {
      onClick: onClose,
      style: {
        padding: "8px 20px",
        background: C?.navy || "#28394A",
        color: "#fff",
        border: "none",
        borderRadius: 6,
        fontSize: 13,
        fontWeight: 600,
        cursor: "pointer"
      }
    }, "Fermer l'aide"))));
  }
  exports.HelpBadge = HelpBadge;
  exports.HelpModal = HelpModal;
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);