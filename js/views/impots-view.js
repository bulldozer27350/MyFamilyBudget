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
    EditableTable,
    KPI
  } = exports.SectionCard ? exports : window.BudgetApp || {};
  const {
    LineChartJS
  } = exports.LineChartJS ? exports : window.BudgetApp || {};

  function ImpotsView({
    openHelp
  }) {
    const [impotsData, setImpotsData] = React.useState(null);
    const [loading, setLoading] = React.useState(true);
    const [error, setError] = React.useState(null);

    // Charger les données via l'API asynchrone
    React.useEffect(() => {
      const loadImpotsData = async () => {
        try {
          setLoading(true);
          const data = await BudgetApp.BudgetApi.getImpots();
          setImpotsData(data);
          setError(null);
        } catch (err) {
          console.error("Erreur de chargement des données impôts:", err);
          setError(err);
        } finally {
          setLoading(false);
        }
      };

      loadImpotsData();

      // S'abonner aux changements
      const unsubscribe = BudgetApp.BudgetApi.onImpotsChanged(() => {
        loadImpotsData();
      });

      return unsubscribe;
    }, []);

    // Gestionnaires d'événements
    const handleSetCell = (listKey) => (id, field, value) => {
      BudgetApp.BudgetApi.updateImpotsLigne(listKey, id, field, value).then(() => {
        BudgetApp.BudgetApi.getImpots().then(setImpotsData);
      });
    };

    const handleAddRow = (listKey, rowFactory) => {
      BudgetApp.BudgetApi.addImpotsLigne(listKey, rowFactory).then(() => {
        BudgetApp.BudgetApi.getImpots().then(setImpotsData);
      });
    };

    const handleRemoveRow = (listKey) => (id) => {
      BudgetApp.BudgetApi.removeImpotsLigne(listKey, id).then(() => {
        BudgetApp.BudgetApi.getImpots().then(setImpotsData);
      });
    };

    const handleUpdate = (field, value) => {
      BudgetApp.BudgetApi.updateImpotsSettings(field, value).then(() => {
        BudgetApp.BudgetApi.getImpots().then(setImpotsData);
      });
    };

    const handleResetBrackets = () => {
      if (window.confirm("Réinitialiser les tranches d'imposition au barème légal officiel français (0%, 11%, 30%, 41%, 45%) ?")) {
        BudgetApp.BudgetApi.resetDefaultTaxBrackets().then(() => {
          BudgetApp.BudgetApi.getImpots().then(setImpotsData);
        });
      }
    };

    if (loading) {
      return /*#__PURE__*/React.createElement("div", {
        style: {
          minHeight: "60vh",
          display: "flex",
          alignItems: "center",
          justifyContent: "center"
        }
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          color: C?.inkSoft || "#6B7278",
          fontFamily: "sans-serif",
          fontSize: 14
        }
      }, "Chargement des données fiscales…"));
    }

    if (error) {
      return /*#__PURE__*/React.createElement("div", {
        style: {
          minHeight: "60vh",
          display: "flex",
          alignItems: "center",
          justifyContent: "center"
        }
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          color: C?.brick || "#A8503C",
          fontFamily: "sans-serif"
        }
      }, "Erreur de chargement des données impôts."));
    }

    const data = impotsData;
    const allTax = (data?.taxYearly && data.taxYearly.length > 0) ? data.taxYearly : (data?.taxPreview || []);
    const currentYear = new Date().getFullYear();
    const currentTax = allTax.find(t => t.year === currentYear)
                    || allTax.find(t => t.year > currentYear)
                    || (allTax.length > 0 ? allTax[0] : null);
    const taxPreview = (data?.taxPreview && data.taxPreview.length > 0)
      ? data.taxPreview
      : allTax.filter(t => t.year >= currentYear).slice(0, 6);

    const inputStyle = {
      border: `1px solid ${C?.line || "#DED6C4"}`,
      borderRadius: 7,
      padding: "8px 10px",
      fontSize: 14,
      width: 140,
      background: C?.panel || "#FFFFFF",
      color: C?.ink || "#232A2E"
    };

    return /*#__PURE__*/React.createElement(React.Fragment, null,
      /*#__PURE__*/React.createElement("div", {
        style: {
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
          gap: 16,
          marginBottom: 24
        }
      },
        /*#__PURE__*/React.createElement(KPI, {
          label: "Taux de PAS estimé",
          value: currentTax ? (currentTax.ratePAS * 100).toFixed(1) + " %" : "0.0 %",
          accent: C?.pine || "#2F5D50",
          sub: currentTax ? `Année ${currentTax.year}` : "Prélèvement à la source"
        }),
        /*#__PURE__*/React.createElement(KPI, {
          label: "Impôt annuel net estimé",
          value: currentTax ? eur(currentTax.taxActual) : "0 €",
          accent: C?.brick || "#A8503C",
          sub: currentTax ? `~ ${eur(Math.round(currentTax.taxActual / 12))} / mois` : "Montant annuel"
        }),
        /*#__PURE__*/React.createElement(KPI, {
          label: "Parts fiscales",
          value: currentTax ? `${currentTax.parts} part${currentTax.parts > 1 ? "s" : ""}` : "2 parts",
          accent: C?.navy || "#28394A",
          sub: `${(data?.taxChildren || []).length} enfant(s) à charge`
        }),
        /*#__PURE__*/React.createElement(KPI, {
          label: "Revenu net imposable",
          value: currentTax ? eur(currentTax.taxableIncome) : "0 €",
          accent: C?.gold || "#93802E",
          sub: "Après abattement 10 %"
        })
      ),

      /*#__PURE__*/React.createElement(SectionCard, {
        title: "Foyer fiscal",
        subtitle: "Déclaration commune (mariés/pacsés) — 2 parts de base, puis 0,5 part pour chacun des deux premiers enfants à charge, 1 part à partir du 3ᵉ"
      },
        /*#__PURE__*/React.createElement("div", {
          style: {
            display: "flex",
            gap: 30,
            flexWrap: "wrap",
            marginBottom: 18
          }
        },
          /*#__PURE__*/React.createElement("div", null,
            /*#__PURE__*/React.createElement("div", {
              style: {
                fontSize: 12,
                color: C?.inkSoft || "#6B7278",
                marginBottom: 6
              }
            }, "Âge de sortie du foyer fiscal des enfants"),
            /*#__PURE__*/React.createElement("input", {
              type: "number",
              value: data?.settings?.childExitAge ?? 21,
              onChange: e => handleUpdate("childExitAge", parseInt(e.target.value, 10) || 21),
              style: inputStyle
            }),
            /*#__PURE__*/React.createElement("div", {
              style: {
                fontSize: 11.5,
                color: C?.inkSoft || "#6B7278",
                marginTop: 4
              }
            }, "21 ans par défaut — jusqu'à 25 ans si rattachement étudiant")
          ),
          /*#__PURE__*/React.createElement("div", null,
            /*#__PURE__*/React.createElement("div", {
              style: {
                fontSize: 12,
                color: C?.inkSoft || "#6B7278",
                marginBottom: 6
              }
            }, "Abattement forfaitaire (frais professionnels)"),
            /*#__PURE__*/React.createElement("input", {
              type: "number",
              step: "0.1",
              value: (Number(data?.settings?.taxAbattement || 0.1) * 100).toFixed(1),
              onChange: e => handleUpdate("taxAbattement", (parseFloat(e.target.value || 0) || 0) / 100),
              style: inputStyle
            }),
            /*#__PURE__*/React.createElement("div", {
              style: {
                fontSize: 11.5,
                color: C?.inkSoft || "#6B7278",
                marginTop: 4
              }
            }, "10 % par défaut, sans plafond modélisé ici")
          )
        ),
        /*#__PURE__*/React.createElement("div", {
          style: {
            fontSize: 12,
            color: C?.inkSoft || "#6B7278",
            marginBottom: 8,
            fontWeight: 600
          }
        }, "Enfants à charge (fiscalement)"),
        /*#__PURE__*/React.createElement(EditableTable, {
          columns: [{
            key: "birthYear",
            label: "Année de naissance",
            type: "number"
          }],
          rows: data?.taxChildren || [],
          onCell: handleSetCell("taxChildren"),
          onRemove: handleRemoveRow("taxChildren"),
          onAdd: () => handleAddRow("taxChildren", () => ({
            id: uid(),
            birthYear: new Date().getFullYear()
          }))
        })
      ),

      /*#__PURE__*/React.createElement(SectionCard, {
        title: "Barème progressif de l'impôt (par part)",
        subtitle: "Barème officiel français par défaut — tranches progressives calculées par part fiscale (quotient familial)",
        right: /*#__PURE__*/React.createElement("button", {
          type: "button",
          onClick: handleResetBrackets,
          style: {
            fontSize: 12,
            color: C?.pine || "#2F5D50",
            background: C?.pineSoft || "#E3ECE8",
            border: `1px solid ${C?.line || "#DED6C4"}`,
            borderRadius: 6,
            padding: "6px 12px",
            cursor: "pointer",
            fontWeight: 600
          }
        }, "↺ Rétablir barème officiel 2025/2026")
      },
        /*#__PURE__*/React.createElement(EditableTable, {
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
          onCell: handleSetCell("taxBrackets"),
          onRemove: handleRemoveRow("taxBrackets"),
          onAdd: () => handleAddRow("taxBrackets", () => ({
            id: uid(),
            upTo: "",
            rate: 0
          }))
        })
      ),

      taxPreview.length > 0 && LineChartJS && /*#__PURE__*/React.createElement(SectionCard, {
        title: "Évolution prévisionnelle de l'impôt",
        subtitle: "Projection de l'impôt annuel dû et du revenu net imposable sur les prochaines années"
      },
        /*#__PURE__*/React.createElement(LineChartJS, {
          data: taxPreview,
          xKey: "year",
          series: [
            {
              key: "taxActual",
              label: "Impôt annuel (€)",
              color: C?.brick || "#A8503C",
              width: 3
            },
            {
              key: "taxableIncome",
              label: "Revenu imposable (€)",
              color: C?.pine || "#2F5D50",
              width: 2
            }
          ],
          height: 240
        })
      ),

      /*#__PURE__*/React.createElement(SectionCard, {
        title: "Aperçu — parts, revenu imposable et taux de PAS prévisionnels",
        subtitle: "Synthèse annuelle du quotient familial, de l'impôt calculé et du prélèvement à la source estimé"
      },
        /*#__PURE__*/React.createElement("div", {
          style: {
            overflowX: "auto"
          }
        },
          /*#__PURE__*/React.createElement("table", {
            style: {
              width: "100%",
              borderCollapse: "collapse",
              fontSize: 13
            }
          },
            /*#__PURE__*/React.createElement("thead", null,
              /*#__PURE__*/React.createElement("tr", null,
                /*#__PURE__*/React.createElement("th", {
                  style: {
                    textAlign: "left",
                    fontSize: 11,
                    color: C?.inkSoft || "#6B7278",
                    padding: "0 6px 6px"
                  }
                }, "Année"),
                /*#__PURE__*/React.createElement("th", {
                  style: {
                    textAlign: "right",
                    fontSize: 11,
                    color: C?.inkSoft || "#6B7278",
                    padding: "0 6px 6px"
                  }
                }, "Parts"),
                /*#__PURE__*/React.createElement("th", {
                  style: {
                    textAlign: "right",
                    fontSize: 11,
                    color: C?.inkSoft || "#6B7278",
                    padding: "0 6px 6px"
                  }
                }, "Revenu imposable"),
                /*#__PURE__*/React.createElement("th", {
                  style: {
                    textAlign: "right",
                    fontSize: 11,
                    color: C?.inkSoft || "#6B7278",
                    padding: "0 6px 6px"
                  }
                }, "Impôt estimé / réel"),
                /*#__PURE__*/React.createElement("th", {
                  style: {
                    textAlign: "right",
                    fontSize: 11,
                    color: C?.inkSoft || "#6B7278",
                    padding: "0 6px 6px"
                  }
                }, "Mensualité PAS"),
                /*#__PURE__*/React.createElement("th", {
                  style: {
                    textAlign: "right",
                    fontSize: 11,
                    color: C?.inkSoft || "#6B7278",
                    padding: "0 6px 6px"
                  }
                }, "Taux PAS")
              )
            ),
            /*#__PURE__*/React.createElement("tbody", null,
              (taxPreview || []).map(row => /*#__PURE__*/React.createElement("tr", {
                key: row.year,
                style: {
                  borderTop: `1px solid ${C?.line || "#DED6C4"}`
                }
              },
                /*#__PURE__*/React.createElement("td", {
                  style: {
                    padding: "8px 6px",
                    fontWeight: 600
                  }
                }, row.year),
                /*#__PURE__*/React.createElement("td", {
                  style: {
                    textAlign: "right",
                    padding: "8px 6px",
                    fontFamily: "'IBM Plex Mono', monospace"
                  }
                }, row.parts),
                /*#__PURE__*/React.createElement("td", {
                  style: {
                    textAlign: "right",
                    padding: "8px 6px",
                    fontFamily: "'IBM Plex Mono', monospace"
                  }
                }, eur(row.taxableIncome)),
                /*#__PURE__*/React.createElement("td", {
                  style: {
                    textAlign: "right",
                    padding: "8px 6px",
                    fontFamily: "'IBM Plex Mono', monospace",
                    color: C?.brick || "#A8503C",
                    fontWeight: 600
                  }
                }, eur(row.taxActual)),
                /*#__PURE__*/React.createElement("td", {
                  style: {
                    textAlign: "right",
                    padding: "8px 6px",
                    fontFamily: "'IBM Plex Mono', monospace",
                    color: C?.inkSoft || "#6B7278"
                  }
                }, eur(Math.round(row.taxActual / 12))),
                /*#__PURE__*/React.createElement("td", {
                  style: {
                    textAlign: "right",
                    padding: "8px 6px",
                    fontFamily: "'IBM Plex Mono', monospace",
                    fontWeight: 600,
                    color: C?.pine || "#2F5D50"
                  }
                }, (row.ratePAS * 100).toFixed(1), " %")
              ))
            )
          )
        )
      ),

      /*#__PURE__*/React.createElement(SectionCard, {
        title: "Taux de prélèvement à la source — valeurs réelles",
        subtitle: "Renseignez ici le taux exact affiché sur votre fiche de paie (il change chaque septembre) : il remplace le taux prévisionnel pour cette année précise"
      },
        /*#__PURE__*/React.createElement(EditableTable, {
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
          onCell: handleSetCell("taxRateOverrides"),
          onRemove: handleRemoveRow("taxRateOverrides"),
          onAdd: () => handleAddRow("taxRateOverrides", () => ({
            id: uid(),
            year: new Date().getFullYear(),
            rate: 0.1,
            notes: ""
          }))
        })
      ),

      /*#__PURE__*/React.createElement(SectionCard, {
        title: "Impôt réel constaté (avis d'imposition)",
        subtitle: "Dès réception de votre avis d'imposition définitif, ajoutez le montant réellement dû pour cette année-là — cela affine automatiquement la régularisation de l'année suivante"
      },
        /*#__PURE__*/React.createElement(EditableTable, {
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
          onCell: handleSetCell("taxActualOverrides"),
          onRemove: handleRemoveRow("taxActualOverrides"),
          onAdd: () => handleAddRow("taxActualOverrides", () => ({
            id: uid(),
            year: new Date().getFullYear(),
            amount: 0,
            notes: ""
          }))
        })
      )
    );
  }

  exports.ImpotsView = ImpotsView;
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);
