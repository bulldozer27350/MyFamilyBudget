/**
 * Wrappers Chart.js et graphiques interactifs (LineChartJS, AllocationChartJS, InteractiveTreasuryChart)
 */
(function (exports) {
  'use strict';

  const {
    useState,
    useEffect,
    useMemo,
    useRef
  } = React;
  const {
    C,
    eur
  } = (typeof window !== 'undefined' && window.BudgetApp) ? window.BudgetApp : (exports.C ? exports : {});
  const {
    calculateDetailedFinancialTimeline
  } = (typeof window !== 'undefined' && window.BudgetApp) ? window.BudgetApp : (exports.calculateDetailedFinancialTimeline ? exports : {});
  const pillStyle = (active, color) => ({
    fontSize: 11.5,
    fontWeight: 600,
    padding: "4px 10px",
    borderRadius: 14,
    border: `1.5px solid ${color}`,
    background: active ? color : "transparent",
    color: active ? "#FFFFFF" : color,
    cursor: "pointer",
    transition: "all 0.15s ease"
  });
  const btnSmStyle = {
    fontSize: 11,
    padding: "3px 8px",
    borderRadius: 5,
    border: `1px solid ${C?.line || "#DED6C4"}`,
    background: C?.panelAlt || "#EFEAE0",
    color: C?.ink || "#232A2E",
    cursor: "pointer"
  };

  /**
   * Graphique linéaire simple Chart.js
   */
  function LineChartJS({
    data,
    xKey,
    series,
    height = 280,
    zeroLine
  }) {
    const canvasRef = useRef(null);
    const chartRef = useRef(null);
    useEffect(() => {
      if (!canvasRef.current || !data || data.length === 0) return;
      const labels = data.map(d => d[xKey]);
      const datasets = (series || []).map(s => ({
        label: s.label,
        data: data.map(d => d[s.key]),
        borderColor: s.color,
        backgroundColor: s.fill ? s.color + "33" : "transparent",
        fill: !!s.fill,
        tension: 0.3,
        pointRadius: 0,
        borderWidth: s.width || 2.5
      }));
      if (zeroLine) {
        datasets.push({
          label: "Zéro",
          data: data.map(() => 0),
          borderColor: C?.brick || "#A8503C",
          borderDash: [5, 5],
          pointRadius: 0,
          borderWidth: 1
        });
      }
      if (chartRef.current) chartRef.current.destroy();
      chartRef.current = new Chart(canvasRef.current.getContext("2d"), {
        type: "line",
        data: {
          labels,
          datasets
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          interaction: {
            mode: "index",
            intersect: false
          },
          plugins: {
            legend: {
              display: series.length > 1,
              labels: {
                font: {
                  size: 12
                },
                boxWidth: 14
              }
            },
            tooltip: {
              callbacks: {
                label: ctx => ctx.dataset.label + ": " + eur(ctx.parsed.y)
              }
            }
          },
          scales: {
            x: {
              grid: {
                display: false
              },
              ticks: {
                font: {
                  size: 11
                }
              }
            },
            y: {
              grid: {
                color: C?.line || "#DED6C4"
              },
              ticks: {
                font: {
                  size: 11
                },
                callback: v => eur(v)
              }
            }
          }
        }
      });
      return () => {
        if (chartRef.current) chartRef.current.destroy();
      };
    }, [JSON.stringify(data), JSON.stringify(series)]);
    return /*#__PURE__*/React.createElement("div", {
      style: {
        height
      }
    }, /*#__PURE__*/React.createElement("canvas", {
      ref: canvasRef
    }));
  }

  /**
   * Graphique de répartition d'actifs (camembert ou histogramme)
   */
  function AllocationChartJS({
    allocation,
    mode,
    height = 260
  }) {
    const canvasRef = useRef(null);
    const chartRef = useRef(null);
    useEffect(() => {
      if (!canvasRef.current || !allocation || allocation.length === 0) return;
      if (chartRef.current) chartRef.current.destroy();
      const labels = allocation.map(a => a.label);
      const values = allocation.map(a => a.amount);
      const colors = allocation.map(a => a.color);
      const total = values.reduce((s, v) => s + v, 0);
      const config = mode === "pie" ? {
        type: "pie",
        data: {
          labels,
          datasets: [{
            data: values,
            backgroundColor: colors,
            borderColor: C?.panel || "#FFFFFF",
            borderWidth: 2
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: {
            legend: {
              position: "right",
              labels: {
                font: {
                  size: 11.5
                },
                boxWidth: 12
              }
            },
            tooltip: {
              callbacks: {
                label: ctx => ` ${ctx.label} : ${eur(ctx.parsed)} (${total ? (ctx.parsed / total * 100).toFixed(1) : "0"} %)`
              }
            }
          }
        }
      } : {
        type: "bar",
        data: {
          labels,
          datasets: [{
            data: values,
            backgroundColor: colors,
            borderRadius: 4
          }]
        },
        options: {
          indexAxis: "y",
          responsive: true,
          maintainAspectRatio: false,
          plugins: {
            legend: {
              display: false
            },
            tooltip: {
              callbacks: {
                label: ctx => eur(ctx.parsed.x)
              }
            }
          },
          scales: {
            x: {
              grid: {
                color: C?.line || "#DED6C4"
              },
              ticks: {
                font: {
                  size: 11
                },
                callback: v => eur(v)
              }
            },
            y: {
              grid: {
                display: false
              },
              ticks: {
                font: {
                  size: 11.5
                }
              }
            }
          }
        }
      };
      chartRef.current = new Chart(canvasRef.current.getContext("2d"), config);
      return () => {
        if (chartRef.current) chartRef.current.destroy();
      };
    }, [JSON.stringify(allocation), mode]);
    return /*#__PURE__*/React.createElement("div", {
      style: {
        height
      }
    }, /*#__PURE__*/React.createElement("canvas", {
      ref: canvasRef
    }));
  }

  /**
   * Graphique interactif de trésorerie avec zoom molette et navigation temporelle
   */
  function InteractiveTreasuryChart({
    data,
    years,
    height = 340,
    useConstantEuros = false
  }) {
    const containerRef = useRef(null);
    const canvasRef = useRef(null);
    const chartRef = useRef(null);
    const [scenario, setScenario] = useState("corr");
    const [visibleKeys, setVisibleKeys] = useState(null); // null = toutes visibles
    const [minTime, setMinTime] = useState(null);
    const [maxTime, setMaxTime] = useState(null);
    const [isDragging, setIsDragging] = useState(false);
    const dragRef = useRef({
      startX: 0,
      minTime: 0,
      maxTime: 0
    });
    const timeline = useMemo(() => {
      const calcFn = exports.calculateDetailedFinancialTimeline || window.BudgetApp && window.BudgetApp.calculateDetailedFinancialTimeline || calculateDetailedFinancialTimeline;
      return calcFn(data, years, scenario, useConstantEuros);
    }, [data, years, scenario, useConstantEuros]);
    const fullBounds = useMemo(() => {
      if (!timeline.yearly || timeline.yearly.length === 0) return {
        min: 0,
        max: 0
      };
      const min = timeline.yearly[0].timestamp;
      const max = timeline.yearly[timeline.yearly.length - 1].timestamp;
      return {
        min,
        max
      };
    }, [timeline]);
    useEffect(() => {
      if (!fullBounds.min || !fullBounds.max) return;
      if (minTime == null || maxTime == null || minTime < fullBounds.min || maxTime > fullBounds.max + 86400000) {
        setMinTime(fullBounds.min);
        setMaxTime(fullBounds.max);
      }
    }, [fullBounds]);
    const accountSeries = useMemo(() => {
      const list = [{
        key: "totalAvoirs",
        label: "Total des avoirs",
        color: C?.navy || "#28394A",
        width: 3.5
      }, {
        key: "cash",
        label: "Trésorerie disponible (Compte courant)",
        color: C?.pine || "#2F5D50",
        width: 2.5
      }];
      const placements = data.placements || [];
      const colors = ["#93802E", "#A8503C", "#6B3FA0", "#17A2B8", "#D9534F", "#4A7C59", "#8E44AD", "#D35400"];
      placements.forEach((p, idx) => {
        list.push({
          key: p.label,
          label: p.label,
          color: colors[idx % colors.length],
          width: 2
        });
      });
      return list;
    }, [data?.placements]);

    // Toutes les clés disponibles pour ce jeu de données
    const allKeys = useMemo(() => accountSeries.map(s => s.key), [accountSeries]);

    // L'ensemble réel de clés visibles (null signifie toutes)
    const effectiveVisible = useMemo(() => {
      if (visibleKeys === null) return new Set(allKeys);
      return visibleKeys;
    }, [visibleKeys, allKeys]);

    // Logique de clic sur une courbe :
    // - Toutes visibles         → solo sur la clé cliquée
    // - Seule visible + cliquée → revenir à toutes
    // - Visible parmi plusieurs → la masquer
    // - Masquée                 → l'ajouter aux visibles
    const handleSeriesClick = (key) => {
      const allVisible = visibleKeys === null;
      const isCurrentlyVisible = allVisible || visibleKeys.has(key);
      const visibleCount = allVisible ? allKeys.length : visibleKeys.size;

      if (allVisible) {
        setVisibleKeys(new Set([key]));
      } else if (isCurrentlyVisible && visibleCount === 1) {
        setVisibleKeys(null);
      } else if (isCurrentlyVisible) {
        const next = new Set(visibleKeys);
        next.delete(key);
        setVisibleKeys(next);
      } else {
        const next = new Set(visibleKeys);
        next.add(key);
        setVisibleKeys(next);
      }
    };

    const currentSpanMs = (maxTime || fullBounds.max) - (minTime || fullBounds.min);
    const currentSpanDays = currentSpanMs / (86400 * 1000);
    let activeGranularity = "yearly";
    let datasetSource = timeline.yearly;
    if (currentSpanDays <= 60) {
      activeGranularity = "daily";
      datasetSource = timeline.daily;
    } else if (currentSpanDays <= 1095) {
      activeGranularity = "monthly";
      datasetSource = timeline.monthly;
    }
    const visiblePoints = useMemo(() => {
      if (!datasetSource || minTime == null || maxTime == null) return [];
      return datasetSource.filter(pt => pt.timestamp >= minTime && pt.timestamp <= maxTime);
    }, [datasetSource, minTime, maxTime]);
    useEffect(() => {
      if (!canvasRef.current || visiblePoints.length === 0) return;
      // Prepare labels and series
      const labels = visiblePoints.map(pt => pt.label);
      const filteredSeries = accountSeries.filter(s => effectiveVisible.has(s.key));
      const datasets = filteredSeries.map(s => ({
        label: s.label,
        data: visiblePoints.map(pt => pt[s.key] ?? 0),
        borderColor: s.color,
        backgroundColor: s.key === "cash" ? (C?.pine || "#2F5D50") + "22" : "transparent",
        fill: s.key === "cash",
        tension: 0.2,
        pointRadius: visiblePoints.length <= 31 ? 3 : 0,
        pointHoverRadius: 5,
        borderDash: s.dash || [],
        borderWidth: s.width || 2
      }));
      // Build annotation objects for pivot date and today
      const annotations = {};
      const getAnnotationPos = (targetDate) => {
        if (!targetDate || !visiblePoints || visiblePoints.length === 0) return null;
        const targetTime = typeof targetDate === 'string' ? new Date(targetDate).getTime() : targetDate.getTime();
        if (isNaN(targetTime)) return null;
        if (targetTime < minTime || targetTime > maxTime) return null;

        if (activeGranularity === "daily") {
          const targetISO = typeof targetDate === 'string' ? targetDate.slice(0, 10) : targetDate.toISOString().slice(0, 10);
          const idx = visiblePoints.findIndex(pt => pt.dateISO === targetISO);
          if (idx !== -1) return idx;
          let bestIdx = 0, bestDiff = Infinity;
          visiblePoints.forEach((pt, i) => {
            const diff = Math.abs(pt.timestamp - targetTime);
            if (diff < bestDiff) {
              bestDiff = diff;
              bestIdx = i;
            }
          });
          return bestIdx;
        } else if (activeGranularity === "monthly") {
          const d = typeof targetDate === 'string' ? new Date(targetDate) : targetDate;
          const tY = d.getFullYear(), tM = d.getMonth() + 1, tD = d.getDate();
          const daysInM = new Date(tY, tM, 0).getDate();
          const idx = visiblePoints.findIndex(pt => pt.year === tY && pt.month === tM);
          if (idx !== -1) {
            return idx + (tD - 1) / daysInM;
          }
        } else {
          // yearly
          const d = typeof targetDate === 'string' ? new Date(targetDate) : targetDate;
          const tY = d.getFullYear(), tM = d.getMonth(), tD = d.getDate();
          const idx = visiblePoints.findIndex(pt => pt.year === tY);
          if (idx !== -1) {
            return idx + (tM + tD / 30) / 12;
          }
        }
        return null;
      };

      if (data?.settings?.pivotDate) {
        const pivotPos = getAnnotationPos(data.settings.pivotDate);
        if (pivotPos !== null) {
          annotations.pivot = {
            type: "line",
            scaleID: "x",
            value: pivotPos,
            borderColor: C?.brick || "#A8503C",
            borderWidth: 2,
            borderDash: [6, 6],
            label: {
              display: true,
              content: "Pivot",
              position: "start",
              backgroundColor: C?.brick || "#A8503C",
              color: "#FFFFFF",
              font: {
                size: 11,
                weight: "bold"
              },
              padding: 4
            }
          };
        }
      }

      const today = new Date();
      const todayISO = today.toISOString().slice(0, 10);
      const pivotISO = data?.settings?.pivotDate ? String(data.settings.pivotDate).slice(0, 10) : null;
      if (todayISO !== pivotISO) {
        const todayPos = getAnnotationPos(today);
        if (todayPos !== null) {
          annotations.today = {
            type: "line",
            scaleID: "x",
            value: todayPos,
            borderColor: C?.pine || "#2F5D50",
            borderWidth: 2,
            label: {
              display: true,
              content: "Aujourd'hui",
              position: "start",
              backgroundColor: C?.pine || "#2F5D50",
              color: "#FFFFFF",
              font: {
                size: 11,
                weight: "bold"
              },
              padding: 4
            }
          };
        }
      }

      if (chartRef.current) chartRef.current.destroy();
      // Register annotation plugin if available
      if (typeof Chart !== 'undefined' && Chart.register) {
        if (typeof ChartAnnotation !== 'undefined') {
          Chart.register(ChartAnnotation);
        } else if (typeof window !== 'undefined' && window['chartjs-plugin-annotation']) {
          Chart.register(window['chartjs-plugin-annotation']);
        }
      }
      chartRef.current = new Chart(canvasRef.current.getContext("2d"), {
        type: "line",
        data: {
          labels,
          datasets
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          animation: false,
          interaction: {
            mode: "index",
            intersect: false
          },
          plugins: {
            legend: {
              display: true,
              position: "top",
              labels: {
                font: {
                  size: 11.5
                },
                boxWidth: 14,
                usePointStyle: true
              },
              onClick: (e, legendItem) => {
                const clickedKey = filteredSeries[legendItem.datasetIndex]?.key;
                if (clickedKey) handleSeriesClick(clickedKey);
              }
            },
            tooltip: {
              callbacks: {
                label: ctx => ctx.dataset.label + ": " + eur(ctx.parsed.y)
              }
            },
            annotation: {
              annotations
            }
          },
          scales: {
            x: {
              grid: {
                display: false
              },
              ticks: {
                font: {
                  size: 11
                },
                maxRotation: 0
              }
            },
            y: {
              grid: {
                color: C?.line || "#DED6C4"
              },
              ticks: {
                font: {
                  size: 11
                },
                callback: v => eur(v)
              }
            }
          }
        }
      });
      return () => {
        if (chartRef.current) chartRef.current.destroy();
      };
    }, [visiblePoints, effectiveVisible, accountSeries]);
    useEffect(() => {
      const el = containerRef.current;
      if (!el) return;
      const handleWheel = e => {
        e.preventDefault();
        if (!fullBounds.min || !fullBounds.max) return;
        const rect = el.getBoundingClientRect();
        const mouseX = e.clientX - rect.left;
        const ratio = Math.max(0, Math.min(1, mouseX / rect.width));
        const curMin = minTime || fullBounds.min;
        const curMax = maxTime || fullBounds.max;
        const curSpan = curMax - curMin;
        const factor = e.deltaY > 0 ? 1.25 : 0.8;
        const minSpanAllowed = 7 * 86400 * 1000;
        const maxSpanAllowed = fullBounds.max - fullBounds.min;
        let newSpan = curSpan * factor;
        if (newSpan < minSpanAllowed) newSpan = minSpanAllowed;
        if (newSpan > maxSpanAllowed) newSpan = maxSpanAllowed;
        const mouseTimestamp = curMin + ratio * curSpan;
        let newMin = mouseTimestamp - ratio * newSpan;
        let newMax = mouseTimestamp + (1 - ratio) * newSpan;
        if (newMin < fullBounds.min) {
          newMin = fullBounds.min;
          newMax = Math.min(fullBounds.max, newMin + newSpan);
        }
        if (newMax > fullBounds.max) {
          newMax = fullBounds.max;
          newMin = Math.max(fullBounds.min, newMax - newSpan);
        }
        setMinTime(newMin);
        setMaxTime(newMax);
      };
      el.addEventListener("wheel", handleWheel, {
        passive: false
      });
      return () => el.removeEventListener("wheel", handleWheel);
    }, [minTime, maxTime, fullBounds]);
    const handlePointerDown = e => {
      if (!fullBounds.min || !fullBounds.max) return;
      setIsDragging(true);
      dragRef.current = {
        startX: e.clientX,
        minTime: minTime || fullBounds.min,
        maxTime: maxTime || fullBounds.max
      };
      e.currentTarget.setPointerCapture(e.pointerId);
    };
    const handlePointerMove = e => {
      if (!isDragging) return;
      const containerWidth = containerRef.current?.clientWidth || 800;
      const deltaX = e.clientX - dragRef.current.startX;
      const span = dragRef.current.maxTime - dragRef.current.minTime;
      const msPerPx = span / containerWidth;
      const deltaMs = -deltaX * msPerPx;
      let newMin = dragRef.current.minTime + deltaMs;
      let newMax = dragRef.current.maxTime + deltaMs;
      if (newMin < fullBounds.min) {
        const shift = fullBounds.min - newMin;
        newMin += shift;
        newMax += shift;
      }
      if (newMax > fullBounds.max) {
        const shift = newMax - fullBounds.max;
        newMin -= shift;
        newMax -= shift;
      }
      setMinTime(newMin);
      setMaxTime(newMax);
    };
    const handlePointerUp = e => {
      setIsDragging(false);
      try {
        e.currentTarget.releasePointerCapture(e.pointerId);
      } catch (_) {}
    };
    const setQuickRange = yearsCount => {
      if (!fullBounds.min || !fullBounds.max) return;
      if (yearsCount === "all") {
        setMinTime(fullBounds.min);
        setMaxTime(fullBounds.max);
      } else {
        const start = fullBounds.min;
        const targetMax = start + yearsCount * 365.25 * 86400 * 1000;
        setMinTime(start);
        setMaxTime(Math.min(fullBounds.max, targetMax));
      }
    };
    const granLabel = activeGranularity === "yearly" ? "📅 Vue par année (zoom > 3 ans)" : activeGranularity === "monthly" ? "🗓️ Vue par mois (zoom 2 mois – 3 ans)" : "📆 Vue par jour (zoom < 2 mois)";
    const isAllVisible = visibleKeys === null;

    return /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        flexWrap: "wrap",
        gap: 10,
        marginBottom: 14
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 6,
        flexWrap: "wrap",
        alignItems: "center"
      }
    }, /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 11.5,
        color: C?.inkSoft || "#6B7278",
        fontWeight: 600
      }
    }, "Filtrer par compte :"), /*#__PURE__*/React.createElement("button", {
      onClick: () => setVisibleKeys(null),
      style: pillStyle(isAllVisible, C?.navy || "#28394A")
    }, "Tous"), accountSeries.map(acc => /*#__PURE__*/React.createElement("button", {
      key: acc.key,
      onClick: () => handleSeriesClick(acc.key),
      style: pillStyle(effectiveVisible.has(acc.key), acc.color)
    }, acc.label))), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 6,
        alignItems: "center"
      }
    }, /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 11.5,
        color: C?.inkSoft || "#6B7278",
        fontWeight: 600
      }
    }, "Scénario placements :"), /*#__PURE__*/React.createElement("select", {
      value: scenario,
      onChange: e => setScenario(e.target.value),
      style: {
        padding: "4px 8px",
        borderRadius: 6,
        border: `1px solid ${C?.line || "#DED6C4"}`,
        fontSize: 12,
        background: C?.panel || "#FFFFFF",
        cursor: "pointer"
      }
    }, /*#__PURE__*/React.createElement("option", {
      value: "pess"
    }, "Pessimiste"), /*#__PURE__*/React.createElement("option", {
      value: "corr"
    }, "Correct (défaut)"), /*#__PURE__*/React.createElement("option", {
      value: "opti"
    }, "Optimiste")))), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        marginBottom: 10,
        fontSize: 11.5,
        color: C?.inkSoft || "#6B7278"
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontWeight: 600,
        color: C?.pine || "#2F5D50"
      }
    }, granLabel), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 6,
        alignItems: "center"
      }
    }, /*#__PURE__*/React.createElement("span", null, "Zoom rapide :"), /*#__PURE__*/React.createElement("button", {
      onClick: () => setQuickRange("all"),
      style: btnSmStyle
    }, "Tout"), /*#__PURE__*/React.createElement("button", {
      onClick: () => setQuickRange(10),
      style: btnSmStyle
    }, "10 ans"), /*#__PURE__*/React.createElement("button", {
      onClick: () => setQuickRange(5),
      style: btnSmStyle
    }, "5 ans"), /*#__PURE__*/React.createElement("button", {
      onClick: () => setQuickRange(1),
      style: btnSmStyle
    }, "1 an"))), /*#__PURE__*/React.createElement("div", {
      ref: containerRef,
      onPointerDown: handlePointerDown,
      onPointerMove: handlePointerMove,
      onPointerUp: handlePointerUp,
      onPointerCancel: handlePointerUp,
      style: {
        height,
        position: "relative",
        cursor: isDragging ? "grabbing" : "grab",
        userSelect: "none",
        touchAction: "none"
      }
    }, /*#__PURE__*/React.createElement("canvas", {
      ref: canvasRef
    })), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11,
        color: C?.inkSoft || "#6B7278",
        marginTop: 8,
        textAlign: "right"
      }
    }, "💡 ", /*#__PURE__*/React.createElement("em", null, "Cliquez sur une courbe ou sur la légende pour l'isoler ou la masquer. Zoomez à la molette pour changer d'échelle et glissez horizontalement pour naviguer dans le temps.")));
  }
  exports.LineChartJS = LineChartJS;
  exports.AllocationChartJS = AllocationChartJS;
  exports.InteractiveTreasuryChart = InteractiveTreasuryChart;
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);