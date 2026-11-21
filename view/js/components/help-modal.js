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
  const HELP_VIDEOS = exports.HELP_VIDEOS || window.BudgetApp && window.BudgetApp.HELP_VIDEOS || { chapters: [], items: [] };

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

  /* ───────────────────────────────────────────────────
   *  Couleurs par chapitre (badges, bordures…)
   * ─────────────────────────────────────────────────── */
  const CHAPTER_COLORS = {
    ch0: { bg: "#E8F4FD", border: "#5BAEE0", text: "#1A5C87" },
    ch1: { bg: "#FFF3E0", border: "#F5A623", text: "#7A4B00" },
    ch2: { bg: "#E9F5EC", border: "#4CAF77", text: "#1A5C33" },
    ch3: { bg: "#F3E8FD", border: "#9B59B6", text: "#5A1A87" }
  };

  /* ───────────────────────────────────────────────────
   *  Composant VideoLibrary
   * ─────────────────────────────────────────────────── */
  function VideoLibrary({ onVideoActiveChange }) {
    const chapters = (HELP_VIDEOS && HELP_VIDEOS.chapters) || [];
    const allItems = (HELP_VIDEOS && HELP_VIDEOS.items) || [];

    const [selectedChapter, setSelectedChapter] = useState(null);
    const [currentVideo, setCurrentVideo] = useState(null);
    const [searchQuery, setSearchQuery] = useState("");
    const videoRef = useRef(null);
    const playerContainerRef = useRef(null);

    // Notifier le parent (HelpModal) si une vidéo est active ou non
    useEffect(() => {
      if (onVideoActiveChange) {
        onVideoActiveChange(!!currentVideo);
      }
    }, [currentVideo, onVideoActiveChange]);

    // Fermer le player avec Échap
    useEffect(() => {
      if (!currentVideo) return;
      function onKey(e) {
        if (e.key === "Escape") setCurrentVideo(null);
      }
      document.addEventListener("keydown", onKey);
      return () => document.removeEventListener("keydown", onKey);
    }, [currentVideo]);

    // Remettre la vidéo au début lors d'un changement
    useEffect(() => {
      if (videoRef.current) {
        videoRef.current.load();
      }
    }, [currentVideo]);

    // ─── Filtrage ───────────────────────────────────────
    const q = searchQuery.trim().toLowerCase();
    const filtered = allItems.filter(item => {
      const chapterOk = !selectedChapter || item.chapterId === selectedChapter;
      if (!chapterOk) return false;
      if (!q) return true;
      return (
        item.title.toLowerCase().includes(q) ||
        item.need.toLowerCase().includes(q) ||
        (item.highlights || []).some(h => h.toLowerCase().includes(q)) ||
        (item.keywords || []).some(k => k.toLowerCase().includes(q)) ||
        (item.badge || "").toLowerCase().includes(q)
      );
    });

    // ─── Navigation Précédent / Suivant ─────────────────
    const currentIndex = currentVideo ? filtered.findIndex(v => v.id === currentVideo.id) : -1;
    const hasPrev = currentIndex > 0;
    const hasNext = currentIndex >= 0 && currentIndex < filtered.length - 1;
    const goPrev = () => hasPrev && setCurrentVideo(filtered[currentIndex - 1]);
    const goNext = () => hasNext && setCurrentVideo(filtered[currentIndex + 1]);

    // ─── Comptage par chapitre pour les badges ───────────
    function countForChapter(chId) {
      if (!q) return allItems.filter(i => i.chapterId === chId).length;
      return allItems.filter(i => {
        if (i.chapterId !== chId) return false;
        return (
          i.title.toLowerCase().includes(q) ||
          i.need.toLowerCase().includes(q) ||
          (i.highlights || []).some(h => h.toLowerCase().includes(q)) ||
          (i.keywords || []).some(k => k.toLowerCase().includes(q)) ||
          (i.badge || "").toLowerCase().includes(q)
        );
      }).length;
    }

    // ─── Styles ─────────────────────────────────────────
    const panelBg = C?.panel || "#FFFFFF";
    const panelAlt = C?.panelAlt || "#EFEAE0";
    const ink = C?.ink || "#232A2E";
    const inkSoft = C?.inkSoft || "#6B7278";
    const pine = C?.pine || "#2F5D50";
    const navy = C?.navy || "#28394A";
    const lineBorder = C?.line || "#DED6C4";

    // ─── Carte vidéo ────────────────────────────────────
    function VideoCard({ item }) {
      const col = CHAPTER_COLORS[item.chapterId] || CHAPTER_COLORS.ch0;
      const isPlaying = currentVideo && currentVideo.id === item.id;
      return React.createElement("div", {
        onClick: () => setCurrentVideo(item),
        style: {
          background: panelBg,
          border: `2px solid ${isPlaying ? pine : lineBorder}`,
          borderRadius: 10,
          padding: "14px 16px",
          cursor: "pointer",
          transition: "all 0.15s ease",
          display: "flex",
          flexDirection: "column",
          gap: 8,
          boxShadow: isPlaying ? `0 0 0 3px ${pine}30` : "0 2px 6px rgba(0,0,0,0.04)"
        },
        onMouseEnter: e => { if (!isPlaying) e.currentTarget.style.borderColor = pine; },
        onMouseLeave: e => { if (!isPlaying) e.currentTarget.style.borderColor = lineBorder; }
      },
        // Ligne supérieure : badge chapitre + durée
        React.createElement("div", { style: { display: "flex", justifyContent: "space-between", alignItems: "center" } },
          React.createElement("span", {
            style: {
              fontSize: 11, fontWeight: 700, padding: "2px 8px",
              borderRadius: 20, background: col.bg, color: col.text,
              border: `1px solid ${col.border}`, whiteSpace: "nowrap"
            }
          }, item.badge || ""),
          React.createElement("span", { style: { fontSize: 11, color: inkSoft, whiteSpace: "nowrap" } },
            "⏱ ", item.duration)
        ),
        // Titre
        React.createElement("div", {
          style: { fontWeight: 700, fontSize: 13.5, color: navy, lineHeight: 1.3 }
        }, item.id, " — ", item.title),
        // Besoin
        React.createElement("div", {
          style: {
            fontSize: 12, color: inkSoft, lineHeight: 1.4,
            overflow: "hidden", display: "-webkit-box",
            WebkitLineClamp: 2, WebkitBoxOrient: "vertical"
          }
        }, item.need),
        // Highlights
        React.createElement("ul", { style: { margin: 0, paddingLeft: 16, fontSize: 11.5, color: ink, lineHeight: 1.5 } },
          (item.highlights || []).map((h, i) =>
            React.createElement("li", { key: i, style: { marginBottom: 2 } }, h)
          )
        ),
        // Bouton lecture
        React.createElement("button", {
          onClick: e => { e.stopPropagation(); setCurrentVideo(item); },
          style: {
            alignSelf: "flex-start", marginTop: 4,
            padding: "5px 14px", borderRadius: 20, fontSize: 12, fontWeight: 600,
            background: isPlaying ? pine : panelAlt,
            color: isPlaying ? "#fff" : pine,
            border: `1px solid ${pine}`,
            cursor: "pointer"
          }
        }, isPlaying ? "▶ En lecture" : "▶ Regarder")
      );
    }

    // ─── Lecteur vidéo (overlay) ─────────────────────────
    function VideoPlayer() {
      if (!currentVideo) return null;
      const col = CHAPTER_COLORS[currentVideo.chapterId] || CHAPTER_COLORS.ch0;
      const chapter = chapters.find(c => c.id === currentVideo.chapterId);

      const [isPlaying, setIsPlaying] = useState(true);
      const [currentTime, setCurrentTime] = useState(0);
      const [duration, setDuration] = useState(0);

      // Gestion play/pause & reset lors du changement de vidéo
      useEffect(() => {
        setIsPlaying(true);
        setCurrentTime(0);
        setDuration(0);
      }, [currentVideo]);

      const togglePlay = () => {
        if (!videoRef.current) return;
        if (videoRef.current.paused) {
          videoRef.current.play();
          setIsPlaying(true);
        } else {
          videoRef.current.pause();
          setIsPlaying(false);
        }
      };

      const handleTimeUpdate = () => {
        if (videoRef.current) {
          setCurrentTime(videoRef.current.currentTime);
        }
      };

      const handleLoadedMetadata = () => {
        if (videoRef.current) {
          setDuration(videoRef.current.duration || 0);
        }
      };

      const handleSeek = (e) => {
        const val = parseFloat(e.target.value);
        if (videoRef.current) {
          videoRef.current.currentTime = val;
          setCurrentTime(val);
        }
      };

      const [isFullscreen, setIsFullscreen] = useState(false);

      useEffect(() => {
        const onFsChange = () => {
          setIsFullscreen(!!document.fullscreenElement);
        };
        document.addEventListener("fullscreenchange", onFsChange);
        return () => document.removeEventListener("fullscreenchange", onFsChange);
      }, []);

      const toggleFullscreen = () => {
        if (!playerContainerRef.current) return;
        if (document.fullscreenElement) {
          document.exitFullscreen();
        } else if (playerContainerRef.current.requestFullscreen) {
          playerContainerRef.current.requestFullscreen();
        }
      };

      const formatTime = (sec) => {
        if (!sec || isNaN(sec)) return "0:00";
        const m = Math.floor(sec / 60);
        const s = Math.floor(sec % 60);
        return `${m}:${s < 10 ? '0' : ''}${s}`;
      };

      return React.createElement("div", {
        ref: playerContainerRef,
        style: {
          position: isFullscreen ? "fixed" : "absolute",
          inset: 0,
          zIndex: isFullscreen ? 999999 : 10,
          background: "rgba(22, 28, 33, 0.99)",
          display: "flex", flexDirection: "column",
          borderRadius: isFullscreen ? 0 : 12,
          overflowY: "auto"
        }
      },
        // Header du player
        React.createElement("div", {
          style: {
            padding: isFullscreen ? "14px 24px" : "10px 18px",
            display: "flex", justifyContent: "space-between",
            alignItems: "center", background: "rgba(0,0,0,0.4)", flexShrink: 0
          }
        },
          React.createElement("div", { style: { display: "flex", alignItems: "center", gap: 10 } },
            React.createElement("span", {
              style: {
                fontSize: 11, fontWeight: 700, padding: "2px 10px",
                borderRadius: 20, background: col.bg, color: col.text,
                border: `1px solid ${col.border}`
              }
            }, chapter ? (chapter.icon ? chapter.icon + " " + chapter.title : chapter.title) : ""),
            React.createElement("span", { style: { color: "#fff", fontWeight: 700, fontSize: isFullscreen ? 16 : 14 } },
              currentVideo.id, " — ", currentVideo.title)
          ),
          React.createElement("div", { style: { display: "flex", gap: 8 } },
            React.createElement("button", {
              onClick: toggleFullscreen,
              title: isFullscreen ? "Quitter le plein écran" : "Plein écran",
              style: {
                background: "rgba(255,255,255,0.12)", border: "none", color: "#fff",
                fontSize: 13, cursor: "pointer", borderRadius: 6, padding: "3px 10px"
              }
            }, isFullscreen ? "✕ Réduire" : "⛶ Plein écran"),
            React.createElement("button", {
              onClick: () => {
                if (isFullscreen && document.exitFullscreen) document.exitFullscreen();
                setCurrentVideo(null);
              },
              title: "Fermer la vidéo",
              style: {
                background: "rgba(255,255,255,0.12)", border: "none", color: "#fff",
                fontSize: 18, cursor: "pointer", borderRadius: 6, padding: "3px 10px"
              }
            }, "✕")
          )
        ),

        // Vidéo SANS contrôles natifs (aucun ombrage sur l'image)
        React.createElement("div", {
          style: {
            position: "relative",
            width: "100%",
            flex: isFullscreen ? "1 1 0" : "1 1 auto",
            minHeight: isFullscreen ? "60vh" : "48vh",
            maxHeight: isFullscreen ? "78vh" : "58vh",
            background: "#000",
            display: "flex",
            justifyContent: "center",
            alignItems: "center",
            cursor: "pointer",
            flexShrink: 0
          },
          onClick: togglePlay
        },
          React.createElement("video", {
            ref: videoRef,
            controls: false,
            autoPlay: true,
            onTimeUpdate: handleTimeUpdate,
            onLoadedMetadata: handleLoadedMetadata,
            onPlay: () => setIsPlaying(true),
            onPause: () => setIsPlaying(false),
            onEnded: () => setIsPlaying(false),
            style: {
              width: "100%",
              height: "100%",
              objectFit: "contain",
              display: "block"
            },
            src: `videos/${currentVideo.file}`
          })
        ),

        // Barre de contrôle personnalisée placée EN DESSOUS de la vidéo
        React.createElement("div", {
          style: {
            display: "flex", alignItems: "center", gap: 12,
            padding: "8px 18px", background: "rgba(18, 25, 30, 0.96)",
            borderBottom: "1px solid rgba(255,255,255,0.08)", flexShrink: 0
          }
        },
          // Bouton Play / Pause
          React.createElement("button", {
            onClick: togglePlay,
            title: isPlaying ? "Pause (Espace)" : "Lecture (Espace)",
            style: {
              background: pine, border: "none", color: "#fff",
              borderRadius: "50%", width: 32, height: 32,
              display: "flex", alignItems: "center", justifyContent: "center",
              cursor: "pointer", fontSize: 13, flexShrink: 0
            }
          }, isPlaying ? "⏸" : "▶"),

          // Horodatage actuel
          React.createElement("span", {
            style: { color: "#C5D0D8", fontSize: 11.5, minWidth: 36, textAlign: "right", fontFamily: "monospace" }
          }, formatTime(currentTime)),

          // Barre de progression (slider)
          React.createElement("input", {
            type: "range",
            min: 0,
            max: duration || 100,
            step: 0.1,
            value: currentTime,
            onChange: handleSeek,
            style: {
              flex: 1, cursor: "pointer", accentColor: pine,
              height: 4, borderRadius: 2
            }
          }),

          // Durée totale
          React.createElement("span", {
            style: { color: "#8E9DA8", fontSize: 11.5, minWidth: 36, fontFamily: "monospace" }
          }, formatTime(duration)),

          // Bouton Plein écran
          React.createElement("button", {
            onClick: toggleFullscreen,
            title: isFullscreen ? "Quitter le plein écran" : "Plein écran",
            style: {
              background: "transparent", border: "1px solid rgba(255,255,255,0.2)",
              color: "#C5D0D8", borderRadius: 4, padding: "3px 8px",
              cursor: "pointer", fontSize: 11, flexShrink: 0
            }
          }, isFullscreen ? "✕ Réduire" : "⛶")
        ),

        // Infos + Navigation
        React.createElement("div", {
          style: {
            padding: "10px 18px",
            display: "flex", flexDirection: "column", gap: 8,
            flexShrink: 0
          }
        },
          React.createElement("div", { style: { fontSize: 12.5, color: "#D0D8DF", lineHeight: 1.4 } },
            currentVideo.need),
          React.createElement("ul", { style: { margin: 0, paddingLeft: 18, color: "#A8C0CC", fontSize: 11.5 } },
            (currentVideo.highlights || []).map((h, i) =>
              React.createElement("li", { key: i, style: { marginBottom: 3 } }, h)
            )
          ),
          // Navigation Préc/Suiv
          React.createElement("div", {
            style: {
              display: "flex", gap: 10, justifyContent: "space-between",
              marginTop: 4, paddingTop: 8, borderTop: "1px solid rgba(255,255,255,0.1)"
            }
          },
            React.createElement("button", {
              onClick: goPrev, disabled: !hasPrev,
              style: {
                padding: "6px 16px", borderRadius: 20, fontSize: 12, fontWeight: 600,
                border: "1px solid rgba(255,255,255,0.25)",
                background: hasPrev ? "rgba(255,255,255,0.1)" : "transparent",
                color: hasPrev ? "#fff" : "rgba(255,255,255,0.3)",
                cursor: hasPrev ? "pointer" : "default"
              }
            }, "◀ Précédente"),
            React.createElement("span", {
              style: { alignSelf: "center", fontSize: 12, color: "rgba(255,255,255,0.4)" }
            }, `${currentIndex + 1} / ${filtered.length}`),
            React.createElement("button", {
              onClick: goNext, disabled: !hasNext,
              style: {
                padding: "6px 16px", borderRadius: 20, fontSize: 12, fontWeight: 600,
                border: "1px solid rgba(255,255,255,0.25)",
                background: hasNext ? "rgba(255,255,255,0.1)" : "transparent",
                color: hasNext ? "#fff" : "rgba(255,255,255,0.3)",
                cursor: hasNext ? "pointer" : "default"
              }
            }, "Suivante ▶")
          )
        )
      );
    }

    // ─── Rendu principal ────────────────────────────────
    return React.createElement("div", { style: { display: "flex", flex: 1, minHeight: 0, height: "100%", position: "relative", overflow: "hidden" } },
      // Lecteur superposé
      React.createElement(VideoPlayer, null),

      // ─── Sidebar filtres ───────────────────────────────
      React.createElement("div", {
        style: {
          width: 220, flexShrink: 0, borderRight: `1px solid ${lineBorder}`,
          display: "flex", flexDirection: "column", gap: 6,
          padding: "14px 12px", overflowY: "auto"
        }
      },
        React.createElement("div", { style: { fontSize: 11, fontWeight: 700, color: inkSoft, marginBottom: 4, textTransform: "uppercase", letterSpacing: 0.5 } }, "Chapitres"),
        // Bouton Tous
        React.createElement("button", {
          onClick: () => setSelectedChapter(null),
          style: {
            textAlign: "left", padding: "8px 10px", borderRadius: 8, fontSize: 12,
            fontWeight: !selectedChapter ? 700 : 500,
            background: !selectedChapter ? pine : panelAlt,
            color: !selectedChapter ? "#fff" : ink,
            border: `1px solid ${!selectedChapter ? pine : lineBorder}`,
            cursor: "pointer", display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8
          }
        },
          React.createElement("span", { style: { flex: 1 } }, "🎬 Toutes"),
          React.createElement("span", {
            style: {
              fontSize: 10, fontWeight: 700, padding: "1px 6px", borderRadius: 10,
              background: !selectedChapter ? "rgba(255,255,255,0.2)" : panelBg,
              color: !selectedChapter ? "#fff" : inkSoft,
              flexShrink: 0
            }
          }, allItems.length)
        ),
        // Boutons par chapitre
        chapters.map(ch => {
          const col = CHAPTER_COLORS[ch.id] || CHAPTER_COLORS.ch0;
          const isActive = selectedChapter === ch.id;
          const count = countForChapter(ch.id);
          return React.createElement("button", {
            key: ch.id,
            onClick: () => setSelectedChapter(isActive ? null : ch.id),
            style: {
              textAlign: "left", padding: "8px 10px", borderRadius: 8, fontSize: 12,
              fontWeight: isActive ? 700 : 500,
              background: isActive ? col.bg : panelAlt,
              color: isActive ? col.text : ink,
              border: `1px solid ${isActive ? col.border : lineBorder}`,
              cursor: "pointer", display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8
            }
          },
            React.createElement("span", { style: { flex: 1, lineHeight: 1.35 } }, ch.icon ? `${ch.icon} ${ch.title}` : ch.title),
            React.createElement("span", {
              style: {
                fontSize: 10, fontWeight: 700, padding: "1px 6px", borderRadius: 10,
                background: isActive ? col.border : panelBg,
                color: isActive ? "#fff" : inkSoft,
                flexShrink: 0
              }
            }, count)
          );
        }),
        // Champ de recherche
        React.createElement("div", { style: { marginTop: 10 } },
          React.createElement("div", { style: { fontSize: 11, fontWeight: 700, color: inkSoft, marginBottom: 6, textTransform: "uppercase", letterSpacing: 0.5 } }, "Recherche"),
          React.createElement("input", {
            type: "text",
            placeholder: "🔍 Chercher...",
            value: searchQuery,
            onChange: e => setSearchQuery(e.target.value),
            style: {
              width: "100%", boxSizing: "border-box",
              padding: "7px 10px", borderRadius: 8, fontSize: 12,
              border: `1px solid ${lineBorder}`, background: panelBg, color: ink
            }
          }),
          searchQuery && React.createElement("button", {
            onClick: () => setSearchQuery(""),
            style: {
              marginTop: 4, fontSize: 11, color: inkSoft,
              background: "none", border: "none", cursor: "pointer", padding: 0
            }
          }, "✕ Effacer")
        )
      ),

      // ─── Grille des cartes ──────────────────────────────
      React.createElement("div", {
        style: { flex: 1, overflowY: "auto", padding: "14px 16px" }
      },
        filtered.length === 0
          ? React.createElement("div", {
              style: { textAlign: "center", padding: "40px 0", color: inkSoft, fontSize: 14 }
            }, "Aucune vidéo ne correspond à votre recherche.")
          : React.createElement("div", {
              style: {
                display: "grid",
                gridTemplateColumns: "repeat(auto-fill, minmax(240px, 1fr))",
                gap: 14
              }
            },
              filtered.map(item =>
                React.createElement(VideoCard, { key: item.id, item })
              )
            )
      )
    );
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
    const [isVideoPlaying, setIsVideoPlaying] = useState(false);
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
      key: "demarrage",
      label: "🚀 Démarrer"
    }, {
      key: "videos",
      label: "🎬 Vidéos"
    }, {
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
        maxWidth: activeTab === "videos" ? (isVideoPlaying ? 1280 : 1150) : 880,
        height: activeTab === "videos" ? (isVideoPlaying ? "96vh" : "92vh") : "auto",
        maxHeight: isVideoPlaying ? "98vh" : "92vh",
        display: "flex",
        flexDirection: "column",
        boxShadow: "0 20px 40px rgba(0,0,0,0.25)",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        overflow: "hidden"
      },
      onClick: e => e.stopPropagation()
    }, (!isVideoPlaying || activeTab !== "videos") && /*#__PURE__*/React.createElement("div", {
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
    }, "✕")), (!isVideoPlaying || activeTab !== "videos") && /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.panelAlt || "#EFEAE0",
        borderBottom: `1px solid ${C?.line || "#DED6C4"}`,
        padding: "12px 24px"
      }
    }, activeTab !== "videos" && /*#__PURE__*/React.createElement("div", {
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
    }, tab.label)))),
    /* ── Zone de contenu : VideoLibrary ou sections textuelles ── */
    activeTab === "videos"
      ? /*#__PURE__*/React.createElement("div", {
          style: { flex: 1, minHeight: 0, display: "flex", flexDirection: "column", overflow: "hidden", position: "relative" }
        }, /*#__PURE__*/React.createElement(VideoLibrary, { onVideoActiveChange: setIsVideoPlaying }))
      : /*#__PURE__*/React.createElement("div", {
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
    }))))), activeTab !== "videos" && /*#__PURE__*/React.createElement("div", {
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