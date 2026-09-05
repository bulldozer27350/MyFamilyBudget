/**
 * Layout unifié de l'application (Sidebar, Navigation, Gestion JSON, Monnaie constante, Aide)
 */
(function (exports) {
  'use strict';

  const {
    useState,
    useRef,
    useEffect,
    useCallback
  } = React;
  const {
    C
  } = exports.C ? exports : window.BudgetApp || {};
  const {
    HelpModal
  } = exports.HelpModal ? exports : window.BudgetApp || {};
  const NAV_ITEMS = [{
    key: "overview",
    label: "Vue d'ensemble",
    href: "overview.html",
    icon: "overview"
  }, {
    key: "cashflow",
    label: "Trésorerie",
    href: "cashflow.html",
    icon: "cashflow"
  }, {
    key: "patrimoine",
    label: "Patrimoine",
    href: "patrimoine.html",
    icon: "patrimoine"
  }, {
    key: "retraite",
    label: "Retraite",
    href: "retraite.html",
    icon: "retraite"
  }, {
    key: "impots",
    label: "Impôts",
    href: "impots.html",
    icon: "impots"
  }, {
    key: "settings",
    label: "Paramètres",
    href: "settings.html",
    icon: "settings"
  }];
  const NAV_ANALYSE_ITEMS = [{
    key: "import",
    label: "Import",
    href: "import.html",
    icon: "inbox"
  }, {
    key: "pending",
    label: "Opérations en cours",
    href: "pending.html",
    icon: "pending"
  }, {
    key: "pointage",
    label: "Pointage",
    href: "pointage.html",
    icon: "pointage"
  }, {
    key: "analyse",
    label: "Analyse",
    href: "analyse.html",
    icon: "analyse"
  }];
  const navBtnStyle = {
    display: "flex",
    alignItems: "center",
    gap: 8,
    fontSize: 12,
    color: "#B9C6D0",
    background: "transparent",
    border: "1px solid #46586A",
    borderRadius: 7,
    padding: "8px 10px",
    cursor: "pointer",
    width: "100%",
    textAlign: "left",
    transition: "all 0.15s ease"
  };

  const iconProps = {
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: "currentColor",
    strokeWidth: 1.6,
    strokeLinecap: "round",
    strokeLinejoin: "round"
  };

  function Icon(name) {
    switch (name) {
      case "overview":
        return React.createElement("svg", iconProps,
          React.createElement("rect", { x: 3, y: 3, width: 7, height: 7, rx: 1.5 }),
          React.createElement("rect", { x: 14, y: 3, width: 7, height: 7, rx: 1.5 }),
          React.createElement("rect", { x: 3, y: 14, width: 7, height: 7, rx: 1.5 }),
          React.createElement("rect", { x: 14, y: 14, width: 7, height: 7, rx: 1.5 })
        );
      case "cashflow":
        return React.createElement("svg", iconProps,
          React.createElement("rect", { x: 2.5, y: 6, width: 19, height: 13, rx: 2 }),
          React.createElement("path", { d: "M2.5 10h19" }),
          React.createElement("circle", { cx: 17, cy: 14.5, r: 1.3, fill: "currentColor", stroke: "none" })
        );
      case "patrimoine":
        return React.createElement("svg", iconProps,
          React.createElement("path", { d: "M3 17l6-6 4 4 8-9" }),
          React.createElement("path", { d: "M15 6h6v6" })
        );
      case "retraite":
        return React.createElement("svg", iconProps,
          React.createElement("path", { d: "M12 3v3" }),
          React.createElement("path", { d: "M5.6 8.6l1.7 1.7M18.4 8.6l-1.7 1.7" }),
          React.createElement("path", { d: "M3 14h18" }),
          React.createElement("path", { d: "M6 14a6 6 0 0112 0" })
        );
      case "impots":
        return React.createElement("svg", iconProps,
          React.createElement("path", { d: "M6 2.5h9l4 4v15a1 1 0 01-1 1H6a1 1 0 01-1-1v-18a1 1 0 011-1z" }),
          React.createElement("path", { d: "M15 2.5V7h4" }),
          React.createElement("path", { d: "M8 12h8M8 15.5h8M8 8.5h3" })
        );
      case "settings":
        return React.createElement("svg", iconProps,
          React.createElement("path", { d: "M4 7h9M17 7h3" }),
          React.createElement("circle", { cx: 14, cy: 7, r: 2 }),
          React.createElement("path", { d: "M4 12h3M11 12h9" }),
          React.createElement("circle", { cx: 8, cy: 12, r: 2 }),
          React.createElement("path", { d: "M4 17h11M19 17h1" }),
          React.createElement("circle", { cx: 17, cy: 17, r: 2 })
        );
      case "inbox":
        return React.createElement("svg", iconProps,
          React.createElement("path", { d: "M3 12l3-8h12l3 8" }),
          React.createElement("path", { d: "M3 12v6a1 1 0 001 1h16a1 1 0 001-1v-6" }),
          React.createElement("path", { d: "M3 12h5l1.5 2.5h5L16 12h5" })
        );
      case "pending":
        return React.createElement("svg", iconProps,
          React.createElement("circle", { cx: 12, cy: 12, r: 8.5 }),
          React.createElement("path", { d: "M12 7.5V12l3 2" })
        );
      case "pointage":
        return React.createElement("svg", iconProps,
          React.createElement("circle", { cx: 12, cy: 12, r: 8.5 }),
          React.createElement("path", { d: "M8 12.5l2.5 2.5L16 9.5" })
        );
      case "analyse":
        return React.createElement("svg", iconProps,
          React.createElement("circle", { cx: 10.5, cy: 10.5, r: 6.5 }),
          React.createElement("path", { d: "M20 20l-4.8-4.8" })
        );
      case "help":
        return React.createElement("svg", iconProps,
          React.createElement("path", { d: "M4 5.5A2.5 2.5 0 016.5 3H12v16H6.5A2.5 2.5 0 004 16.5v-11z" }),
          React.createElement("path", { d: "M20 5.5A2.5 2.5 0 0017.5 3H12v16h5.5A2.5 2.5 0 0020 16.5v-11z" })
        );
      case "upload":
        return React.createElement("svg", iconProps,
          React.createElement("path", { d: "M12 15V4" }),
          React.createElement("path", { d: "M7.5 8.5L12 4l4.5 4.5" }),
          React.createElement("path", { d: "M4 15v3a2 2 0 002 2h12a2 2 0 002-2v-3" })
        );
      case "download":
        return React.createElement("svg", iconProps,
          React.createElement("path", { d: "M12 4v11" }),
          React.createElement("path", { d: "M7.5 10.5L12 15l4.5-4.5" }),
          React.createElement("path", { d: "M4 15v3a2 2 0 002 2h12a2 2 0 002-2v-3" })
        );
      case "refresh":
        return React.createElement("svg", iconProps,
          React.createElement("path", { d: "M20 11A8 8 0 105.5 16.5" }),
          React.createElement("path", { d: "M20 5v6h-6" })
        );
      default:
        return null;
    }
  }

  const SIDEBAR_COLLAPSED_KEY = "budgetapp.sidebarCollapsed";

  function AppLayout({
    currentSection = "overview",
    status = "",
    useConstantEuros = false,
    setUseConstantEuros = () => {},
    exportJSON = () => {},
    importJSON = () => {},
    resetData = () => {},
    openHelpExternal = null,
    children
  }) {
    const [helpState, setHelpState] = useState({
      isOpen: false,
      section: currentSection,
      badgeId: null
    });
    const [collapsed, setCollapsed] = useState(() => {
      try {
        return localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === "1";
      } catch (e) {
        return false;
      }
    });

    // ── Mobile overlay state ──────────────────────────────────────────
    const [mobileOpen, setMobileOpen] = useState(false);
    const sidebarRef = useRef(null);

    /**
     * Version de build affichee en pied de menu (voir tache "afficher la
     * version deployee"). Le fichier assets/build-info.json est genere
     * uniquement par le job GitHub Actions build-and-push (cf. ci-cd.yml) au
     * moment de la construction de l'image Docker : il n'existe pas en dev
     * local ni pendant les tests e2e, d'ou le catch silencieux ci-dessous
     * (aucune version affichee plutot qu'une erreur bloquante).
     */
    const [buildInfo, setBuildInfo] = useState(null);
    useEffect(() => {
      fetch('assets/build-info.json')
        .then(res => (res.ok ? res.json() : null))
        .then(data => data && setBuildInfo(data))
        .catch(() => {});
    }, []);

    /** Fermeture intelligente : clic sur le backdrop ou en dehors de la sidebar */
    const closeMobileMenu = useCallback(() => {
      setMobileOpen(false);
    }, []);

    useEffect(() => {
      if (!mobileOpen) return;

      // Fermer si clic en dehors de la sidebar (sur le backdrop ou le main)
      function handleOutsideClick(e) {
        if (sidebarRef.current && !sidebarRef.current.contains(e.target)) {
          closeMobileMenu();
        }
      }

      // Fermer si clic sur un lien de navigation (changement de page)
      function handleNavClick(e) {
        const link = e.target.closest('a.sidebar-nav-link');
        if (link) {
          closeMobileMenu();
        }
      }

      document.addEventListener('pointerdown', handleOutsideClick, true);
      document.addEventListener('click', handleNavClick, true);

      // Bloquer le scroll du body pendant que la sidebar est ouverte
      document.body.style.overflow = 'hidden';

      return () => {
        document.removeEventListener('pointerdown', handleOutsideClick, true);
        document.removeEventListener('click', handleNavClick, true);
        document.body.style.overflow = '';
      };
    }, [mobileOpen, closeMobileMenu]);
    // ─────────────────────────────────────────────────────────────────

    const toggleCollapsed = () => {
      setCollapsed(prev => {
        const next = !prev;
        try {
          localStorage.setItem(SIDEBAR_COLLAPSED_KEY, next ? "1" : "0");
        } catch (e) {}
        return next;
      });
    };
    const importRef = useRef(null);
    const openHelp = (sectionKey = currentSection, badgeId = null) => {
      setHelpState({
        isOpen: true,
        section: sectionKey,
        badgeId
      });
    };
    const closeHelp = () => {
      setHelpState(prev => ({
        ...prev,
        isOpen: false
      }));
    };
    const handleFileChange = e => {
      const file = e.target.files[0];
      if (file) {
        importJSON(file);
      }
      e.target.value = "";
    };

    // ── Icône hamburger SVG ───────────────────────────────────────────
    const HamburgerIcon = () => React.createElement("svg", {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      strokeWidth: 2,
      strokeLinecap: "round",
      strokeLinejoin: "round"
    },
      React.createElement("line", { x1: 3, y1: 7, x2: 21, y2: 7 }),
      React.createElement("line", { x1: 3, y1: 12, x2: 21, y2: 12 }),
      React.createElement("line", { x1: 3, y1: 17, x2: 21, y2: 17 })
    );
    // ─────────────────────────────────────────────────────────────────

    return React.createElement("div", {
      className: "app-container",
      style: {
        minHeight: "100vh",
        background: C?.paper || "#F6F3EC",
        color: C?.ink || "#232A2E"
      }
    },

    /* ── Backdrop mobile (derrière la sidebar, devant le contenu) ── */
    React.createElement("div", {
      className: `mobile-sidebar-backdrop${mobileOpen ? " visible" : ""}`,
      "aria-hidden": "true",
      onClick: closeMobileMenu
    }),

    /* ── Bouton hamburger flottant (mobile uniquement via CSS) ── */
    React.createElement("button", {
      type: "button",
      className: "mobile-hamburger-btn",
      "aria-label": "Ouvrir le menu de navigation",
      "aria-expanded": mobileOpen,
      onClick: () => setMobileOpen(true)
    }, React.createElement(HamburgerIcon, null)),

    /* ── Sidebar ── */
    React.createElement("aside", {
      ref: sidebarRef,
      className: `app-sidebar ${collapsed ? "collapsed" : ""} ${mobileOpen ? "mobile-open" : ""}`.trim()
    }, 
    React.createElement("div", {
      className: "sidebar-header"
    }, !collapsed && 
    React.createElement("div", null, 
      React.createElement("div", {
      className: "sidebar-title"
    }, "MyFamilyBudget"), 
    React.createElement("div", {
      className: "sidebar-subtitle"
    }, "Budget & patrimoine familial")), 
    React.createElement("button", {
      type: "button",
      onClick: toggleCollapsed,
      className: "sidebar-toggle-btn",
      title: collapsed ? "Étendre le menu" : "Réduire le menu"
    }, collapsed ? "»" : "«")), 
    React.createElement("div", {
      className: "sidebar-section-title"
    }, "Analyse du réel"), NAV_ANALYSE_ITEMS.map(item => {
      const isActive = currentSection === item.key;
      return React.createElement("a", {
        key: item.key,
        href: item.href,
        className: `sidebar-nav-link ${isActive ? "active" : ""}`,
        title: item.label
      }, 
      React.createElement("span", {
        className: "sidebar-nav-icon"
      }, Icon(item.icon)), 
      React.createElement("span", {
        className: "sidebar-nav-label"
      }, item.label));
    }), 
    React.createElement("div", {
      className: "sidebar-divider"
    }),
    React.createElement("div", {
      className: "sidebar-section-title"
    }, "Prévisions & planification"), NAV_ITEMS.map(item => {
      const isActive = currentSection === item.key;
      return React.createElement("a", {
        key: item.key,
        href: item.href,
        className: `sidebar-nav-link ${isActive ? "active" : ""}`,
        title: item.label
      }, 
      React.createElement("span", {
        className: "sidebar-nav-icon"
      }, Icon(item.icon)), 
      React.createElement("span", {
        className: "sidebar-nav-label"
      }, item.label));
    }), 
    React.createElement("div", {
      style: {
        marginTop: 20,
        paddingTop: 18,
        borderTop: "1px solid #46586A"
      }
    }, 
    React.createElement("button", {
      type: "button",
      onClick: () => openHelp(currentSection),
      className: "sidebar-btn-help",
      title: "Aide en Ligne"
    }, 
    React.createElement("span", {
      className: "sidebar-btn-icon"
    }, Icon("help")), 
    React.createElement("span", {
      className: "sidebar-btn-label"
    }, "Aide en Ligne"))), !collapsed && 
    React.createElement("div", {
      style: {
        marginTop: 14,
        paddingTop: 14,
        borderTop: "1px solid #46586A"
      }
    }, 
    React.createElement("label", {
      style: {
        display: "flex",
        alignItems: "center",
        gap: 10,
        cursor: "pointer",
        fontSize: 13,
        color: "#fff",
        fontWeight: 600
      }
    }, 
    React.createElement("input", {
      type: "checkbox",
      checked: useConstantEuros,
      onChange: e => setUseConstantEuros(e.target.checked),
      style: {
        cursor: "pointer",
        accentColor: C?.pine || "#2F5D50"
      }
    }), "Monnaie constante"), 
    React.createElement("div", {
      style: {
        fontSize: 11,
        color: "#9FB0BE",
        marginTop: 6,
        paddingLeft: 23,
        lineHeight: 1.4
      }
    }, "Déduit l'inflation pour visualiser votre pouvoir d'achat réel futur.")), 
    React.createElement("div", {
      style: {
        marginTop: "auto",
        paddingTop: 20,
        display: "flex",
        flexDirection: "column",
        gap: 8
      }
    }, 
    React.createElement("div", {
      className: "sidebar-status"
    }, status), 
    React.createElement("button", {
      type: "button",
      onClick: exportJSON,
      style: navBtnStyle,
      className: "sidebar-btn-secondary",
      title: "Exporter (JSON)"
    }, React.createElement("span", {
      className: "sidebar-btn-icon"
    }, Icon("upload")), React.createElement("span", {
      className: "sidebar-btn-label"
    }, "Exporter (JSON)")),
    React.createElement("button", {
      type: "button",
      onClick: () => importRef.current && importRef.current.click(),
      style: navBtnStyle,
      className: "sidebar-btn-secondary",
      title: "Importer (JSON)"
    }, React.createElement("span", {
      className: "sidebar-btn-icon"
    }, Icon("download")), React.createElement("span", {
      className: "sidebar-btn-label"
    }, "Importer (JSON)")),
    React.createElement("input", {
      type: "file",
      accept: "application/json",
      ref: importRef,
      onChange: handleFileChange,
      style: {
        display: "none"
      }
    }), 
    React.createElement("button", {
      type: "button",
      onClick: resetData,
      style: navBtnStyle,
      className: "sidebar-btn-secondary",
      title: "Réinitialiser"
    }, React.createElement("span", {
      className: "sidebar-btn-icon"
    }, Icon("refresh")), React.createElement("span", {
      className: "sidebar-btn-label"
    }, "Réinitialiser")), buildInfo && !collapsed && React.createElement("div", {
      className: "sidebar-build-version",
      title: `Construit le ${buildInfo.builtAt || "?"}`,
      style: {
        fontSize: 11,
        color: "#9FB0BE",
        textAlign: "center",
        marginTop: 10,
        opacity: 0.7
      }
    }, buildInfo.version || "?"))),

    /* ── Zone de contenu principale ── */
    React.createElement("main", {
      className: "app-main"
    }, typeof children === "function" ? children({
      openHelp
    }) : children), 

    /* ── Modal d'aide ── */
    React.createElement(HelpModal, {
      isOpen: helpState.isOpen,
      onClose: closeHelp,
      initialSection: helpState.section,
      initialBadgeId: helpState.badgeId
    }));
  }
  exports.AppLayout = AppLayout;
  exports.NAV_ITEMS = NAV_ITEMS;
  exports.NAV_ANALYSE_ITEMS = NAV_ANALYSE_ITEMS;
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);